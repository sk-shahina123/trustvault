package com.trustvault;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    public enum EventType {
        UPLOAD, LIST, READ, DOWNLOAD, EDIT, DELETE,
        GRANT, REVOKE, VIEW_AUDIT, ACCESS, REGISTER
    }

    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRequest(
            String actorEmail, Long documentId, EventType type,
            String outcome, String reason, String method, String path) {
        jdbcTemplate.update("""
                INSERT INTO audit_events
                    (actor_email, document_id, event_type, outcome, details)
                VALUES (?, ?, ?, ?, JSON_OBJECT(
                    'reason', ?, 'method', ?, 'path', ?))
                """, actorEmail, documentId, type.name(), outcome,
                reason, method, path);
    }

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
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPermissionSuccess(
            Long actorId, String actorEmail, Long documentId,
            EventType eventType, String reason,
            Long recipientId, String recipientEmail,
            String previousPermission, String permission) {

        jdbcTemplate.update(
                """
                INSERT INTO audit_events
                    (actor_id, actor_email, document_id,
                     event_type, outcome, details)
                VALUES (?, ?, ?, ?, 'SUCCESS', JSON_OBJECT(
                    'reason', ?,
                    'recipientId', ?,
                    'recipientEmail', ?,
                    'previousPermission', ?,
                    'permission', ?))
                """,
                actorId, actorEmail, documentId, eventType.name(), reason,
                recipientId, recipientEmail, previousPermission, permission);
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
