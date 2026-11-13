# Originality Desk — 40-Pull-Request Implementation Roadmap

This roadmap defines the sequential, incremental engineering programme transforming **Originality Desk** into a production-grade, institution-ready academic document originality and research integrity platform.

---

## Programme Overview

```mermaid
flowchart LR
    Phase1["Phase 1: Foundation & Security\n(PR 01-08)"] --> Phase2["Phase 2: Document & OCR\n(PR 09-13)"]
    Phase2 --> Phase3["Phase 3: Source Discovery\n(PR 14-20)"]
    Phase3 --> Phase4["Phase 4: Semantic Search\n(PR 21-27)"]
    Phase4 --> Phase5["Phase 5: AI & Review\n(PR 28-30)"]
    Phase5 --> Phase6["Phase 6: Persistence & Jobs\n(PR 31-35)"]
    Phase6 --> Phase7["Phase 7: Institutional SSO\n(PR 36-39)"]
    Phase7 --> Phase8["Phase 8: Validation & Release\n(PR 40)"]
```

---

## Phase Breakdown & PR Specifications

### PHASE 1 — Repository Quality, Architecture and Security Foundation

#### PR 01: Baseline Audit and Engineering Documentation
- **Branch:** `feature/pr-01-baseline-audit`
- **Scope:** Complete repository inspection, baseline test execution recording, architecture documentation (`docs/ARCHITECTURE.md`), engineering audit (`docs/ENGINEERING_AUDIT.md`), roadmap (`docs/IMPLEMENTATION_ROADMAP.md`), and PR progress tracker (`docs/PR_PROGRESS.md`).
- **Dependencies:** None (Baseline).
- **Deliverables:** `docs/ARCHITECTURE.md`, `docs/ENGINEERING_AUDIT.md`, `docs/IMPLEMENTATION_ROADMAP.md`, `docs/PR_PROGRESS.md`.
- **Acceptance:** Clean build, documented baseline test results (154 passing Maven tests), no regressions.

#### PR 02: Maven Dependency and Build Reliability
- **Branch:** `feature/pr-02-maven-build-reliability`
- **Scope:** Audit `pom.xml`, eliminate obsolete dependencies, pin plugins, verify reproducible builds, check open-source licenses, enforce strict compiler flags (`-Xlint:unchecked`).
- **Dependencies:** PR 01.
- **Acceptance:** Zero build warnings, clean dependency tree, all existing tests pass.

#### PR 03: Backend Package and Service Architecture
- **Branch:** `feature/pr-03-backend-architecture`
- **Scope:** Refactor service coupling, establish clean interfaces between API handlers, domain services, persistence DAOs, and analysis engines without altering existing API behavior.
- **Dependencies:** PR 02.
- **Acceptance:** Modular package organization, 100% backward compatibility for existing endpoints.

#### PR 04: Configuration and Secrets Management
- **Branch:** `feature/pr-04-configuration-secrets-management`
- **Scope:** Centralized typed configuration, environment variable overrides, removal of hardcoded values, fail-fast validation on startup, `.env.example` maintenance.
- **Dependencies:** PR 03.
- **Acceptance:** Server refuses to start with invalid configurations; zero secrets in logs.

#### PR 05: Authentication and Session Security Review
- **Branch:** `feature/pr-05-auth-session-security`
- **Scope:** Constant-time password verification, PBKDF2 iteration hardening, token-bucket login rate limiting, secure cookie flags (`SameSite=Strict; HttpOnly; Secure`), session timeout enforcement.
- **Dependencies:** PR 04.
- **Acceptance:** Comprehensive authentication security tests passing.

#### PR 06: Authorisation and Tenant Isolation
- **Branch:** `feature/pr-06-authorisation-tenant-isolation`
- **Scope:** Enforce workspace isolation across all endpoints and DAOs, prevent Insecure Direct Object References (IDOR), implement automated cross-tenant security test assertions.
- **Dependencies:** PR 05.
- **Acceptance:** Zero cross-tenant data leaks; any unauthorized workspace access returns 404 or 403.

#### PR 07: Secure File Upload and Validation
- **Branch:** `feature/pr-07-secure-file-upload-validation`
- **Scope:** Magic byte header verification, strict file extension checks, path traversal mitigation, decompression limit guards (max 200MB, 100:1 ratio), sanitized storage filenames.
- **Dependencies:** PR 06.
- **Acceptance:** Malformed, oversized, or malicious files rejected safely without resource exhaustion.

#### PR 08: API Validation and Error Handling
- **Branch:** `feature/pr-08-api-validation-error-handling`
- **Scope:** Standardized JSON error response envelope (`error`, `message`, `requestId`, `timestamp`), structured logging, correlation ID propagation (`X-Request-Id`).
- **Dependencies:** PR 07.
- **Acceptance:** Consistent API error schema across all routes; stack traces stripped from client responses.

---

### PHASE 2 — Document Processing and OCR

#### PR 09: Document Processing Abstraction
- **Branch:** `feature/pr-09-document-processing-abstraction`
- **Scope:** Introduce `DocumentParser` interface, common `ExtractionResult` model with character offsets, word counts, and language metadata.
- **Dependencies:** PR 08.
- **Acceptance:** Seamless parser dispatching across PDF, DOCX, and TXT.

#### PR 10: PDF Extraction Quality Improvements
- **Branch:** `feature/pr-10-pdf-extraction-improvements`
- **Scope:** Multi-page PDF extraction using Apache PDFBox 3.0, page boundary preservation, font encoding repairs, low-text detection.
- **Dependencies:** PR 09.
- **Acceptance:** Reproducible page-level text extraction with accurate offset coordinates.

#### PR 11: OCR Integration Using Tesseract
- **Branch:** `feature/pr-11-ocr-tesseract-integration`
- **Scope:** Optional OCR fallback for image-only or low-text PDFs using Tesseract OCR / Tess4J, page-by-page rendering, OCR flag metadata.
- **Dependencies:** PR 10.
- **Acceptance:** Scanned PDF extracts text with clear OCR provenance distinction.

#### PR 12: OCR Quality, Confidence and Resource Controls
- **Branch:** `feature/pr-12-ocr-quality-resource-controls`
- **Scope:** OCR confidence score tracking, timeout and memory boundaries, configurable language packs, graceful failure degradation.
- **Dependencies:** PR 11.
- **Acceptance:** OCR processes within bounded memory/CPU limits; timeouts degrade gracefully.

#### PR 13: Unified Extraction Pipeline
- **Branch:** `feature/pr-13-unified-extraction-pipeline`
- **Scope:** Combine PDF, DOCX, TXT, and OCR extraction into a unified pipeline with consistent metadata and quality reporting.
- **Dependencies:** PR 12.
- **Acceptance:** Single pipeline entry point for all document formats.

---

### PHASE 3 — Academic Source Discovery

#### PR 14: External Source Provider Architecture
- **Branch:** `feature/pr-14-external-source-provider-architecture`
- **Scope:** Provider-agnostic interface (`ScholarlySourceProvider`), source metadata models (DOI, authors, title, publisher, license, abstract).
- **Dependencies:** PR 13.
- **Acceptance:** Pluggable scholarly provider architecture with clean abstractions.

#### PR 15: OpenAlex Integration
- **Branch:** `feature/pr-15-openalex-integration`
- **Scope:** Official OpenAlex API client, rate limiting, pagination, citation metadata retrieval, and provenance tracking.
- **Dependencies:** PR 14.
- **Acceptance:** Real OpenAlex metadata retrieval with error handling and zero fabricated records.

#### PR 16: Crossref Integration
- **Branch:** `feature/pr-16-crossref-integration`
- **Scope:** Official Crossref API client, DOI resolution, bibliographic metadata deduplication against OpenAlex.
- **Dependencies:** PR 15.
- **Acceptance:** Verified Crossref metadata lookups and DOI validation.

#### PR 17: Semantic Scholar Integration
- **Branch:** `feature/pr-17-semantic-scholar-integration`
- **Scope:** Semantic Scholar API adapter, paper search, citation count retrieval, API key rate-limit handling.
- **Dependencies:** PR 16.
- **Acceptance:** Traceable Semantic Scholar queries with caching and backoff.

#### PR 18: CORE Integration and Full-Text Availability
- **Branch:** `feature/pr-18-core-integration-fulltext`
- **Scope:** CORE API client, open-access full-text and abstract discovery, clear distinction between abstract-only and full-text matches.
- **Dependencies:** PR 17.
- **Acceptance:** Explicit labeling of comparison scope (abstract vs. full article).

#### PR 19: Unified External Reference Search
- **Branch:** `feature/pr-19-unified-external-reference-search`
- **Scope:** Multi-provider federated query generator, deduplication by DOI/title, provider provenance tracking.
- **Dependencies:** PR 18.
- **Acceptance:** Parallel queries across configured providers with graceful single-provider failure.

#### PR 20: External Reference Evidence and Source Attribution
- **Branch:** `feature/pr-20-external-reference-attribution`
- **Scope:** External source attribution persistence, linking to official publisher landing pages, report citation formatting.
- **Dependencies:** PR 19.
- **Acceptance:** All external matches trace to verified publisher records with persistent provenance.

---

### PHASE 4 — Advanced Similarity and Semantic Search

#### PR 21: Similarity Engine Architecture
- **Branch:** `feature/pr-21-similarity-engine-architecture`
- **Scope:** Decouple exact, near-duplicate, and semantic similarity engines with a common `SimilarityStrategy` interface.
- **Dependencies:** PR 20.
- **Acceptance:** Modular engine interface preserving existing n-gram and shingling algorithms.

#### PR 22: Embedding Model Evaluation and Selection
- **Branch:** `feature/pr-22-embedding-model-selection`
- **Scope:** Benchmark Sentence Transformers models (`all-MiniLM-L6-v2`, `multilingual-e5-base`, `bge-m3`) for memory, latency, and academic domain accuracy.
- **Dependencies:** PR 21.
- **Acceptance:** Evidence-based model selection report and licensing audit.

#### PR 23: ONNX Model Runtime Integration
- **Branch:** `feature/pr-23-onnx-runtime-integration`
- **Scope:** ONNX Runtime Java integration, tokenizer implementation, tensor pooling, and embedding normalization.
- **Dependencies:** PR 22.
- **Acceptance:** Deterministic local embedding generation without external cloud dependencies.

#### PR 24: Document Chunking and Semantic Embeddings
- **Branch:** `feature/pr-24-document-chunking-embeddings`
- **Scope:** Sentence-aware text chunking with coordinate preservation, batch embedding computation, memory-bounded processing.
- **Dependencies:** PR 23.
- **Acceptance:** Traceable chunk embeddings mapped to document character offsets.

#### PR 25: PostgreSQL and pgvector Integration
- **Branch:** `feature/pr-25-postgresql-pgvector-integration`
- **Scope:** Optional PostgreSQL persistence layer with `pgvector` extension for cosine similarity vector search, keeping SQLite development path active.
- **Dependencies:** PR 24.
- **Acceptance:** Vector index search working under tenant-isolated SQL queries.

#### PR 26: Semantic Similarity Analysis
- **Branch:** `feature/pr-26-semantic-similarity-analysis`
- **Scope:** Semantic candidate retrieval, chunk-to-chunk cosine scoring, candidate passage highlighting.
- **Dependencies:** PR 25.
- **Acceptance:** Retrieval of conceptual paraphrases with clear passage highlighting.

#### PR 27: Hybrid Similarity and Evidence Aggregation
- **Branch:** `feature/pr-27-hybrid-similarity-aggregation`
- **Scope:** Unified scoring decomposing Exact, Near-Duplicate, Semantic, and Quoted overlap without conflation or double-counting.
- **Dependencies:** PR 26.
- **Acceptance:** Independent metric reporting; zero arbitrary "universal plagiarism percentages".

---

### PHASE 5 — Experimental AI-Writing Analysis

#### PR 28: AI-Writing Analysis Feasibility and Dataset Evaluation
- **Branch:** `feature/pr-28-ai-writing-feasibility-evaluation`
- **Scope:** Literature and dataset review on AI detection reliability, false-positive risks on non-native English, ethical guidelines.
- **Dependencies:** PR 27.
- **Acceptance:** Rigorous feasibility study documenting technical boundaries and ethical risks.

#### PR 29: Optional Experimental AI-Writing Analysis
- **Branch:** `feature/pr-29-experimental-ai-writing-analysis`
- **Scope:** Optional, isolated experimental indicator with prominent disclaimers, or honest documented unavailable state.
- **Dependencies:** PR 28.
- **Acceptance:** AI indicators strictly decoupled from similarity scores and human misconduct decisions.

#### PR 30: Human Review and Decision Workflow Improvements
- **Branch:** `feature/pr-30-human-review-decision-workflow`
- **Scope:** Reviewer decision states (`ACCEPTABLE_CITATION`, `QUOTATION`, `MISSING_ATTRIBUTION`, `SUSPECTED_PLAGIARISM`), audit notes, decision timestamps.
- **Dependencies:** PR 29.
- **Acceptance:** Fully auditable reviewer decision trail distinguishing machine findings from human verdicts.

---

### PHASE 6 — Database, Background Jobs and Storage Reliability

#### PR 31: Database Migration Framework
- **Branch:** `feature/pr-31-database-migration-framework`
- **Scope:** Flyway integration for versioned database migrations across both SQLite and PostgreSQL.
- **Dependencies:** PR 30.
- **Acceptance:** Repeatable, version-controlled schema migrations tested on clean and upgraded databases.

#### PR 32: Safe SQLite-to-PostgreSQL Migration
- **Branch:** `feature/pr-32-sqlite-to-postgresql-migration`
- **Scope:** Migration tooling to transfer workspaces, users, documents, analyses, and audit logs from SQLite to PostgreSQL with data validation.
- **Dependencies:** PR 31.
- **Acceptance:** Verified end-to-end data transfer with row counts, foreign key integrity, and zero data corruption.

#### PR 33: Durable Background Job Processing
- **Branch:** `feature/pr-33-durable-background-job-processing`
- **Scope:** Idempotent job execution, crash recovery, exponential backoff retries, stuck job detection, cancellation support.
- **Dependencies:** PR 32.
- **Acceptance:** Jobs survive application restarts and resume without duplicating analysis records.

#### PR 34: Object Storage Abstraction
- **Branch:** `feature/pr-34-object-storage-abstraction`
- **Scope:** `StorageService` interface with local filesystem implementation and S3-compatible provider support (e.g., SeaweedFS / MinIO).
- **Dependencies:** PR 33.
- **Acceptance:** Hot-swappable storage backends with content-addressed SHA-256 validation.

#### PR 35: Production Deployment and Backup Readiness
- **Branch:** `feature/pr-35-production-deployment-backup`
- **Scope:** Hardened Docker Compose configurations, persistent volume configurations, automated SQLite WAL/PostgreSQL backup scripts, restore drills.
- **Dependencies:** PR 34.
- **Acceptance:** Verified backup generation and restoration drill in under 60 seconds.

---

### PHASE 7 — Institutional Integrations

#### PR 36: Keycloak and OIDC Integration
- **Branch:** `feature/pr-36-keycloak-oidc-integration`
- **Scope:** OpenID Connect (OIDC) client implementation, JWT signature verification, institutional claim mapping.
- **Dependencies:** PR 35.
- **Acceptance:** Verified OIDC authentication against local Keycloak container.

#### PR 37: SAML 2.0 and Institutional Authentication
- **Branch:** `feature/pr-37-saml2-authentication`
- **Scope:** SAML 2.0 Service Provider implementation, XML signature verification, replay protection.
- **Dependencies:** PR 36.
- **Acceptance:** Successful SAML assertion validation and user provisioning.

#### PR 38: LTI 1.3 Advantage Integration
- **Branch:** `feature/pr-38-lti-1-3-advantage-integration`
- **Scope:** LTI 1.3 Core launch validation, dynamic registration, context/role mapping for Canvas and Moodle.
- **Dependencies:** PR 37.
- **Acceptance:** Verified LTI launch simulation with tenant isolation.

#### PR 39: SCIM 2.0 Provisioning and Institutional Administration
- **Branch:** `feature/pr-39-scim2-provisioning`
- **Scope:** SCIM 2.0 REST endpoints for user and group lifecycle management (`/scim/v2/Users`, `/scim/v2/Groups`).
- **Dependencies:** PR 38.
- **Acceptance:** Standardized user sync without cross-workspace boundary leaks.

---

### PHASE 8 — Final Quality, Performance and Release Readiness

#### PR 40: End-to-End Validation, Performance, Security and Release Documentation
- **Branch:** `feature/pr-40-release-readiness`
- **Scope:** Full regression test execution, end-to-end institutional workflow verification, throughput & latency benchmarking, final documentation synchronization, release readiness sign-off.
- **Dependencies:** PR 39.
- **Acceptance:** All 40 PRs verified, test suite 100% passing, comprehensive release report published.
