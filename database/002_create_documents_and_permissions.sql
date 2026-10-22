USE trustvault;

CREATE TABLE documents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes INT NOT NULL,
    file_data MEDIUMBLOB NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    CONSTRAINT fk_documents_owner
        FOREIGN KEY (owner_id) REFERENCES app_users(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_documents_name
        CHECK (CHAR_LENGTH(TRIM(name)) > 0),

    CONSTRAINT chk_documents_category
        CHECK (CHAR_LENGTH(TRIM(category)) > 0),

    CONSTRAINT chk_documents_size
        CHECK (size_bytes BETWEEN 1 AND 5242880),

    CONSTRAINT chk_documents_data_length
        CHECK (OCTET_LENGTH(file_data) = size_bytes),

    INDEX idx_documents_owner_created (owner_id, created_at),
    INDEX idx_documents_category_created (category, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


CREATE TABLE document_permissions (
    document_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    permission VARCHAR(4) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    granted_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (document_id, user_id),

    CONSTRAINT fk_permissions_document
        FOREIGN KEY (document_id) REFERENCES documents(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_permissions_user
        FOREIGN KEY (user_id) REFERENCES app_users(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_permissions_granter
        FOREIGN KEY (granted_by) REFERENCES app_users(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_permissions_value
        CHECK (permission IN ('READ', 'EDIT')),

    INDEX idx_permissions_user_document (user_id, document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;