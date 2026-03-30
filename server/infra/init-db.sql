-- ============================================================
-- Collaborative Document Editor — Database Initialization
-- Applied automatically on first run by PostgreSQL entrypoint.
-- JPA ddl-auto=update handles schema evolution after that.
-- ============================================================

-- Performance extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Documents
CREATE TABLE IF NOT EXISTS documents (
    document_id   VARCHAR(50)  PRIMARY KEY,
    title         VARCHAR(255) NOT NULL,
    owner_id      VARCHAR(50)  NOT NULL,
    home_region   VARCHAR(50),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    version_vector JSONB       NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX IF NOT EXISTS idx_documents_owner    ON documents(owner_id);
CREATE INDEX IF NOT EXISTS idx_documents_updated  ON documents(updated_at);

-- CRDT Operations (source of truth)
CREATE TABLE IF NOT EXISTS document_operations (
    operation_id     BIGSERIAL    PRIMARY KEY,
    document_id      VARCHAR(50)  NOT NULL REFERENCES documents(document_id) ON DELETE CASCADE,
    site_id          VARCHAR(50)  NOT NULL,
    logical_timestamp BIGINT      NOT NULL,
    operation_type   VARCHAR(20)  NOT NULL,
    character_id     VARCHAR(100) NOT NULL,
    character_value  VARCHAR(1),
    previous_id      VARCHAR(100),
    deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    format_data      JSONB,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (document_id, character_id)
);
CREATE INDEX IF NOT EXISTS idx_ops_doc_site ON document_operations(document_id, site_id, logical_timestamp);
CREATE INDEX IF NOT EXISTS idx_ops_doc_created ON document_operations(document_id, created_at);

-- Snapshots (periodic checkpoints for fast recovery)
CREATE TABLE IF NOT EXISTS document_snapshots (
    snapshot_id     VARCHAR(50)  PRIMARY KEY,
    document_id     VARCHAR(50)  NOT NULL REFERENCES documents(document_id) ON DELETE CASCADE,
    content         TEXT         NOT NULL,
    version_vector  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    operation_count INTEGER      NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_snapshots_doc ON document_snapshots(document_id, created_at DESC);

-- Collaborators & Permissions
CREATE TABLE IF NOT EXISTS document_collaborators (
    document_id VARCHAR(50)  NOT NULL REFERENCES documents(document_id) ON DELETE CASCADE,
    user_id     VARCHAR(50)  NOT NULL,
    permission  VARCHAR(20)  NOT NULL,
    joined_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (document_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_collab_user ON document_collaborators(user_id);

-- Grant permissions
GRANT ALL PRIVILEGES ON ALL TABLES    IN SCHEMA public TO collab;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO collab;
