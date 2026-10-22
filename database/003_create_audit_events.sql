USE trustvault;

CREATE TABLE audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_id BIGINT NULL,
    actor_email VARCHAR(254) NULL,
    document_id BIGINT NULL,
    event_type VARCHAR(32)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    outcome VARCHAR(7)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    details JSON NULL,
    created_at TIMESTAMP(6)
        NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    CONSTRAINT chk_audit_events_outcome
        CHECK (outcome IN ('SUCCESS', 'DENIED', 'FAILURE')),

    INDEX idx_audit_events_created (created_at),
    INDEX idx_audit_events_document_created
        (document_id, created_at),
    INDEX idx_audit_events_actor_created
        (actor_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;