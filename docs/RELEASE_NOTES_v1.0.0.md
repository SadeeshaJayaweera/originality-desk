# Originality Desk — Release Notes v1.0.0

Originality Desk v1.0.0 represents the culmination of a rigorous 40-Pull-Request engineering modernisation programme transforming the platform into a secure, extensible, high-performance, and institution-ready academic originality analysis system.

---

## Executive Summary

| Metric | Baseline | Modernised v1.0.0 | Improvement |
|:---|:---|:---|:---|
| **Architectural Modules** | Monolithic server | 8 decoupled sub-systems | Complete separation of concerns |
| **Document Extractors** | Basic PDF/DOCX | Unified multi-stage pipeline + OCR fallback | High-fidelity extraction & scanned document support |
| **Scholarly Sources** | Local files only | 4 global providers (OpenAlex, Crossref, Semantic Scholar, CORE) | Federated search across 250M+ scholarly works |
| **Similarity Engine** | Lexical exact/near shingling | Pluggable multi-strategy (Exact, Near, ONNX Semantic, Hybrid) | Decomposed overlap, zero score inflation |
| **AI Writing Analysis** | None / Naive | Ethical opt-in stylometry with mandatory ESL bias disclaimers | Transparent entropy/burstiness, zero disciplinary automation |
| **Review Workflows** | Unstructured notes | Formal 4-tier state machine + conflict adjudication | Full governance audit trail, role hierarchies |
| **Persistence & Storage** | Embedded SQLite | Dual-mode SQLite + PostgreSQL/pgvector, pluggable S3 object storage | Enterprise scale, zero data corruption |
| **Identity & SSO** | Local passwords only | Keycloak OIDC, SAML 2.0 (Shibboleth/InCommon), LTI 1.3 Advantage, SCIM 2.0 | Turnkey LMS & institutional federation |
| **Automated Tests** | 154 unit tests | 240+ comprehensive tests across all modules | 100% test pass rate |

---

## 40-Pull-Request Transformation Summary

### Phase 1: Repository Quality, Architecture & Security Foundation (PR 01 - 08)
- **PR 01**: Baseline audit, dependency mapping, and 40-PR tracking matrix (`ARCHITECTURE.md`, `ENGINEERING_AUDIT.md`, `IMPLEMENTATION_ROADMAP.md`).
- **PR 02**: Maven build hardening, pinned reproducible builds, dependency vulnerability audit.
- **PR 03**: Decoupled package boundaries, introducing operations interfaces (`AuthOperations`, `DocumentOperations`, `AnalysisOperations`, `ReviewOperations`, `SystemOperations`).
- **PR 04**: Environment configuration profiles, secret masking, production fail-safe validator (`ServerConfig`).
- **PR 05**: Cryptographic session security review, hardened PBKDF2 hashing, session entropy audit.
- **PR 06**: Multi-tenant authorization and IDOR elimination across all API boundaries.
- **PR 07**: Hardened file upload validation with magic-byte verification, zip bomb defense, and randomized blob storage.
- **PR 08**: Standardized RFC 7807-compliant API error envelope, `X-Request-Id` correlation, zero stack trace leakage.

### Phase 2: Document Processing and OCR Integration (PR 09 - 13)
- **PR 09**: Pluggable `DocumentParser` strategy pattern with extensible parser registry.
- **PR 10**: High-quality PDF extraction with positional sorting, typographic ligature normalization, and soft-hyphen de-hyphenation.
- **PR 11**: Native Tesseract OCR integration via decoupled `OcrEngine` adapter with process isolation and temp directory jailing.
- **PR 12**: OCR resource governance (page boundaries, semaphore concurrency limits, TSV confidence scoring).
- **PR 13**: `UnifiedDocumentExtractor` coordinating native extraction, text density assessment, automatic OCR fallback, and structured telemetry.

### Phase 3: External Scholarly Reference Integration (PR 14 - 20)
- **PR 14**: `ExternalSourceProvider` SPI, canonical `ExternalScholarlyWork`, thread-safe provider registry, DOI normalizer.
- **PR 15**: OpenAlex REST API client with polite-pool mailto forwarding, inverted-index abstract reconstruction, and DOI resolution.
- **PR 16**: Crossref REST client with JATS XML tag stripping, publisher metadata extraction, and rate-limit resilience.
- **PR 17**: Semantic Scholar Academic Graph client with OA PDF discovery, citation counts, and API key support.
- **PR 18**: CORE API v3 client with Bearer authentication and full-text document discovery.
- **PR 19**: Federated parallel search coordinator with cross-provider DOI/title deduplication and full-text prioritization.
- **PR 20**: Evidence-based citation formatter (APA 7th, IEEE, MLA 9th, BibTeX) and workspace reference ingestion.

### Phase 4: Advanced Similarity and Semantic Search (PR 21 - 27)
- **PR 21**: Pluggable similarity strategy architecture (`ExactSimilarityStrategy`, `NearDuplicateSimilarityStrategy`, `CompositeSimilarityEngine`).
- **PR 22**: Empirical embedding model selection framework (`all-MiniLM-L6-v2`, `multilingual-e5-base`, `bge-m3`) with MTEB benchmark mapping.
- **PR 23**: Native ONNX Runtime Java execution engine, pure-Java WordPiece subword tokenizer, attention-weighted mean pooling, L2 normalization.
- **PR 24**: Sentence-aware academic document chunking with character-level coordinate preservation and sliding window overlaps.
- **PR 25**: Dual-mode vector persistence: SQLite WAL vector store for desktop and PostgreSQL `pgvector` HNSW index for enterprise deployments.
- **PR 26**: Semantic similarity analyzer detecting conceptual paraphrasing and passage consolidation.
- **PR 27**: Decomposed hybrid similarity aggregation (exact, near, semantic, quoted, cited) with interval-union coverage and zero double-counting.

### Phase 5: Experimental AI-Writing Analysis (PR 28 - 29)
- **PR 28**: Scientific feasibility study documenting statistical limitations and Stanford ESL disparate impact bias (Liang et al., 2023).
- **PR 29**: Opt-in statistical stylometry analyzer (Shannon entropy, burstiness metrics), mandatory acknowledgment of ESL bias, strict prohibition of disciplinary sanctions.

### Phase 6: Workflow, Persistence and Operational Reliability (PR 30 - 35)
- **PR 30**: Human review state machine (`APPROVED`, `FLAGGED`, `EXEMPTED`, `ESCALATED`), role hierarchy, consensus and conflict adjudication.
- **PR 31**: Flyway 9.22.3 database migration framework with dialect resolution and idempotent schema versioning (`V1__initial_schema.sql`).
- **PR 32**: Safe SQLite-to-PostgreSQL institutional migration engine with topological foreign-key ordering and atomic rollback.
- **PR 33**: Durable background job queue with distributed leases, heartbeat tracking, stale worker reclamation, and exponential backoff.
- **PR 34**: Pluggable object storage abstraction (Local directory-jailed filesystem and zero-dependency S3-compatible backend for SeaweedFS/MinIO/AWS S3).
- **PR 35**: Multi-container production deployment stack (`docker-compose.prod.yml`) and automated online backup engine (`VACUUM INTO` and `pg_dump`).

### Phase 7: Institutional Identity and System Integration (PR 36 - 39)
- **PR 36**: Keycloak and OpenID Connect (OIDC) Single Sign-On, JWT token validation, dynamic role mapping, and JIT provisioning.
- **PR 37**: SAML 2.0 Service Provider (SP) implementation for Shibboleth, InCommon, eduGAIN, and Microsoft Entra ID; XXE-defended response validator with XML digital signatures.
- **PR 38**: 1EdTech LTI 1.3 Advantage LMS integration (Canvas, Moodle, Blackboard, D2L) with state/nonce replay defense, course workspace partitioning, and LIS role mapping.
- **PR 39**: RFC 7643 / RFC 7644 SCIM 2.0 institutional provisioning protocol for automated user and group lifecycle management and soft account deactivation.

### Phase 8: Release Readiness and Governance (PR 40)
- **PR 40**: Full-suite regression validation (`ReleaseReadinessTest`), complete release documentation, and formal 11-discipline engineering sign-off.

---

## Upgrade & Migration Runbook

For existing installations running embedded SQLite, refer to:
- `docs/SQLITE_TO_POSTGRESQL_MIGRATION_GUIDE.md`
- `docs/DATABASE_MIGRATION_FRAMEWORK.md`
- `docs/PRODUCTION_DEPLOYMENT_AND_BACKUP_GUIDE.md`
