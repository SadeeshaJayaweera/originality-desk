-- Originality Desk SQLite Initial Schema Migration (V1)
CREATE TABLE workspaces (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE users (
    id TEXT PRIMARY KEY,
    workspace_id TEXT NOT NULL REFERENCES workspaces(id),
    name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    role TEXT NOT NULL CHECK(role IN ('INSTITUTION_ADMIN','REVIEWER','MEMBER')),
    password_hash TEXT NOT NULL,
    created_at TEXT NOT NULL,
    UNIQUE(workspace_id, id)
);

CREATE TABLE sessions (
    token_hash TEXT PRIMARY KEY,
    workspace_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    expires_at INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY(workspace_id, user_id) REFERENCES users(workspace_id, id) ON DELETE CASCADE
);
CREATE INDEX sessions_expiry ON sessions(expires_at);

CREATE TABLE documents (
    id TEXT PRIMARY KEY,
    workspace_id TEXT NOT NULL REFERENCES workspaces(id),
    title TEXT NOT NULL,
    filename TEXT NOT NULL,
    kind TEXT NOT NULL CHECK(kind IN ('REFERENCE','SUBMISSION')),
    status TEXT NOT NULL CHECK(status IN ('EXTRACTING','READY','FAILED')),
    error TEXT,
    word_count INTEGER NOT NULL DEFAULT 0,
    page_count INTEGER NOT NULL DEFAULT 0,
    bytes INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    storage_name TEXT NOT NULL UNIQUE,
    sha256 TEXT NOT NULL,
    text TEXT,
    UNIQUE(workspace_id, id),
    UNIQUE(workspace_id, kind, sha256)
);
CREATE INDEX documents_workspace ON documents(workspace_id, created_at);

CREATE TABLE analyses (
    id TEXT PRIMARY KEY,
    workspace_id TEXT NOT NULL,
    document_id TEXT NOT NULL,
    title TEXT NOT NULL,
    status TEXT NOT NULL CHECK(status IN ('QUEUED','RUNNING','COMPLETED','FAILED','CANCELLED')),
    profile TEXT NOT NULL,
    created_at TEXT NOT NULL,
    completed_at TEXT,
    error TEXT,
    source_count INTEGER NOT NULL,
    result_json TEXT,
    document_text TEXT,
    idempotency_key TEXT,
    request_hash TEXT NOT NULL,
    FOREIGN KEY(workspace_id, document_id) REFERENCES documents(workspace_id, id) ON DELETE CASCADE,
    UNIQUE(workspace_id, id),
    UNIQUE(workspace_id, idempotency_key)
);
CREATE INDEX analyses_workspace ON analyses(workspace_id, created_at);

CREATE TABLE analysis_sources (
    workspace_id TEXT NOT NULL,
    analysis_id TEXT NOT NULL,
    document_id TEXT NOT NULL,
    PRIMARY KEY(workspace_id, analysis_id, document_id),
    FOREIGN KEY(workspace_id, analysis_id) REFERENCES analyses(workspace_id, id) ON DELETE CASCADE,
    FOREIGN KEY(workspace_id, document_id) REFERENCES documents(workspace_id, id) ON DELETE CASCADE
);
CREATE INDEX analysis_source_document ON analysis_sources(workspace_id, document_id);

CREATE TABLE jobs (
    id TEXT PRIMARY KEY,
    workspace_id TEXT NOT NULL REFERENCES workspaces(id),
    kind TEXT NOT NULL CHECK(kind IN ('EXTRACT','ANALYZE')),
    resource_id TEXT NOT NULL,
    status TEXT NOT NULL CHECK(status IN ('QUEUED','RUNNING','COMPLETED','FAILED','CANCELLED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    error TEXT,
    UNIQUE(workspace_id, kind, resource_id)
);
CREATE INDEX jobs_pending ON jobs(status, available_at);

CREATE TABLE reviews (
    id TEXT PRIMARY KEY,
    workspace_id TEXT NOT NULL,
    analysis_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    match_index INTEGER NOT NULL,
    decision TEXT NOT NULL,
    note TEXT NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY(workspace_id, analysis_id) REFERENCES analyses(workspace_id, id) ON DELETE CASCADE,
    FOREIGN KEY(workspace_id, user_id) REFERENCES users(workspace_id, id)
);
CREATE INDEX reviews_analysis ON reviews(workspace_id, analysis_id, created_at);

CREATE TABLE audit_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    workspace_id TEXT NOT NULL REFERENCES workspaces(id),
    user_id TEXT NOT NULL,
    action TEXT NOT NULL,
    resource_id TEXT,
    created_at TEXT NOT NULL
);
CREATE INDEX audit_workspace ON audit_logs(workspace_id, id);

CREATE TABLE external_references (
    workspace_id TEXT NOT NULL REFERENCES workspaces(id),
    document_id TEXT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    provider_id TEXT NOT NULL,
    external_id TEXT NOT NULL,
    doi TEXT,
    title TEXT NOT NULL,
    authors_json TEXT,
    publication_year INTEGER,
    venue_or_publisher TEXT,
    landing_page_url TEXT,
    pdf_url TEXT,
    license TEXT,
    is_open_access INTEGER NOT NULL DEFAULT 0,
    citation_count INTEGER NOT NULL DEFAULT 0,
    citation_apa TEXT,
    citation_ieee TEXT,
    citation_mla TEXT,
    citation_bibtex TEXT,
    resolver_url TEXT,
    created_at TEXT NOT NULL,
    PRIMARY KEY (workspace_id, document_id)
);
CREATE INDEX ext_ref_doi ON external_references(workspace_id, doi);
