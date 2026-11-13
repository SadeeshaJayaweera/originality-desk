# Implementation roadmap

Priority is correctness, privacy and a complete evidence-backed workflow, not a count of screens.

1. Preserve the desktop app; fix citation disclosure, deletion integrity, offset handling, resource limits and credential/error paths with regression tests.
2. Introduce a separate headless Java server with versioned API, persisted password-authenticated sessions, workspace-scoped resources, audit records and bounded request handling.
3. Add validated PDF/DOCX/TXT ingestion, bounded extraction, offset-preserving normalization, citation observations and fingerprints.
4. Persist background jobs and analysis snapshots; compare only actual authorized reference documents; calculate union-based overlap and store passage evidence.
5. Connect responsive browser UI to live APIs: dashboard, uploads, reference library, comparison, history, evidence reports and exports. No simulated processing or hardcoded metrics.
6. Add security/integration tests, reproducible synthetic evaluation, Docker/CI and operational documentation.

## Release gates

- No cross-workspace data retrieval, modification or source disclosure.
- No confidential documents sent to hosted AI in the analysis path.
- No AI-authorship claims, semantic scores or internet matches without configured and validated implementations.
- Automated tests and production packaging pass; unrun checks remain explicitly unverified.
- Reports identify methodology, versions, source scope and uncertainty.

## Subsequent production work

Email verification/reset/MFA with a configured mail/identity provider; managed institution membership; production malware scanning and isolated parsers; OCR; local embedding provider and scientific evaluation; PostgreSQL/object storage and horizontally coordinated workers; comprehensive browser/accessibility/performance testing; independent security review and staging deployment. These must not be advertised as completed merely because interfaces or configuration exist.
