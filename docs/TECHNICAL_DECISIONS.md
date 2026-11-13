# Technical decisions

## ADR 001 — Controlled Java migration

Retain Java 17, Maven and the existing desktop entry point. Add a separately launched browser application rather than deleting JavaFX. Existing dependencies and pure Java services remain reusable. A small JDK HTTP adapter avoids rewriting the project in a second language. This is a bounded deployment foundation, not a claim of enterprise framework completeness.

## ADR 002 — Separate tenancy boundary

Web data uses a separate versioned schema and database file. Each authenticated resource operation uses the server-derived workspace identity. Caller-supplied IDs never establish ownership. Desktop data is not automatically imported or exposed.

## ADR 003 — Honest analysis

Textual similarity is not a misconduct determination. Exact and lexical near-duplicate evidence must point to stored authorized documents. Use interval unions, not added percentages. Citation-pattern observations do not validate references. Semantic similarity and AI-authorship detection remain unavailable until defensible providers and evaluations exist. No external model calls occur in the new analysis workflow.

## ADR 004 — Bounded local processing

Use a database-backed job queue and a bounded worker on a single host. Persist status/results and recover interrupted work. Enforce upload, expansion, extraction and comparison limits. SQLite is appropriate for a modest single-instance workspace service, not multi-host horizontal scaling. Deploy parser workloads in a resource-limited container; thread timeouts cannot safely terminate all native/parser work.

## ADR 005 — Stored evidence and exports

Store analysis configuration/version, source snapshots, spans, citations and coverage summaries as a report snapshot. Exports derive from that stored snapshot, not a later re-analysis. Reviewer annotations must be separate from algorithmic evidence. Sensitive files/results require authenticated, workspace-scoped access and deletion semantics.
