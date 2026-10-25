package com.trustvault;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.trustvault.AuditService.EventType;

@Service
public class PermissionService {

    private static final Logger logger =
            LoggerFactory.getLogger(PermissionService.class);

    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;
    private final TransactionTemplate transactions;

    private record Account(
            long id, String email, String role, boolean enabled) {}

    private static final RowMapper<Account> ACCOUNT_ROW = (rs, row) ->
            new Account(
                    rs.getLong("id"),
                    rs.getString("email"),
                    rs.getString("role"),
                    rs.getBoolean("enabled"));

    public record PermissionEntry(
            long userId, String email, String permission, boolean enabled) {}

    public PermissionService(
            JdbcTemplate jdbcTemplate,
            AuditService auditService,
            PlatformTransactionManager transactionManager) {

        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public List<PermissionEntry> list(
            String authenticatedEmail, long documentId) {

        return asOwner(
                authenticatedEmail, documentId, EventType.LIST, actor -> {

            List<PermissionEntry> entries = jdbcTemplate.query(
                    """
                    SELECT p.user_id, u.email, p.permission, u.enabled
                    FROM document_permissions p
                    JOIN app_users u ON u.id = p.user_id
                    WHERE p.document_id = ?
                    ORDER BY u.email, u.id
                    """,
                    (rs, row) -> new PermissionEntry(
                            rs.getLong("user_id"),
                            rs.getString("email"),
                            rs.getString("permission"),
                            rs.getBoolean("enabled")),
                    documentId);

            auditService.recordSuccess(
                    actor.id(), actor.email(), documentId,
                    EventType.LIST, "PERMISSIONS_LISTED");

            return entries;
        });
    }

    public void grant(
            String authenticatedEmail, long documentId,
            String recipientEmail, String permission) {

        asOwner(authenticatedEmail, documentId, EventType.GRANT, actor -> {

            if (!"READ".equals(permission) && !"EDIT".equals(permission)) {
                throw problem(400, "Permission must be READ or EDIT.");
            }

            String email =
                    recipientEmail == null ? "" : recipientEmail.strip();

            if (email.isEmpty() || email.length() > 254) {
                throw problem(
                        400, "Enter an existing account's email address.");
            }

            Account recipient = findByEmail(email);

            if (recipient == null || !recipient.enabled()) {
                throw problem(400, "No enabled account matches that email.");
            }

            if (recipient.id() == actor.id()) {
                throw problem(
                        400, "The owner already has access to this document.");
            }

            if ("EDIT".equals(permission)
                    && !"OWNER".equals(recipient.role())) {
                throw problem(
                        403, "EDIT can be granted only to an OWNER account.");
            }

            String previous = lockPermission(documentId, recipient.id());

            if (previous == null) {
                jdbcTemplate.update(
                        """
                        INSERT INTO document_permissions
                            (document_id, user_id, permission, granted_by)
                        VALUES (?, ?, ?, ?)
                        """,
                        documentId, recipient.id(), permission, actor.id());

            } else if (!previous.equals(permission)) {
                jdbcTemplate.update(
                        """
                        UPDATE document_permissions
                        SET permission = ?, granted_by = ?
                        WHERE document_id = ? AND user_id = ?
                        """,
                        permission, actor.id(), documentId, recipient.id());
            }

            auditService.recordPermissionSuccess(
                    actor.id(), actor.email(), documentId,
                    EventType.GRANT,
                    permission.equals(previous)
                            ? "PERMISSION_UNCHANGED"
                            : "PERMISSION_GRANTED",
                    recipient.id(), recipient.email(),
                    previous, permission);

            return true;
        });
    }

    public void revoke(
            String authenticatedEmail, long documentId, long recipientId) {

        asOwner(authenticatedEmail, documentId, EventType.REVOKE, actor -> {

            List<Account> recipients = jdbcTemplate.query(
                    """
                    SELECT id, email, role, enabled
                    FROM app_users WHERE id = ? FOR SHARE
                    """,
                    ACCOUNT_ROW, recipientId);

            if (recipients.isEmpty()) {
                throw problem(400, "That recipient account does not exist.");
            }

            Account recipient = recipients.get(0);

            if (recipient.id() == actor.id()) {
                throw problem(400, "The owner's access cannot be revoked.");
            }

            // Disabled recipients must still be removable.
            String previous = lockPermission(documentId, recipientId);

            if (previous != null) {
                jdbcTemplate.update(
                        """
                        DELETE FROM document_permissions
                        WHERE document_id = ? AND user_id = ?
                        """,
                        documentId, recipientId);
            }

            auditService.recordPermissionSuccess(
                    actor.id(), actor.email(), documentId,
                    EventType.REVOKE,
                    previous == null
                            ? "PERMISSION_ALREADY_ABSENT"
                            : "PERMISSION_REVOKED",
                    recipient.id(), recipient.email(),
                    previous, null);

            return true;
        });
    }

    private <T> T asOwner(
            String authenticatedEmail, long documentId,
            EventType eventType, Function<Account, T> action) {

        try {
            return Objects.requireNonNull(transactions.execute(status -> {

                // Read and lock the current account state.
                Account actor = findByEmail(authenticatedEmail);

                if (actor == null || !actor.enabled()) {
                    throw problem(
                            403, "This account cannot manage permissions.");
                }

                if (!"OWNER".equals(actor.role())) {
                    throw problem(
                            403, "Only document owners can manage permissions.");
                }

                // Having the OWNER role is insufficient:
                // this account must own this particular document.
                List<Long> ownedDocuments = jdbcTemplate.query(
                        """
                        SELECT id FROM documents
                        WHERE id = ? AND owner_id = ? FOR SHARE
                        """,
                        (rs, row) -> rs.getLong("id"),
                        documentId, actor.id());

                if (ownedDocuments.isEmpty()) {
                    throw problem(
                            404, "Document not found or access unavailable.");
                }

                return action.apply(actor);
            }));

        } catch (ResponseStatusException exception) {

            // The operation has rolled back before this audit is written.
            String reason = exception.getReason();

            if (exception.getStatusCode().value() == 400) {
                auditService.recordFailure(
                        null, authenticatedEmail, documentId,
                        eventType, reason);
            } else {
                auditService.recordDenied(
                        null, authenticatedEmail, documentId,
                        eventType, reason);
            }

            throw exception;

        } catch (DataAccessException | TransactionException exception) {

            logger.error("Permission operation failed", exception);

            try {
                auditService.recordFailure(
                        null, authenticatedEmail, documentId,
                        eventType, "PERMISSION_OPERATION_FAILED");

            } catch (RuntimeException auditFailure) {
                logger.error(
                        "Could not record the permission failure",
                        auditFailure);
            }

            throw problem(
                    503,
                    "Permissions could not be updated or loaded. Try again.");
        }
    }

    private Account findByEmail(String email) {

        List<Account> accounts = jdbcTemplate.query(
                """
                SELECT id, email, role, enabled
                FROM app_users WHERE email = ? FOR SHARE
                """,
                ACCOUNT_ROW, email);

        return accounts.isEmpty() ? null : accounts.get(0);
    }

    private String lockPermission(long documentId, long userId) {

        List<String> permissions = jdbcTemplate.query(
                """
                SELECT permission FROM document_permissions
                WHERE document_id = ? AND user_id = ? FOR UPDATE
                """,
                (rs, row) -> rs.getString("permission"),
                documentId, userId);

        return permissions.isEmpty() ? null : permissions.get(0);
    }

    private static ResponseStatusException problem(
            int status, String message) {

        return new ResponseStatusException(
                HttpStatus.valueOf(status), message);
    }
}
