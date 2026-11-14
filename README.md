# Originality Desk 🔍

[![Java 17](https://img.shields.io/badge/Java-17%2B-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![SQLite](https://img.shields.io/badge/SQLite-3.45.1-003B57?logo=sqlite&logoColor=white)](https://www.sqlite.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15%2B%20pgvector-336791?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Apache PDFBox](https://img.shields.io/badge/Apache%20PDFBox-3.0.5-D22128?logo=apache&logoColor=white)](https://pdfbox.apache.org/)
[![Apache POI](https://img.shields.io/badge/Apache%20POI-5.2.5-D22128?logo=apache&logoColor=white)](https://poi.apache.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Build & Tests](https://img.shields.io/badge/Tests-349%20Passing-brightgreen)]()

An evidence-based academic document originality, similarity detection, and citation analysis platform. Built in **Java 17** with a zero-external-dependency embedded server, a deterministic multi-strategy similarity engine, and an accessible, responsive browser workspace.

Originality Desk bridges the gap between mechanical similarity scores and genuine academic integrity review by providing transparent, reproducible passage-level evidence, citation context analysis, human reviewer decision workflows, and multi-format report exports (PDF, HTML, JSON, CSV).

The repository also preserves the original **Desktop Writing Assistant** (JavaFX + BootstrapFX) for offline authorial drafting, style rewriting, and citation formatting.

---

## 📸 Screenshots

Captured from the live application serving synthetic test corpora in Chromium:

### 1. Public Landing Page
*Responsive landing page with feature explanations, platform overview, and interactive demonstration.*

[![Public Landing](docs/screenshots/landing.png)](docs/screenshots/landing.png)

---

### 2. Evidence-Led Analysis Report
*Traceable side-by-side passage comparison, citation observations, and human reviewer notes.*

[![Analysis Report](docs/screenshots/report.png)](docs/screenshots/report.png)

---

### 3. Mobile Viewport (Dark Theme)
*Persisted dark theme, keyboard-accessible navigation on a 390 × 844 mobile viewport.*

<p align="center">
  <a href="docs/screenshots/mobile.png">
    <img src="docs/screenshots/mobile.png" alt="Mobile Viewport (Dark Theme)" width="420" />
  </a>
</p>

*Image capture metadata and checksums are verified in [docs/screenshots/metadata.json](docs/screenshots/metadata.json).*

---

## 🎯 The Problem & Our Solution

### The Problem in Academic Integrity Review
1. **Opaque "Similarity Percentages"**: Commercial plagiarism detection tools return a single, unexplainable percentage score without distinguishing between verbatim copying, legitimate citations, common academic phrasing, or bibliography lists.
2. **Fabricated & Unreliable AI Detectors**: Heuristic AI detectors generate alarming false-positive rates on non-native English writing and formal academic prose, frequently leading to unjust accusations of misconduct.
3. **Data Privacy & Surveillance Concerns**: Uploading confidential academic manuscripts, patent applications, or grant proposals to third-party cloud servers risks intellectual property leaks and breaches non-disclosure agreements.
4. **Patchwriting & Synonym Substitution**: Superficial word-swapping tools bypass simple exact-string checks while retaining the original author's structural sequence.

### The Solution Provided by Originality Desk
- **Evidence-First Transparency**: Similarity is reported as verifiable passage-level evidence pointing to explicit, authorized reference documents.
- **Independent Metric Decomposition**: Distinct metrics for **Exact Matches**, **Near-Duplicate Paraphrasing**, **Quoted Passages**, and **Bibliography Overlap** using interval union mathematics that prevents double-counting.
- **Citation-Aware Context**: Automatic detection of standard academic citation styles (APA, IEEE, MLA, Harvard, Vancouver) adjacent to matching passages.
- **Human Reviewer Decisions**: Tools for lecturers and supervisors to mark passages as acceptable citations, missing attributions, or confirmed quotations with persistent reviewer notes.
- **100% Offline-First Privacy**: The core similarity detection engine runs completely in-process. Documents are never transmitted to third-party AI APIs or external web crawlers.

---

## 🏗️ System Architecture & Workflows

### 1. Overall System Architecture

```mermaid
flowchart TD
    subgraph Clients ["Client Layer"]
        Browser["Modern Web Browser (SPA)"]
        CLI["API Clients / cURL"]
        DesktopApp["Desktop Writing Assistant (JavaFX 21)"]
        LMS["LTI 1.3 LMS (Canvas / Moodle / Blackboard)"]
    end

    subgraph Ingress ["Ingress & Security Boundary"]
        ReverseProxy["TLS Reverse Proxy (Nginx / Caddy)"]
    end

    subgraph Server ["Originality Desk Application Server (Java 17)"]
        HttpListener["Embedded JDK HttpServer (Port 8080)"]
        SecurityFilter["Security Filter (Host/Origin CSRF Check, Limiter, CSP)"]
        RestAPI["REST API Router (/api/v1/*, /scim/v2/*)"]
        IdentityService["Identity & SSO (SAML 2.0 / OIDC / SCIM / LTI)"]
        
        subgraph Core_Engines ["Core Analysis & Document Engines"]
            DocExtractor["Document Extractor (PDF, DOCX, TXT)"]
            TextNormalizer["Text Normalizer (Unicode NFC, Offsets)"]
            EvidenceEngine["Similarity Engine (Exact N-gram, Near Shingling)"]
            SemanticEngine["Semantic Retrieval (ONNX Runtime, all-MiniLM-L6-v2)"]
            CitationDetector["Citation Pattern Detector (APA/IEEE/MLA)"]
            ExternalSources["Federated External Search (Crossref, Semantic Scholar, CORE)"]
            ReportGen["Multi-Format Report Generator (PDFBox, HTML, CSV)"]
        end

        subgraph Background ["Background Processing"]
            JobQueue["Durable Job Dispatcher & Lease Manager"]
            Worker["Bounded Computation Worker Thread"]
        end
    end

    subgraph Storage ["Storage & Persistence Layer"]
        DB["SQLite 3.45 (WAL Mode) / PostgreSQL 15+ (pgvector)"]
        BlobStore["Object Storage (POSIX Local 0700 / AWS S3 Compatible)"]
    end

    Browser -->|HTTPS| ReverseProxy
    CLI -->|HTTPS| ReverseProxy
    LMS -->|LTI 1.3 / HTTPS| ReverseProxy
    ReverseProxy -->|HTTP| HttpListener
    HttpListener --> SecurityFilter
    SecurityFilter --> RestAPI
    RestAPI --> IdentityService
    RestAPI --> Core_Engines
    RestAPI --> JobQueue
    JobQueue --> Worker
    Worker --> Core_Engines
    Core_Engines --> DB
    Core_Engines --> BlobStore
    DesktopApp -.->|Offline Direct Access| DB
```

---

### 2. Document Processing Lifecycle

```mermaid
stateDiagram-v2
    [*] --> UPLOADED: File Received via Stream
    UPLOADED --> VALIDATING: Size & Magic Byte Check
    VALIDATING --> QUEUED: SHA-256 Deduplication & Blob Storage
    VALIDATING --> FAILED: Invalid Type or Over 10MB
    QUEUED --> EXTRACTING: Claimed by Background Worker
    EXTRACTING --> NORMALISING: Text Extracted (PDFBox / POI)
    EXTRACTING --> FAILED: Parser Error or Bomb Detected
    NORMALISING --> SEGMENTING: UTF-16 Offsets & Tokens Mapped
    SEGMENTING --> READY: Available for Analysis
    READY --> ANALYSING: Analysis Job Queued
    ANALYSING --> GENERATING_REPORT: Similarity & Citations Evaluated
    GENERATING_REPORT --> COMPLETED: Snapshot Persisted
    ANALYSING --> FAILED: Execution Timeout (90s)
    ANALYSING --> CANCELLED: User Requested Cancellation
    FAILED --> RETRYING: Exponential Backoff (Max 3)
    RETRYING --> QUEUED
    COMPLETED --> [*]
```

---

### 3. Similarity Analysis Pipeline

```mermaid
flowchart TD
    subgraph Ingestion ["1. Document Ingestion"]
        DocSub["Submitted Document"]
        DocRefs["Selected Reference Documents"]
        ExtRefs["External Scholarly Sources (Crossref, Semantic Scholar, CORE)"]
    end

    subgraph Normalization ["2. Normalization & Tokenization"]
        Norm["Unicode NFC, Line-Break & Homoglyph Normalizer"]
        Offsets["Preserve Original UTF-16 Character Offsets"]
        Chunks["Sentence-Aware Chunking & Coordinate Preservation"]
    end

    subgraph Similarity ["3. Multi-Strategy Similarity Analysis"]
        Exact["Exact Matcher: Inverted N-Gram Index (n >= 8)"]
        Near["Near Matcher: Word Shingling & Jaccard Index (t >= 0.70)"]
        Semantic["Semantic Matcher: Local ONNX Vector Embeddings (MiniLM-L6)"]
        Union["Interval Union Coverage: Non-Overlapping Spans"]
    end

    subgraph Citation ["4. Citation Contextualization"]
        Cite["Citation Detector: Regex Patterns & Quotation Search"]
        Context["Classify: Quoted / Cited / Uncited Matches"]
    end

    subgraph Persistence ["5. Snapshot & Exports"]
        Snapshot["Persist Immutable Analysis Snapshot"]
        Exports["Generate Exports: PDF, HTML, JSON, CSV"]
    end

    DocSub --> Norm
    DocRefs --> Norm
    ExtRefs --> Norm
    Norm --> Offsets
    Offsets --> Chunks
    Offsets --> Exact
    Offsets --> Near
    Chunks --> Semantic
    Exact --> Union
    Near --> Union
    Semantic --> Union
    Union --> Context
    Cite --> Context
    Context --> Snapshot
    Snapshot --> Exports
```

---

### 4. Authentication & Workspace Authorization

```mermaid
sequenceDiagram
    autonumber
    actor User as User / Browser
    participant Svr as Originality Desk Server
    participant DB as Relational Database (SQLite / Postgres)

    User->>Svr: POST /api/v1/auth/register (name, email, password, workspace)
    Note over Svr: PBKDF2-HMAC-SHA256 (210,000 iterations)
    Svr->>DB: INSERT into workspaces, users (Role: INSTITUTION_ADMIN)
    Note over Svr: Generate 256-bit cryptographically secure token
    Svr->>DB: INSERT into sessions (SHA-256 token_hash, 12h expiry)
    Svr-->>User: 201 Created (Set-Cookie: desk_session=..., HttpOnly, SameSite=Strict)

    User->>Svr: POST /api/v1/documents (with Origin header & desk_session cookie)
    Note over Svr: Validate Host authority & Origin == publicOrigin (Anti-CSRF)
    Svr->>DB: SELECT workspace_id, role FROM sessions JOIN users
    Note over Svr: Derive workspace_id strictly from session
    Svr->>DB: INSERT INTO documents WHERE workspace_id = ?
    Svr-->>User: 201 Created (Document Metadata)
```

---

### 5. Relational Database Entity-Relationship Diagram

```mermaid
erDiagram
    WORKSPACES ||--o{ USERS : contains
    WORKSPACES ||--o{ DOCUMENTS : owns
    WORKSPACES ||--o{ ANALYSES : runs
    WORKSPACES ||--o{ JOBS : tracks
    WORKSPACES ||--o{ AUDIT_LOGS : records
    USERS ||--o{ SESSIONS : establishes
    USERS ||--o{ REVIEWS : authors
    DOCUMENTS ||--o{ ANALYSES : submitted_as
    ANALYSES ||--o{ ANALYSIS_SOURCES : references
    DOCUMENTS ||--o{ ANALYSIS_SOURCES : included_in
    ANALYSES ||--o{ REVIEWS : annotated_by

    WORKSPACES {
        string id PK
        string name
        string created_at
    }
    USERS {
        string id PK
        string workspace_id FK
        string email UK
        string name
        string role
        string password_hash
        string created_at
    }
    SESSIONS {
        string token_hash PK
        string workspace_id FK
        string user_id FK
        integer expires_at
        integer created_at
    }
    DOCUMENTS {
        string id PK
        string workspace_id FK
        string title
        string filename
        string kind
        string status
        string sha256
        string storage_name UK
        integer word_count
        integer page_count
        integer bytes
        string text
    }
    ANALYSES {
        string id PK
        string workspace_id FK
        string document_id FK
        string title
        string status
        string profile
        integer source_count
        string result_json
        string idempotency_key UK
    }
    ANALYSIS_SOURCES {
        string workspace_id PK,FK
        string analysis_id PK,FK
        string document_id PK,FK
    }
    JOBS {
        string id PK
        string workspace_id FK
        string kind
        string resource_id
        string status
        integer attempts
        integer available_at
    }
    REVIEWS {
        string id PK
        string workspace_id FK
        string analysis_id FK
        string user_id FK
        integer match_index
        string decision
        string note
        string created_at
    }
    AUDIT_LOGS {
        integer id PK
        string workspace_id FK
        string user_id
        string action
        string resource_id
        string created_at
    }
```

---

### 6. Background Job Worker & Failure Recovery

```mermaid
flowchart LR
    API["API Request"] -->|Enqueue| JobsTable[("jobs Table (SQLite / Postgres)")]
    Dispatcher["JobWorker Dispatcher Thread"] -->|Poll available_at| JobsTable
    Dispatcher -->|Claim & Lease Management| Computation["Single-Thread Bounded Worker"]
    Computation -->|Execute Extraction or Similarity| CoreEngine["Analysis Engine"]
    CoreEngine -->|Success| CommitSuccess["Mark COMPLETED & Save Result"]
    CoreEngine -->|Transient Error| Backoff["Exponential Backoff Retry (Max 3)"]
    CoreEngine -->|Timeout >90s| CommitFail["Mark FAILED with Error Trace"]
    CommitSuccess --> JobsTable
    Backoff --> JobsTable
    CommitFail --> JobsTable

    Startup["Server Startup"] --> Recovery["JobWorker.recover()"]
    Recovery -->|Interrupted RUNNING Jobs| Reset["Reset to QUEUED or Dead-Letter"]
    Recovery -->|Orphaned Blobs| Prune["Wipe Blobs Missing in DB"]
```

---

## 💻 Technology Stack

| Component | Technology | Rationale |
|---|---|---|
| **Programming Language** | Java 17 LTS (compile target 17) | Strict memory safety, cross-platform portability, high throughput |
| **HTTP Server** | JDK `HttpServer` (Java Standard Library) | Zero external web framework dependencies, minimal attack surface |
| **Relational Database** | SQLite 3.45 / PostgreSQL 15+ | Transactional ACID safety, WAL mode, Flyway migrations, pgvector support |
| **Semantic Embeddings** | ONNX Runtime Java (`all-MiniLM-L6-v2`) | High-speed, deterministic in-process vector embeddings without Python dependencies |
| **Object Storage** | Pluggable Local POSIX / AWS S3 Compatible | Content-addressed storage abstraction with SHA-256 deduplication |
| **Enterprise Identity** | SAML 2.0, OIDC, LTI 1.3, SCIM 2.0 | Institutional single sign-on, LMS grade synchronization, directory provisioning |
| **PDF Extraction & Export** | Apache PDFBox 3.0.5 | Native vector PDF generation and bounded text extraction |
| **DOCX Processing** | Apache POI 5.2.5 | Reliable Office Open XML extraction with decompression protection |
| **JSON Serialization** | Google Gson 2.10.1 | Fast, strict JSON binding for configurations, APIs, and snapshots |
| **Frontend Architecture** | Vanilla HTML5, CSS3, JavaScript (ES6) | Zero npm runtime dependencies, fast loading, full keyboard accessibility |
| **Desktop GUI** | JavaFX 21 + BootstrapFX 0.4.0 | Preserved desktop authoring tool for offline writing and citation styling |
| **Test Automation** | JUnit 5 + Node Test Runner + Python | 349 automated test cases spanning unit, API, security, and benchmark suites |

---

## 🚀 Quick Start & Local Setup

### Prerequisites
- **Java 17+** (Eclipse Temurin or OpenJDK recommended)
- **Apache Maven 3.9+**
- **Node.js 20+** (optional, for frontend unit tests)
- **Python 3.10+** (optional, for evaluation benchmark)

### 1. Build the Application
```sh
# Clone repository
git clone https://github.com/SadeeshaJayaweera/originality-desk.git
cd originality-desk

# Build shaded executable JAR
mvn clean package -DskipTests
```

### 2. Launch Originality Desk Web Server
```sh
# Run the standalone web server
java -jar target/writing-assistant-1.0.0-SNAPSHOT.jar --server

# Or run directly via class name:
java -cp target/writing-assistant-1.0.0-SNAPSHOT.jar com.writingassistant.server.OriginalityDeskServer
```

Navigate to: **[http://localhost:8080](http://localhost:8080)**  
Click **Create workspace** to initialize your institution administrator account.

### 3. CLI Command Options

| Command | Description |
|---|---|
| `java -jar target/writing-assistant-1.0.0-SNAPSHOT.jar --server` | Launch standalone Originality Desk HTTP server (default port `8080`). |
| `java -jar target/writing-assistant-1.0.0-SNAPSHOT.jar --smoke-test` | Run headless system diagnostics (database, rewrite engine, citation engine). |
| `java -jar target/writing-assistant-1.0.0-SNAPSHOT.jar --help` | Show command-line usage summary. |
| `java -jar target/writing-assistant-1.0.0-SNAPSHOT.jar` | Launch the offline JavaFX Desktop Writing Assistant GUI. |
| `java -cp target/writing-assistant-1.0.0-SNAPSHOT.jar com.writingassistant.persistence.migration.SqliteToPostgresMigrationCli --source <path> --target <jdbc:postgresql://...> --user <usr> --password <pwd>` | Execute automated SQLite to PostgreSQL database migration. |

---

## 🐳 Docker Deployment

Run Originality Desk with Docker Compose:

```sh
# Copy environment template
cp .env.example .env

# Build and start container in detached mode
docker compose up -d

# Check service health
curl -f http://localhost:8080/health/live
curl -f http://localhost:8080/health/ready
```

For production deployments with PostgreSQL and MinIO/S3:
```sh
docker compose -f docker-compose.prod.yml up -d
```

---

## ⚙️ Environment Variables

Originality Desk is configured via environment variables or a `.env` file in the working directory:

### Core Server & Security
| Variable | Default | Purpose |
|---|---|---|
| `DESK_ENV` | `development` | Environment mode (`development`, `test`, `production`). In production, HTTPS and secure cookies are strictly enforced. |
| `DESK_HOST` | `127.0.0.1` | Network interface address to bind to (`0.0.0.0` in Docker). |
| `DESK_PORT` | `8080` | TCP port for the HTTP server. |
| `DESK_PUBLIC_ORIGIN` | `http://localhost:8080` | Canonical browser origin. Required for Host and CSRF Origin validation. |
| `DESK_DATA_DIR` | `./desk-server-data` | Directory storing SQLite database, WAL files, and document blobs. |
| `DESK_SECURE_COOKIES`| `false` | Set to `true` in production to enforce the `Secure` flag on session cookies. |
| `DESK_RETENTION_DAYS` | `0` | Automatic document retention limit in days (`0` = retain indefinitely). |
| `DESK_JOB_TIMEOUT_SECONDS` | `90` | Bounded execution deadline per background extraction or analysis job. |

### External Academic Reference Search APIs (Optional)
| Variable | Default | Purpose |
|---|---|---|
| `ORIGINALITY_CROSSREF_ENABLED` | `false` | Enable automated Crossref external reference discovery. |
| `ORIGINALITY_CROSSREF_MAILTO` | `integrity@originalitydesk.org` | Polite API contact email for Crossref rate limits. |
| `ORIGINALITY_SEMANTICSCHOLAR_ENABLED` | `false` | Enable Semantic Scholar academic paper lookup. |
| `ORIGINALITY_SEMANTICSCHOLAR_API_KEY` | *(none)* | Optional API key for Semantic Scholar higher rate limits. |
| `ORIGINALITY_CORE_ENABLED` | `false` | Enable CORE open-access full-text repository search. |
| `ORIGINALITY_CORE_API_KEY` | *(none)* | CORE API authentication key. |
| `ORIGINALITY_OPENALEX_ENABLED` | `false` | Enable OpenAlex global research corpus integration. |

### Pluggable Object Storage & Enterprise SSO
| Variable | Default | Purpose |
|---|---|---|
| `DESK_STORAGE_BACKEND` | `LOCAL` | Storage provider (`LOCAL` or `S3`). |
| `DESK_S3_BUCKET` | *(none)* | Target S3 bucket name when `DESK_STORAGE_BACKEND=S3`. |
| `DESK_S3_ENDPOINT` | *(none)* | Custom endpoint for MinIO or AWS S3. |
| `SAML_IDP_METADATA_URL` | *(none)* | Identity Provider metadata URL for SAML 2.0 institutional login. |
| `OIDC_ISSUER_URI` | *(none)* | OpenID Connect / Keycloak issuer URI for institutional SSO. |
| `GEMINI_API_KEY` | *(none)* | Optional Gemini API key for authorial style rewriting in desktop GUI mode. |

---

## 🧪 Test Execution & Verification

Run the comprehensive multi-tier test suites:

```sh
# 1. Run all Java unit, integration, and security tests (349 tests)
mvn clean test

# 2. Run JavaScript frontend and markup validation tests
node scripts/e2e/fixtures.test.cjs
node scripts/e2e/markup.test.cjs
node src/main/resources/web/frontend.test.cjs

# 3. Verify synthetic evaluation benchmark
python3 scripts/evaluation/run.py --verify-only scripts/evaluation/results
```

All 349 automated test cases pass with 0 failures and 0 errors across all modules.

---

## 📡 REST API Reference

The server exposes a clean REST API under `/api/v1/*`. All mutating requests require authentication and strict `Origin` validation.

| Endpoint | Method | Role | Description |
|---|---|---|---|
| `/health/live` | `GET` | Public | Liveness probe returning server process status. |
| `/health/ready` | `GET` | Public | Readiness probe checking database connectivity and job worker health. |
| `/api/v1/auth/register` | `POST` | Public | Register new user and workspace (creates `INSTITUTION_ADMIN`). |
| `/api/v1/auth/login` | `POST` | Public | Authenticate user and issue opaque session cookie. |
| `/api/v1/auth/me` | `GET` | Member | Retrieve current user profile and workspace metadata. |
| `/api/v1/auth/logout` | `POST` | Member | Invalidate current session and clear cookie. |
| `/api/v1/dashboard` | `GET` | Member | Retrieve workspace metrics, recent analyses, and storage stats. |
| `/api/v1/documents` | `GET` | Member | List workspace documents with status and pagination. |
| `/api/v1/documents` | `POST` | Member | Stream raw file upload (PDF, DOCX, TXT; 10MB max). |
| `/api/v1/documents/:id` | `GET` | Member | Get document status, metadata, and extracted text. |
| `/api/v1/documents/:id` | `DELETE`| Member | Securely delete document and all dependent analyses. |
| `/api/v1/references/external/search` | `GET` | Member | Federated external academic search across Crossref, Semantic Scholar, CORE. |
| `/api/v1/references/external/doi` | `GET` | Member | Resolve external reference metadata and citations by DOI. |
| `/api/v1/references/external/import` | `POST` | Member | Import external scholarly source into workspace references. |
| `/api/v1/analyses` | `GET` | Member | List analyses with status and pagination. |
| `/api/v1/analyses` | `POST` | Member | Submit new similarity analysis with profile & source IDs. |
| `/api/v1/analyses/:id` | `GET` | Member | Retrieve analysis result snapshot with passage matches. |
| `/api/v1/analyses/:id/cancel` | `POST` | Member | Cancel a queued or running analysis. |
| `/api/v1/analyses/:id/reviews`| `GET/POST` | Reviewer | Retrieve or record human reviewer decisions and notes. |
| `/api/v1/analyses/:id/review-summary` | `GET` | Reviewer | Retrieve aggregate review metrics and resolution status. |
| `/api/v1/analyses/:id/review-resolve` | `POST` | Admin | Resolve reviewer annotation conflicts. |
| `/api/v1/reports/:id/json` | `GET` | Member | Download complete evidence report as JSON. |
| `/api/v1/reports/:id/html` | `GET` | Member | Download standalone interactive HTML report. |
| `/api/v1/reports/:id/csv` | `GET` | Member | Download passage evidence rows as CSV. |
| `/api/v1/reports/:id/pdf` | `GET` | Member | Download publication-grade vector PDF report. |
| `/api/v1/admin/health` | `GET` | Admin | Inspect operational metrics, memory, and queue depth. |
| `/api/v1/admin/jobs` | `GET` | Admin | Paginated list of background extraction and analysis jobs. |
| `/api/v1/admin/audit-logs` | `GET` | Admin | Paginated audit trail of workspace security actions. |
| `/scim/v2/Users` | `GET/POST` | Enterprise | SCIM 2.0 user directory provisioning. |
| `/scim/v2/Groups` | `GET/POST` | Enterprise | SCIM 2.0 group and membership synchronization. |

*Full OpenAPI 3.0 specification is available at [docs/openapi.yaml](docs/openapi.yaml).*

---

## 🔒 Security Architecture

- **Tenant Isolation**: Every database operation is constrained by the server-derived `workspace_id`. Cross-tenant queries return 404.
- **CSRF Defense**: Zero CORS support; all state-changing API operations strictly enforce `Origin == DESK_PUBLIC_ORIGIN` and `Sec-Fetch-Site: same-origin`.
- **Password Security**: PBKDF2-HMAC-SHA256 with 210,000 iterations and individual 16-byte random salts.
- **Session Protection**: Host-only, `HttpOnly`, `SameSite=Strict` cookies containing random 256-bit tokens, stored only as SHA-256 hashes.
- **Input Validation**: Magic byte file validation, ZIP decompression bomb protection (`SafeZip`), 10 MiB upload cap, strict UTF-8 JSON parsing.
- **Secure Data Erasure**: SQLite configured with `PRAGMA secure_delete=ON`, zero-filling deleted table pages; blobs wiped immediately on deletion.
- **Institutional Authentication**: Cryptographic XML signature validation for SAML 2.0 assertions and JWKS signature verification for OpenID Connect tokens.

---

## ⚠️ Known Limitations & Disclaimers

1. **Source Scope**: Originality Desk compares submissions **strictly against authorized reference documents uploaded to your workspace** and explicit federated external scholarly queries. It does not crawl the uncontrolled open web or paywalled publishers without configured institutional credentials.
2. **Text Similarity $\ne$ Academic Misconduct**: High similarity scores do not prove plagiarism; low scores do not prove originality. Matches require human review to evaluate disciplinary context, quotations, and field conventions.
3. **AI Writing Detection**: In adherence to scientific rigor, the platform refuses to manufacture unverified AI-authorship percentages. AI analysis status is honestly reported as `UNAVAILABLE` without a certified local classifier, mitigating false-positive harms against non-native English writers.
4. **Hardware Sizing**: In SQLite mode, the server uses single-process file locking designed for departmental workloads (thousands of documents). For large enterprise campuses, configure PostgreSQL + pgvector and S3-compatible object storage.

---

## 🗺️ Implementation Programme & Milestones

All 40 Pull Requests across 8 implementation phases have been fully delivered and verified:

- [x] **Phase 1: Web Application Foundation** — Standalone headless Java web server with embedded JDK `HttpServer`, REST API, and zero external framework dependencies.
- [x] **Phase 2: Multi-Tenant Workspace & Security** — Workspace isolation, PBKDF2 authentication, CSRF origin enforcement, and role-based access control.
- [x] **Phase 3: Bounded Ingestion & Document Processing** — Bounded PDFBox and POI document extraction with decompression bomb protection and offset-preserving normalisation.
- [x] **Phase 4: Multi-Strategy Similarity & Reporting** — Inverted N-Gram exact matching, word shingling, interval union coverage calculation, citation detection, and multi-format reports (PDF, HTML, JSON, CSV).
- [x] **Phase 5: Federated External Academic Sources** — Crossref, Semantic Scholar, and CORE integration with parallel dispatch, deduplication, ranking, and scholarly citation formatting.
- [x] **Phase 6: Semantic Embeddings & Hybrid Similarity** — In-process ONNX Runtime Java with `all-MiniLM-L6-v2`, sentence-aware chunking, pgvector support, and decomposed hybrid scoring.
- [x] **Phase 7: AI-Writing Feasibility & Stylometry Evaluation** — Scientific feasibility study, ESL bias evaluation, ethical constraints, perplexity/burstiness signals, and advanced human reviewer decision state machine.
- [x] **Phase 8: Enterprise Integration & Production Readiness** — Flyway database migrations, SQLite-to-PostgreSQL migration CLI, durable background job leasing, pluggable S3 object storage, Keycloak OIDC SSO, SAML 2.0, LTI 1.3 Advantage LMS integration, and SCIM 2.0 provisioning.

---

## 📦 Project Resources & Specifications

- 📡 **[OpenAPI 3.0 Contract](docs/openapi.yaml)**: Comprehensive OpenAPI specification for all server routes.
- 📸 **[Application Screenshots](docs/screenshots/)**: Interface captures and verified render digests.
- 🧪 **[Testing Guide](TESTING.md)**: Manual testing scenarios, smoke test instructions, and unit test details.
- ⚙️ **[Configuration Template](.env.example)**: Full environment configuration template with default parameters.
- 🐳 **[Docker Compose Setup](docker-compose.yml)** / **[Production Stack](docker-compose.prod.yml)**: Container definitions for development and production environments.

---

## 📄 License

This project is licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.
