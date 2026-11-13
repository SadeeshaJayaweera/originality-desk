# Security review

## Initial findings (source audit)

- Citation library fallback can disclose unrelated source metadata in DOCX exports (MainController).
- SQLite FK enforcement is connection-scoped but was configured only during schema initialization. Rewrite history lacks deletion integrity.
- Credentials are persisted in configuration; Gemini keys appear in URLs; upstream errors may contain sensitive details.
- DOCX input has no application file/expanded-text limits and parses on the desktop UI thread.
- Desktop DAOs have no tenant identity: they must not be exposed directly to web requests.
- Schema/migration/cache errors can be suppressed, making failures appear successful.

## New service threat model

Assets: confidential manuscripts, extracted text, source evidence, passwords/sessions and audit metadata. Trust boundaries: browser ↔ server, authenticated workspace ↔ another workspace, parser ↔ hostile document, server ↔ local database/files, and optional desktop AI ↔ external provider.

Controls must include password KDF, random opaque expiring sessions stored hashed, same-origin mutation protection, request rate/size limits, prepared SQL, resource ownership predicates, safe filenames, magic/content validation, ZIP expansion checks, bounded extraction, no external URL fetching, escaped rendering, CSP, access-controlled exports and secret-free logs. New analysis must not transmit documents to hosted AI.

## Deployment limitations

TLS must terminate at a trusted reverse proxy. Protect volumes/backups and use filesystem/disk encryption where required. SQLite is single-host; do not place database files on a shared network filesystem. In-process parser limits are not a malware sandbox. Malware scanning, external identity/mail providers, MFA, independent penetration testing, advisory scanning and operational recovery drills remain release requirements where not specifically verified in the completion report.

## Verification

See IMPLEMENTATION_COMPLETION_REPORT.md for the actual changes/tests and unresolved findings. This review is not a certification or a claim that the application is production-secure.
