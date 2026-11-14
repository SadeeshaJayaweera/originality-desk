-- Originality Desk PostgreSQL Initial Schema Migration (V1)
CREATE TABLE workspaces (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
    id VARCHAR(64) PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL REFERENCES workspaces(id),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    role VARCHAR(32) NOT NULL CHECK(role IN ('INSTITUTION_ADMIN','REVIEWER','MEMBER')),
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(workspace_id, id)
);

CREATE TABLE sessions (
    token_hash VARCHAR(64) PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    expires_at BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    FOREIGN KEY(workspace_id, user_id) REFERENCES users(workspace_id, id) ON DELETE CASCADE
);
CREATE INDEX sessions_expiry ON sessions(expires_at);

CREATE TABLE documents (
    id VARCHAR(64) PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL REFERENCES workspaces(id),
    title VARCHAR(512) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    kind VARCHAR(32) NOT NULL CHECK(kind IN ('REFERENCE','SUBMISSION')),
    status VARCHAR(32) NOT NULL CHECK(status IN ('EXTRACTING','READY','FAILED')),
    error TEXT,
    word_count INTEGER NOT NULL DEFAULT 0,
    page_count INTEGER NOT NULL DEFAULT 0,
    bytes BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    storage_name VARCHAR(255) NOT NULL UNIQUE,
    sha256 VARCHAR(64) NOT NULL,
    text TEXT,
    UNIQUE(workspace_id, id),
    UNIQUE(workspace_id, kind, sha256)
);
CREATE INDEX documents_workspace ON documents(workspace_id, created_at);

CREATE TABLE analyses (
    id VARCHAR(64) PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    document_id VARCHAR(64) NOT NULL,
    title VARCHAR(512) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK(status IN ('QUEUED','RUNNING','COMPLETED','FAILED','CANCELLED')),
    profile VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    error TEXT,
    source_count INTEGER NOT NULL,
    result_json TEXT,
    document_text TEXT,
    idempotency_key VARCHAR(128),
    request_hash VARCHAR(64) NOT NULL,
    FOREIGN KEY(workspace_id, document_id) REFERENCES documents(workspace_id, id) ON DELETE CASCADE,
    UNIQUE(workspace_id, id),
    UNIQUE(workspace_id, idempotency_key)
);
CREATE INDEX analyses_workspace ON analyses(workspace_id, created_at);

CREATE TABLE analysis_sources (
    workspace_id VARCHAR(64) NOT NULL,
    analysis_id VARCHAR(64) NOT NULL,
    document_id VARCHAR(64) NOT NULL,
    PRIMARY KEY(workspace_id, analysis_id, document_id),
    FOREIGN KEY(workspace_id, analysis_id) REFERENCES analyses(workspace_id, id) ON DELETE CASCADE,
    FOREIGN KEY(workspace_id, document_id) REFERENCES documents(workspace_id, id) ON DELETE CASCADE
);
CREATE INDEX analysis_source_document ON analysis_sources(workspace_id, document_id);

CREATE TABLE jobs (
    id VARCHAR(64) PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL REFERENCES workspaces(id),
    kind VARCHAR(32) NOT NULL CHECK(kind IN ('EXTRACT','ANALYZE')),
    resource_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK(status IN ('QUEUED','RUNNING','COMPLETED','FAILED','CANCELLED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    error TEXT,
    UNIQUE(workspace_id, kind, resource_id)
);
CREATE INDEX jobs_pending ON jobs(status, available_at);

CREATE TABLE reviews (
    id VARCHAR(64) PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    analysis_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    match_index INTEGER NOT NULL,
    decision VARCHAR(32) NOT NULL,
    note TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(workspace_id, analysis_id) REFERENCES analyses(workspace_id, id) ON DELETE CASCADE,
    FOREIGN KEY(workspace_id, user_id) REFERENCES users(workspace_id, id)
);
CREATE INDEX reviews_analysis ON reviews(workspace_id, analysis_id, created_at);

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL REFERENCES workspaces(id),
    user_id VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    resource_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX audit_workspace ON audit_logs(workspace_id, id);

CREATE TABLE external_references (
    workspace_id VARCHAR(64) NOT NULL REFERENCES workspaces(id),
    document_id VARCHAR(64) NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    provider_id VARCHAR(64) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    doi VARCHAR(255),
    title TEXT NOT NULL,
    authors_json TEXT,
    publication_year INTEGER,
    venue_or_publisher TEXT,
    landing_page_url TEXT,
    pdf_url TEXT,
    license VARCHAR(128),
    is_open_access INTEGER NOT NULL DEFAULT 0,
    citation_count INTEGER NOT NULL DEFAULT 0,
    citation_apa TEXT,
    citation_ieee TEXT,
    citation_mla TEXT,
    citation_bibtex TEXT,
    resolver_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (workspace_id, document_id)
);
CREATE INDEX ext_ref_doi ON external_references(workspace_id, doi);
