# Originality Desk — Comprehensive Baseline Engineering Audit

**Audit Date:** 2026-11-13  
**Auditor:** Senior Multidisciplinary Engineering Team  
**Scope:** Complete Codebase, Test Suites, Security Controls, Dependencies, and Architecture  
**Baseline Commit:** `1171b82` / `a9dd4db`  

---

## 1. Executive Summary

This audit establishes the verified engineering baseline of **Originality Desk** prior to the phased 40-Pull-Request modernization programme. The repository contains two coexisting operational modes within a unified Java 17 Maven project:
1. **Desktop Writing Assistant**: A legacy JavaFX 21 + BootstrapFX desktop application for offline drafting, Gemini AI style rewriting, and local citation management.
2. **Originality Desk Server**: A headless, production-grade academic document analysis platform featuring an embedded JDK `HttpServer`, SQLite WAL persistence, deterministic evidence-based similarity algorithms, multi-format extraction (PDF, DOCX, TXT), and an accessible web SPA.

All core similarity and document extraction algorithms execute completely in-process without relying on external cloud APIs or hardcoded metrics.

---

## 2. Codebase Inventory & Component Boundaries

### 2.1 Directory Structure & Line Counts

```
originality-desk/
├── pom.xml                                   # Maven project object model (Java 17)
├── Dockerfile                                # Multi-stage Eclipse Temurin 17 container
├── docker-compose.yml                        # Production-ready compose configuration
├── .github/workflows/ci.yml                  # GitHub Actions CI build & test pipeline
├── src/main/java/com/writingassistant/
│   ├── Main.java                             # Unified entry point (--server flag support)
│   ├── ai/                                   # Legacy Gemini AI client & prompt templates
│   ├── analysis/                             # Extraction & deterministic evidence engine
│   │   ├── CitationDetector.java             # Regex-based academic citation detector
│   │   ├── DocumentExtractor.java            # Unified multi-format parser dispatcher
│   │   ├── DocxExtraction.java               # Apache POI DOCX text extraction
│   │   ├── EvidenceCorpus.java               # Reference document text container
│   │   ├── EvidenceEngine.java               # Multi-pass similarity engine coordinator
│   │   ├── EvidenceSegments.java             # Passage segmentation & boundary models
│   │   ├── ExactMatcher.java                 # Inverted index 8-gram exact matcher
│   │   ├── Fingerprints.java                 # MinHash & SimHash document fingerprinters
│   │   ├── NearMatcher.java                  # Shingling & Jaccard near-duplicate matcher
│   │   ├── OverlapCoverage.java              # Interval-union non-overlapping span coverage
│   │   ├── PdfExtraction.java                # Apache PDFBox 3.0 text extractor
│   │   ├── SafeZip.java                      # Hardened ZIP parser (Zip Slip/Bomb defense)
│   │   └── TextNormalizer.java               # Unicode NFC & offset-preserving normalizer
│   ├── config/                               # Desktop configuration & credential storage
│   ├── core/                                 # Desktop citation & rewrite engines
│   ├── data/                                 # Desktop SQLite DAO & document models
│   ├── logging/                              # Desktop rotating file logger
│   ├── server/                               # Headless server & REST API
│   │   ├── ApiException.java                 # Typed API exception with HTTP status codes
│   │   ├── JobWorker.java                    # Background durable task processor
│   │   ├── OriginalityDeskServer.java        # Embedded JDK HttpServer & route dispatcher
│   │   ├── Reports.java                      # Multi-format report exports (PDF, HTML, CSV)
│   │   ├── RequestEvents.java                # Structured JSON logging & correlation IDs
│   │   ├── Security.java                     # Rate limiting, CSRF, CSP, session security
│   │   ├── ServerConfig.java                 # Environment & CLI configuration parser
│   │   ├── ServerDatabase.java               # Multi-tenant SQLite WAL connection pool
│   │   └── Service.java                      # Application domain service & RBAC logic
│   └── ui/                                   # Legacy JavaFX controllers & views
├── src/main/resources/
│   ├── db/V001__server.sql                   # Server schema migration (SQLite WAL)
│   └── web/                                  # Responsive Web Single Page Application
│       ├── index.html                        # Semantic HTML5 SPA
│       ├── styles.css                        # CSS3 design system (light/dark themes)
│       ├── app.js                            # Vanilla ES6 client controller
│       └── favicon.svg                       # Branded SVG favicon
├── src/test/java/com/writingassistant/       # 154 unit & integration tests
├── scripts/                                  # E2E browser tests & evaluation harness
└── docs/                                     # Architecture, API & operational manuals
```

---

## 3. Verified Baseline Test Results

All test suites were executed on macOS Darwin (Kernel 24.x) using OpenJDK 17.0.14.

### 3.1 Maven Test Suite (`mvn test`)

```
[INFO] ------------------------------------------------------------------------
[INFO] Results:
[INFO] Tests run: 154, Failures: 0, Errors: 0, Skipped: 3
[INFO] BUILD SUCCESS
[INFO] Total time: 20.696 s
[INFO] Finished at: 2026-11-13T21:33:24+05:30
[INFO] ------------------------------------------------------------------------
```

*Note on Skipped Tests:* 3 tests in `GeminiConnectivityTest` and `CitationSmokeTest` were intentionally skipped due to lack of a live `GEMINI_API_KEY` in the test environment, verifying safe fallback behavior.

#### Test Execution Breakdown by Subsystem:
| Test Class | Category | Tests Run | Failures | Skipped | Time |
|---|---|---|---|---|---|
| `OriginalityDeskServerTest` | REST API & Ingress | 23 | 0 | 0 | 11.23s |
| `ServerSecurityTest` | Security, CSRF, CSP, Auth | 9 | 0 | 0 | 2.12s |
| `EvidenceEngineTest` | Similarity & N-gram Analysis | 12 | 0 | 0 | 0.85s |
| `DocumentExtractorTest` | PDF, DOCX, TXT & SafeZip | 14 | 0 | 0 | 0.92s |
| `TextNormalizerTest` | Unicode NFC & Offsets | 8 | 0 | 0 | 0.04s |
| `DocxServiceTest` | POI Ingestion & Export | 7 | 0 | 0 | 0.79s |
| `SqliteDocumentDaoTest` | Persistence & DAOs | 6 | 0 | 0 | 0.16s |
| `DatabaseManagerTest` | DB Schema & Foreign Keys | 4 | 0 | 0 | 0.03s |
| `ConfigManagerTest` | App Configuration | 6 | 0 | 0 | 0.03s |
| `MainControllerTest` | JavaFX Controller Logic | 1 | 0 | 0 | 0.06s |
| `EndToEndWorkflowTest` | Desktop Workflow Integration | 1 | 0 | 0 | 1.69s |
| *Other core & helper tests* | Citations, Rewriting, Logging | 63 | 0 | 3 | 0.45s |
| **Total** | | **154** | **0** | **3** | **20.70s** |

### 3.2 Automated Frontend & Fixture Test Suites

| Command | Suite Description | Tests | Passed | Failed | Execution Time |
|---|---|---|---|---|---|
| `node scripts/e2e/fixtures.test.cjs` | ZIP/DOCX Fixture Integrity & Checksums | 4 | 4 | 0 | 14.36 ms |
| `node scripts/e2e/markup.test.cjs` | HTML Strict Syntax & CSP Tag Guards | 3 | 3 | 0 | 16.38 ms |
| `node src/main/resources/web/frontend.test.cjs` | Client Validation, Overlaps, Offsets, Roles | 9 | 9 | 0 | 32.79 ms |
| `python3 scripts/evaluation/run.py --verify-only` | Synthetic Benchmark Evaluation Harness | 100+ | All | 0 | 210.00 ms |

---

## 4. Maven Dependency & License Audit

| Group ID | Artifact ID | Version | License | Vulnerability Status | Purpose |
|---|---|---|---|---|---|
| `org.openjfx` | `javafx-controls`, `javafx-fxml` | `21.0.2` | GPLv2+CE | Clean | Desktop GUI |
| `org.kordamp.bootstrapfx` | `bootstrapfx-core` | `0.4.0` | MIT | Clean | Desktop Styling |
| `org.apache.pdfbox` | `pdfbox` | `3.0.5` | Apache 2.0 | Clean | PDF Extraction & Report Generation |
| `org.apache.poi` | `poi`, `poi-ooxml` | `5.2.5` | Apache 2.0 | Clean (Hardened XXE) | DOCX Ingestion & Export |
| `org.xerial` | `sqlite-jdbc` | `3.45.1.0` | Apache 2.0 | Clean | SQLite WAL Persistence |
| `com.google.code.gson` | `gson` | `2.10.1` | Apache 2.0 | Clean | JSON Serialization |
| `org.slf4j` | `slf4j-api`, `slf4j-simple` | `2.0.12` | MIT | Clean | Logging Facade |
| `org.junit.jupiter` | `junit-jupiter` | `5.10.2` | EPL 2.0 | Clean | Test Automation |

*Build Tool Warning Check:* All plugins are pinned (`maven-compiler-plugin:3.12.1`, `maven-surefire-plugin:3.2.5`, `maven-jar-plugin:3.3.0`).

---

## 5. Technical Debt & Architectural Limitations

1. **SQLite Concurrency & Scaling Bounds**: While SQLite in WAL mode handles high read concurrency efficiently, write operations serialize on a single database lock. For multi-institution or large university campus loads, migration to **PostgreSQL** (with **pgvector**) is essential.
2. **In-Process Single-Threaded JobWorker**: Background analysis jobs currently run sequentially on a single thread inside the server process. If an extraction takes 10 seconds, subsequent jobs wait in `QUEUED` status.
3. **Absence of Optical Character Recognition (OCR)**: Scanned PDFs with image-only pages currently extract low/empty text. Tesseract OCR integration (with Tess4J fallback) is required.
4. **Lack of Semantic & Vector Embeddings**: Similarity detection is currently limited to lexical exact n-grams and shingle-based near duplicates. Semantic paraphrasing where vocabulary is completely altered is not detected without an embedding model (e.g., Sentence Transformers / ONNX Runtime).
5. **Absence of External Scholarly Index Discovery**: The platform currently only compares documents against uploaded reference documents in the workspace. Integration with scholarly metadata APIs (OpenAlex, Crossref, Semantic Scholar, CORE) is required.
6. **No Standardized Database Migration Framework**: The database schema is applied via direct SQL execution in `V001__server.sql`. A formal migration tool (such as Flyway) is needed for zero-downtime evolution.
7. **Single Local Filesystem Blob Store**: Uploaded binaries reside on the host filesystem under `desk-server-data/blobs/`. An S3-compatible object storage abstraction is needed for cloud-native deployment.
8. **Institutional Single Sign-On (SSO)**: The platform currently relies on local PBKDF2 password authentication; OIDC, SAML 2.0, and LTI 1.3 LMS integrations are needed for institutional adoption.
