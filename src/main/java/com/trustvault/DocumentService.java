package com.trustvault;

import java.io.IOException;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.trustvault.AuditService.EventType;
import com.trustvault.UploadValidator.ValidatedUpload;
import com.trustvault.UploadValidator.ValidationException;

@Service
public class DocumentService {

    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;
    private final TransactionTemplate transactions;

    public DocumentService(
            JdbcTemplate jdbcTemplate,
            AuditService auditService,
            PlatformTransactionManager transactionManager) {

        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    private record Account(
            long id, String email, String role, boolean enabled) {
    }

    public record DocumentSummary(
            long id,
            String name,
            String ownerEmail,
            String category,
            String createdAt,
            String accessLevel) {
    }

    public long upload(
            String authenticatedEmail,
            String name,
            String category,
            MultipartFile file) {

        Account actor = currentAccount(
                authenticatedEmail, EventType.UPLOAD);

        if (!"OWNER".equals(actor.role())) {
            auditService.recordDenied(
                    actor.id(),
                    actor.email(),
                    null,
                    EventType.UPLOAD,
                    "OWNER_ROLE_REQUIRED");

            throw problem(
                    403, "Only an OWNER account can upload documents.");
        }

        ValidatedUpload upload;

        try {
            if (file == null || file.isEmpty()) {
                throw new ValidationException(
                        400, "Choose a non-empty file.");
            }

            if (file.getSize() > UploadValidator.MAX_BYTES) {
                throw new ValidationException(
                        413, "Maximum file size is 5 MiB.");
            }

            byte[] data;

            try (InputStream input = file.getInputStream()) {
                data = input.readNBytes(UploadValidator.MAX_BYTES + 1);
            }

            upload = UploadValidator.validate(
                    name,
                    category,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    data);

        } catch (ValidationException exception) {
            auditService.recordFailure(
                    actor.id(),
                    actor.email(),
                    null,
                    EventType.UPLOAD,
                    exception.getMessage());

            throw problem(
                    exception.status(), exception.getMessage());

        } catch (IOException exception) {
            auditService.recordFailure(
                    actor.id(),
                    actor.email(),
                    null,
                    EventType.UPLOAD,
                    "UPLOAD_READ_FAILED");

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "The uploaded file could not be read.",
                    exception);
        }

        // Save the document and its success audit entry together.
        // If either database write fails, the transaction rolls back.
        Long documentId = transactions.execute(status -> {
            KeyHolder keys = new GeneratedKeyHolder();

            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        """
                        INSERT INTO documents
                            (owner_id, name, original_filename, category,
                             content_type, size_bytes, file_data)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                        Statement.RETURN_GENERATED_KEYS);

                statement.setLong(1, actor.id());
                statement.setString(2, upload.name());
                statement.setString(3, upload.filename());
                statement.setString(4, upload.category());
                statement.setString(5, upload.contentType());
                statement.setInt(6, upload.data().length);
                statement.setBytes(7, upload.data());

                return statement;
            }, keys);

            Number generatedId = Objects.requireNonNull(
                    keys.getKey(),
                    "Database did not return a document ID");

            long id = generatedId.longValue();

            auditService.recordSuccess(
                    actor.id(),
                    actor.email(),
                    id,
                    EventType.UPLOAD,
                    "DOCUMENT_CREATED");

            return id;
        });

        return Objects.requireNonNull(documentId);
    }

    public List<DocumentSummary> list(String authenticatedEmail) {
        return Objects.requireNonNull(transactions.execute(status -> {
            Account actor = currentAccount(authenticatedEmail, EventType.LIST);
            // Return metadata only for owned or explicitly shared documents.
            // Being ADMIN does not automatically grant document access.
            List<DocumentSummary> documents = jdbcTemplate.query(
                    """
                    SELECT d.id, d.name, owner.email AS owner_email,
                           d.category, d.created_at,
                           CASE
                               WHEN d.owner_id = ? THEN 'OWNER'
                               WHEN p.permission = 'EDIT' AND ? = 'OWNER'
                                   THEN 'EDIT'
                               ELSE 'READ'
                           END AS access_level
                    FROM documents d
                    JOIN app_users owner ON owner.id = d.owner_id
                    LEFT JOIN document_permissions p
                        ON p.document_id = d.id AND p.user_id = ?
                    WHERE d.owner_id = ? OR p.permission IN ('READ', 'EDIT')
                    ORDER BY d.created_at DESC, d.id DESC
                    LIMIT 100
                    """,
                    (rs, rowNumber) -> new DocumentSummary(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getString("owner_email"),
                            rs.getString("category"),
                            rs.getTimestamp("created_at")
                                    .toInstant().toString(),
                            rs.getString("access_level")),
                    actor.id(),
                    actor.role(),
                    actor.id(),
                    actor.id());

            auditService.recordSuccess(
                    actor.id(),
                    actor.email(),
                    null,
                    EventType.LIST,
                    "RETURNED_" + documents.size() + "_DOCUMENTS");

            return documents;
        }));
    }
public record DocumentDownload(
        String filename, String contentType, byte[] data) {
}

public DocumentDownload download(
        String authenticatedEmail, long documentId) {

    Account actor = currentAccount(
            authenticatedEmail, EventType.DOWNLOAD);

    DocumentDownload result = transactions.execute(status -> {
        // Fetch bytes only from a document this account may access.
        List<DocumentDownload> matches = jdbcTemplate.query(
                """
                SELECT d.original_filename, d.content_type, d.file_data
                FROM documents d
                LEFT JOIN document_permissions p
                    ON p.document_id = d.id AND p.user_id = ?
                WHERE d.id = ?
                  AND (
                      d.owner_id = ?
                      OR p.permission IN ('READ', 'EDIT')
                  )
                """,
                (rs, rowNumber) -> new DocumentDownload(
                        rs.getString("original_filename"),
                        rs.getString("content_type"),
                        rs.getBytes("file_data")),
                actor.id(), documentId, actor.id());

        if (matches.isEmpty()) {
            return null;
        }

        // The audit must commit before bytes leave this service.
        auditService.recordSuccess(
                actor.id(), actor.email(), documentId,
                EventType.DOWNLOAD, "DOWNLOAD_AUTHORIZED");

        return matches.get(0);
    });

    if (result == null) {
        // Record the denial after the read transaction has finished.
        auditService.recordDenied(
                actor.id(), actor.email(), documentId,
                EventType.DOWNLOAD, "NOT_FOUND_OR_NO_ACCESS");

        // Same response for a missing document and missing permission.
        throw problem(404, "Document not found or access unavailable.");
    }

    return result;
}
    private Account currentAccount(String email, EventType eventType) {
        // Check the current account state in MySQL.
        List<Account> accounts = jdbcTemplate.query(
                """
                SELECT id, email, role, enabled
                FROM app_users
                WHERE email = ?
                """,
                (rs, rowNumber) -> new Account(
                        rs.getLong("id"),
                        rs.getString("email"),
                        rs.getString("role"),
                        rs.getBoolean("enabled")),
                email);

        if (accounts.isEmpty()) {
            auditService.recordDenied(
                    null,
                    email,
                    null,
                    eventType,
                    "ACCOUNT_NOT_FOUND");

            throw problem(
                    403, "This account cannot access documents.");
        }

        Account account = accounts.get(0);

        if (!account.enabled()) {
            auditService.recordDenied(
                    account.id(),
                    account.email(),
                    null,
                    eventType,
                    "ACCOUNT_DISABLED");

            throw problem(
                    403, "This account cannot access documents.");
        }

        return account;
    }

    public void edit(String email, long documentId, String name,
            String category, MultipartFile replacement) {
        mutate(email, documentId, EventType.EDIT, actor -> {
            String cleanName = UploadValidator.requiredText(name, "Document name", 255);
            String cleanCategory = UploadValidator.requiredText(category, "Category", 100);
            boolean replace = replacement != null && replacement.getOriginalFilename() != null
                    && !replacement.getOriginalFilename().isBlank();
            if (replace) {
                try (InputStream input = replacement.getInputStream()) {
                    ValidatedUpload upload = UploadValidator.validate(cleanName, cleanCategory,
                            replacement.getOriginalFilename(), replacement.getContentType(),
                            input.readNBytes(UploadValidator.MAX_BYTES + 1));
                    jdbcTemplate.update("""
                            UPDATE documents SET name = ?, category = ?, original_filename = ?,
                                content_type = ?, size_bytes = ?, file_data = ? WHERE id = ?
                            """, upload.name(), upload.category(), upload.filename(),
                            upload.contentType(), upload.data().length, upload.data(), documentId);
                } catch (IOException exception) {
                    throw problem(400, "The replacement file could not be read.");
                }
            } else {
                jdbcTemplate.update("UPDATE documents SET name = ?, category = ? WHERE id = ?",
                        cleanName, cleanCategory, documentId);
            }
            auditService.recordSuccess(actor.id(), actor.email(), documentId, EventType.EDIT,
                    replace ? "DOCUMENT_AND_FILE_UPDATED" : "DOCUMENT_METADATA_UPDATED");
            return true;
        });
    }

    public void delete(String email, long documentId) {
        mutate(email, documentId, EventType.DELETE, actor -> {
            // Permissions are removed by the FK cascade; audit rows deliberately have no FK.
            jdbcTemplate.update("DELETE FROM documents WHERE id = ?", documentId);
            auditService.recordSuccess(actor.id(), actor.email(), documentId,
                    EventType.DELETE, "DOCUMENT_DELETED");
            return true;
        });
    }

    private void mutate(String email, long documentId, EventType type,
            Function<Account, Boolean> action) {
        try {
            transactions.execute(status -> {
                List<Account> accounts = jdbcTemplate.query("""
                        SELECT id, email, role, enabled FROM app_users WHERE email = ? FOR SHARE
                        """, (rs, row) -> new Account(rs.getLong("id"), rs.getString("email"),
                        rs.getString("role"), rs.getBoolean("enabled")), email);
                if (accounts.isEmpty() || !accounts.get(0).enabled()
                        || !"OWNER".equals(accounts.get(0).role())) {
                    throw problem(403, "An enabled OWNER account is required.");
                }
                Account actor = accounts.get(0);
                // Shares/revocations also lock this row, so their commits cannot race the edit.
                List<Long> owners = jdbcTemplate.query(
                        "SELECT owner_id FROM documents WHERE id = ? FOR UPDATE",
                        (rs, row) -> rs.getLong("owner_id"), documentId);
                boolean allowed = !owners.isEmpty() && owners.get(0) == actor.id();
                if (!allowed && !owners.isEmpty() && type == EventType.EDIT) {
                    allowed = !jdbcTemplate.query("""
                            SELECT permission FROM document_permissions
                            WHERE document_id = ? AND user_id = ? AND permission = 'EDIT' FOR SHARE
                            """, (rs, row) -> rs.getString(1), documentId, actor.id()).isEmpty();
                }
                if (!allowed) throw problem(404, "Document not found or access unavailable.");
                return action.apply(actor);
            });
        } catch (ValidationException exception) {
            auditService.recordFailure(null, email, documentId, type, "INVALID_DOCUMENT_INPUT");
            throw problem(exception.status(), exception.getMessage());
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 400) {
                auditService.recordFailure(null, email, documentId, type, "INVALID_DOCUMENT_INPUT");
            } else {
                auditService.recordDenied(null, email, documentId, type, "NOT_FOUND_OR_NO_ACCESS");
            }
            throw exception;
        }
    }

    private static ResponseStatusException problem(
            int status, String message) {

        return new ResponseStatusException(
                HttpStatus.valueOf(status), message);
    }
}
