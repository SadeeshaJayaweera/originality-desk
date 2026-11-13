# Architecture audit

Audit date: 2026-11-13. This describes the repository **before this implementation**, based on source inspection, not inferred product capabilities.

## Inventory

Root files: `README.md`, `TESTING.md`, `pom.xml`, `.gitignore`. Source is a single Maven Java 17 application under `com.writingassistant`. JavaFX 21/FXML and BootstrapFX implement a desktop editor. SQLite JDBC persists local metadata. Apache POI imports/exports DOCX; Gson handles configuration and Gemini payloads. SLF4J delegates to Java logging. JUnit 5 tests cover services and persistence. No web frontend, REST controllers, authentication, tenant model, queue, Docker configuration, workflow, environment example, or managed migrations existed.

## Implemented versus missing

- Implemented in source: Gemini rewriting/paraphrasing, APA/MLA/Chicago formatting, local citation library, recent-document metadata, accepted rewrite history, DOCX import/export, pairwise n-gram/LCS/Levenshtein analysis and candidate spans.
- Partial: document persistence stores external paths, not editor bodies; schema evolution uses error-suppressing ALTER statements; analysis is transient and lacks source identities and engine versions.
- Missing: users/workspaces/roles, PDF/TXT pipeline, durable analyses, indexed corpus, reference extraction, reproducible reports, semantic embeddings, AI-authorship detection, browser UI, background workers, APIs, deployment automation and evaluation.
- Existing test documentation claims 57 passing tests. That claim was not accepted as independently verified. The so-called end-to-end test invokes services with a mocked Gemini client, not a GUI/browser.

## Confirmed defects

1. MainController falls back to the entire library when a document has no citations, including during DOCX export.
2. DatabaseManager enables SQLite foreign keys only on its initialization connection. Rewrite history has no document FK; deletion can retain sensitive derived data.
3. Similarity tokenization runs on trimmed input, so spans do not preserve leading-whitespace offsets.
4. Whole-input LCS and character edit distance are quadratic in input lengths. They cannot be used unbounded for long documents/corpora.
5. DOCX parsing runs on the JavaFX thread with no application extraction limits.
6. Config persists credentials in plain JSON; Gemini uses key-bearing URLs; errors can log upstream content. Optional rewrite-cache persistence targets a nonexistent column.

## Reuse and migration

Retain JavaFX and existing authoring functionality. Add a separately launched headless web application in the same Maven build. Keep its tenant-aware schema separate from legacy desktop tables. Reuse POI, Gson and SQLite; add a maintained PDF parser for PDF extraction/report export. Do not expose desktop DAOs as multi-tenant APIs. SQLite supports a bounded single-host deployment, not arbitrary horizontal scaling. A later migration to PostgreSQL/object storage and isolated parser workers requires explicit operational validation.

## Validation scope

Static audit findings are not proof of runtime exploitability. Dependency vulnerabilities require a current advisory scan. Build, test and workflow results are recorded in IMPLEMENTATION_COMPLETION_REPORT.md, not fabricated here.
