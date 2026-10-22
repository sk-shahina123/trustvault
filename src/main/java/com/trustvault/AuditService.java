package com.trustvault;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    public enum EventType {
        UPLOAD, LIST, READ, DOWNLOAD, EDIT, DELETE,
        GRANT, REVOKE, VIEW_AUDIT
    }

    private final JdbcTemplate jdbcTemplate;

    public AuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSuccess(
            Long actorId, String actorEmail, Long documentId,
            EventType eventType, String reason) {

        insertEvent(
                actorId, actorEmail, documentId,
                eventType, "SUCCESS", reason
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDenied(
            Long actorId, String actorEmail, Long documentId,
            EventType eventType, String reason) {

        insertEvent(
                actorId, actorEmail, documentId,
                eventType, "DENIED", reason
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            Long actorId, String actorEmail, Long documentId,
            EventType eventType, String reason) {

        insertEvent(
                actorId, actorEmail, documentId,
                eventType, "FAILURE", reason
        );
    }

    private void insertEvent(
            Long actorId, String actorEmail, Long documentId,
            EventType eventType, String outcome, String reason) {

        jdbcTemplate.update(
                """
                INSERT INTO audit_events
                    (actor_id, actor_email, document_id,
                     event_type, outcome, details)
                VALUES (?, ?, ?, ?, ?, JSON_OBJECT('reason', ?))
                """,
                actorId,
                actorEmail,
                documentId,
                eventType.name(),
                outcome,
                reason
        );
    }
}
