package com.trustvault;

import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import com.trustvault.AuditService.EventType;

@Service
public class AuditQueryService {
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final TransactionTemplate transactions;

    public record Event(long id, String actorEmail, Long documentId,
            String eventType, String outcome, String details, String createdAt) {}

    public AuditQueryService(JdbcTemplate jdbc, AuditService audit,
            PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.audit = audit;
        transactions = new TransactionTemplate(manager);
    }

    public List<Event> list(String email, Long before) {
        try {
            return Objects.requireNonNull(transactions.execute(status -> {
                // Session authorities can be stale: check the database again.
                List<Long> admins = jdbc.query("""
                        SELECT id FROM app_users
                        WHERE email = ? AND role = 'ADMIN' AND enabled = 1 FOR SHARE
                        """, (rs, row) -> rs.getLong("id"), email);
                if (admins.isEmpty()) throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Administrator access required.");
                if (before != null && before <= 0) throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid audit cursor.");
                List<Event> events = jdbc.query("""
                        SELECT id, actor_email, document_id, event_type,
                               outcome, details, created_at
                        FROM audit_events WHERE id < ? ORDER BY id DESC LIMIT 100
                        """, (rs, row) -> new Event(rs.getLong("id"),
                        rs.getString("actor_email"), rs.getObject("document_id", Long.class),
                        rs.getString("event_type"), rs.getString("outcome"),
                        rs.getString("details"), rs.getTimestamp("created_at").toInstant().toString()),
                        before == null ? Long.MAX_VALUE : before);
                audit.recordSuccess(admins.get(0), email, null,
                        EventType.VIEW_AUDIT, "AUDIT_PAGE_VIEWED");
                return events;
            }));
        } catch (ResponseStatusException exception) {
            audit.recordDenied(null, email, null, EventType.VIEW_AUDIT, "AUDIT_ACCESS_REJECTED");
            throw exception;
        }
    }
}
