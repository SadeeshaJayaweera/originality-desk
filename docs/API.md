# Originality Desk HTTP API v1

This is the contract for the standalone `com.writingassistant.server.OriginalityDeskServer`, not the desktop application's database or entry point. The machine-readable contract is [openapi.yaml](openapi.yaml). It describes implemented routes only; the server does **not** serve an OpenAPI endpoint.

The service compares one extracted document with a bounded local source corpus. It provides textual evidence, not a plagiarism or misconduct verdict. There is no web search, hosted AI request, semantic similarity, AI-authorship scoring, OCR, citation validation, collaboration, invitation, or user/role-management API. Registration creates a separate workspace for its registrant. There is **no multi-document batch endpoint**: automate individual uploads and analyses, respect limits, and poll their persisted statuses. Supplying several `sourceIds` means one analysis against several sources, not a batch of submissions.

## Address, authentication, and request security

The API prefix is `/api/v1`. Default public origin: `http://localhost:8080`. The listener defaults to `127.0.0.1:8080`; the bind address is not an alternative public origin. In particular, default requests to `http://127.0.0.1:8080` fail the Host check. Configure `DESK_PUBLIC_ORIGIN` for the actual browser-visible origin, without a trailing slash or path.

Every request, including static assets and public health probes, must have a `Host` equal to the configured origin's authority (case-insensitive comparison, including any explicit port). No forwarded header substitutes for Host, Origin, the connection's scheme, or peer IP.

| Setting | Default | Meaning |
| --- | --- | --- |
| `DESK_HOST` | `127.0.0.1` | Listener bind address. |
| `DESK_PORT` | `8080` | Listener port. Embedded tests may use port zero. |
| `DESK_PUBLIC_ORIGIN` | `http://localhost:8080` | Exact accepted Origin and canonical Host authority. |
| `DESK_DATA_DIR` | `./desk-server-data` | Separate `server.sqlite3`, WAL/SHM files, lock, and random-named `documents/` blobs. |
| `DESK_SECURE_COOKIES` | `false` | Literal `true`/`false`; HTTPS public origins require `true`. |

The JDK listener speaks HTTP, not HTTPS. For remote deployment, terminate TLS at a trusted ingress, preserve the canonical Host and browser Origin, enable secure cookies, and prevent bypassing that ingress. Bind-address, port, and origin settings are independent.

### Sessions and roles

Registration and login return an identity object and set a host-only opaque cookie:

```http
Set-Cookie: desk_session=<random-token>; Path=/; HttpOnly; SameSite=Strict; Max-Age=43200
```

`Secure` is appended when configured. There is no bearer-token alternative. Only SHA-256 hashes of random 256-bit session tokens are stored. Sessions have an absolute 12-hour expiry, not a sliding expiry. A login/registration retains at most ten sessions per user; logout revokes only the supplied current session. Passwords use independent random salts and PBKDF2-HMAC-SHA256 with 210,000 iterations; verification uses a constant-time derived-key comparison.

All API routes except register/login require this cookie. Public health and static asset GETs do not. The session determines the workspace; there is no workspace selection parameter. Unknown and other-workspace document, source, analysis, and report IDs return `404` after applicable authentication/role checks. All workspace members can use document, analysis, dashboard, and report operations. Both review GET and POST require `REVIEWER` or `INSTITUTION_ADMIN`. All `/admin/*` routes require `INSTITUTION_ADMIN` and expose that workspace's records, not other tenants' records. Registration always creates `INSTITUTION_ADMIN`; caller-supplied roles cannot change it.

### Same-origin / CSRF rules

* Every non-GET `/api/v1/*` request, including register, login, raw upload, logout, cancel, and DELETE, must send `Origin` exactly equal to `DESK_PUBLIC_ORIGIN`.
* API GETs may omit Origin. If present, it must match exactly.
* If an API request includes `Sec-Fetch-Site`, only `same-origin` and `none` are accepted; `same-site` is not sufficient.
* There is no CORS, preflight/OPTIONS API, separate CSRF token, or Referer fallback. CLI clients must explicitly send Origin for mutations. Browser clients should use same-origin credentials; JavaScript cannot read the HttpOnly cookie.
* JSON POSTs require `Content-Type: application/json` (an optional parameter such as `charset=utf-8` is accepted), including empty-body logout/cancel. Send `{}` for those two operations. DELETE needs no body or Content-Type. Upload uses a raw file body, never multipart or base64-in-JSON.
* JSON is strict UTF-8, one top-level object, at most 16,384 bytes, nesting depth at most ten. Duplicate JSON keys, duplicate parsed query parameters, and malformed JSON are rejected. Unknown JSON fields generally are ignored; they grant no capabilities. JSON string limits are Java UTF-16 lengths.
* Non-identity `Content-Encoding`, including gzip, is rejected. The HTTP handler reads at most its body limit plus one sentinel byte. JDK/parser errors, overload, disconnects, or a sender continuing a substantially oversized upload can close a connection without a complete JSON response.

Application responses use `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, a restrictive CSP, same-origin resource policy, and no-referrer policy. Reports additionally use a sandbox CSP and attachment disposition. These headers do not replace TLS or disk protection.

## Routes

`session` means any authenticated workspace user. `reviewer` includes admin. Only these methods are implemented; there are no PATCH/PUT, bulk, retry, progress-stream, original-file-download, or collection-delete routes.

| Method | Path | Access | Success |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/register` | public + CSRF | 201 identity + cookie |
| POST | `/api/v1/auth/login` | public + CSRF | 200 identity + cookie |
| GET | `/api/v1/auth/me` | session | 200 identity |
| POST | `/api/v1/auth/logout` | session + CSRF | 204, cleared cookie |
| GET | `/api/v1/dashboard` | session | 200 dashboard |
| POST | `/api/v1/documents` | session + CSRF | 201 extracting document |
| GET | `/api/v1/documents` | session | 200 document page |
| GET | `/api/v1/documents/{documentId}` | session | 200 document |
| DELETE | `/api/v1/documents/{documentId}` | session + CSRF | 204 |
| POST | `/api/v1/analyses` | session + CSRF | 202 analysis, including replay |
| GET | `/api/v1/analyses` | session | 200 analysis metadata page |
| GET | `/api/v1/analyses/{analysisId}` | session | 200 analysis, optional result |
| GET | `/api/v1/analyses/{analysisId}/status` | session | 200 analysis metadata |
| POST | `/api/v1/analyses/{analysisId}/cancel` | session + CSRF | 200 analysis metadata |
| GET | `/api/v1/analyses/{analysisId}/reviews` | reviewer | 200 review page |
| POST | `/api/v1/analyses/{analysisId}/reviews` | reviewer + CSRF | 201 appended review |
| GET | `/api/v1/reports/{analysisId}` | session | 200 JSON report snapshot |
| GET | `/api/v1/reports/{analysisId}/json` | session | 200 same JSON snapshot |
| GET | `/api/v1/reports/{analysisId}/html` | session | 200 escaped HTML |
| GET | `/api/v1/reports/{analysisId}/csv` | session | 200 match-table CSV |
| GET | `/api/v1/reports/{analysisId}/pdf` | session | 200 PDFBox-generated PDF |
| GET | `/api/v1/admin/health` | admin | 200 workspace job counts / worker liveness |
| GET | `/api/v1/admin/jobs` | admin | 200 job page |
| GET | `/api/v1/admin/audit-logs` | admin | 200 audit page |
| GET | `/health/live` | public | 200 `{ "status": "UP" }` |
| GET | `/health/ready` | public | 200 UP or 503 DOWN |
| GET | `/`, `/index.html`, `/app.js`, `/styles.css` | public | 200 classpath asset, if packaged |

### Pagination and ordering

Documents, analyses, reviews, jobs, and audit logs accept `page` (default 1, range 1–1,000,000) and `size` (default 20, range 1–100). They return:

```json
{"items": [], "total": 0, "page": 1, "size": 20}
```

`total` is the scoped count, not the current page length. An out-of-range but valid page is empty. Pagination is offset-based, not a cursor or a stable snapshot across requests. Documents and analyses sort by `createdAt DESC, id DESC`; jobs do the same; reviews sort by `createdAt ASC, id ASC`; audit logs sort by numeric `id DESC`. Document lists alone support `kind=REFERENCE|SUBMISSION`; omit it for both. There is no search, analysis-status filter, or workspace-ID filter.

## Authentication examples

Examples use illustrative IDs/timestamps, not credentials or claimed live analysis results. Use the exact public origin and replace example IDs with returned IDs. These commands deliberately do not automate an unsupported batch API.

```sh
curl -i -c cookies.txt http://localhost:8080/api/v1/auth/register \
  -H 'Origin: http://localhost:8080' -H 'Content-Type: application/json' \
  --data '{"name":"Alex Reviewer","email":"alex@example.test","password":"a long example password","workspaceName":"Alex workspace"}'
```

All four fields are required. `name`/`workspaceName` must be nonblank, at most 120 UTF-16 code units, and are stripped of surrounding whitespace. Email is stripped, lowercased, globally unique, at most 254 code units, and must match the service's basic email syntax check; it is not verified by email. Password is not trimmed; registration requires 12–1,024 code units, nonblank, without NUL. The result for registration, login, and me is:

```json
{
  "user": {"id":"10000000-0000-4000-8000-000000000001","name":"Alex Reviewer","email":"alex@example.test","role":"INSTITUTION_ADMIN"},
  "workspace": {"id":"20000000-0000-4000-8000-000000000001","name":"Alex workspace"}
}
```

```sh
curl -i -c cookies.txt http://localhost:8080/api/v1/auth/login \
  -H 'Origin: http://localhost:8080' -H 'Content-Type: application/json' \
  --data '{"email":"alex@example.test","password":"a long example password"}'
curl -b cookies.txt http://localhost:8080/api/v1/auth/me
curl -i -b cookies.txt -c cookies.txt http://localhost:8080/api/v1/auth/logout \
  -H 'Origin: http://localhost:8080' -H 'Content-Type: application/json' --data '{}'
```

Login requires `email` and a nonblank password of at most 1,024 code units. Wrong/unknown credentials return the same `401 INVALID_CREDENTIALS`. Duplicate registration returns `409 CONFLICT`. Logout has no response body and sets `Max-Age=0`; an expired or already-revoked session returns `401`, not another successful logout. There is no password reset, email verification, account deletion, or logout-all-sessions endpoint.

## Documents

Upload query parameters: required URL-encoded `filename` (nonblank, at most 200 UTF-16 code units, no slash, backslash, control characters, `.` or `..` as the entire filename) and optional `kind` (defaults to `SUBMISSION`). Extension comparison is case-insensitive. `title` is the supplied filename; the physical blob path is random and never derived from it.

```sh
curl -i -b cookies.txt 'http://localhost:8080/api/v1/documents?filename=reference%20one.txt&kind=REFERENCE' \
  -H 'Origin: http://localhost:8080' -H 'Content-Type: application/octet-stream' \
  --data-binary @reference.txt
curl -b cookies.txt 'http://localhost:8080/api/v1/documents?kind=REFERENCE&page=1&size=20'
curl -b cookies.txt http://localhost:8080/api/v1/documents/30000000-0000-4000-8000-000000000001
```

Accepted bodies are nonempty UTF-8 `.txt`, `.pdf` with `%PDF-` signature, and `.docx` with matching ZIP/package structure. Use `application/octet-stream` for any accepted format (also the effective default if Content-Type is omitted), or respectively `text/plain`, `application/pdf`, or `application/vnd.openxmlformats-officedocument.wordprocessingml.document`. MIME, extension, and content checks must agree. Raw upload limit: **10,485,760 bytes (10 MiB)**. There is no multipart boundary or filename form field.

A successful upload immediately returns this complete document shape with `EXTRACTING`, zero extraction counts, and `error: null`:

```json
{
  "id":"30000000-0000-4000-8000-000000000001",
  "title":"reference one.txt", "filename":"reference one.txt", "kind":"REFERENCE",
  "status":"EXTRACTING", "error":null, "wordCount":0, "pageCount":0,
  "bytes":523, "createdAt":"2026-11-13T12:00:00Z"
}
```

Poll GET of that ID: extraction persists `READY` with counts, or `FAILED` with a safe error string. Header/signature success is not a guarantee of successful parsing. Empty extracted text, encrypted/image-only/malformed PDFs, unsafe DOCX archives, and extraction budget violations can fail asynchronously. Extraction is limited to 200,000 UTF-16 code units. `wordCount` here is a whitespace-split count, not necessarily the evidence engine's normalized word count. `pageCount` is actual PDF pages, 1 for TXT, and 0 for DOCX (no rendered pagination). Internal paths, SHA-256 hashes, and extracted text are not document metadata fields.

Equal original bytes in the **same workspace and kind** return `409 DUPLICATE_DOCUMENT`; they do not return an existing document as a new upload. Different kinds or different workspaces may store identical bytes. No document update/rename endpoint exists.

### Destructive deletion

```sh
curl -i -X DELETE -b cookies.txt \
  -H 'Origin: http://localhost:8080' \
  http://localhost:8080/api/v1/documents/30000000-0000-4000-8000-000000000001
```

Deletion removes the original blob, extracted text, extraction job, and **all analyses using the document either as submission or source**, including their snapshots, source links, reviews, and jobs. Entire dependent reports are deleted rather than retaining source passages after a source is removed. A running computation cannot republish a deleted job's result. A text-free `DOCUMENT_DELETED` audit action remains. Deleted IDs subsequently return `404`; a repeated DELETE also returns `404`.

This is application-level deletion, not certified physical erasure. SQLite secure-delete and a WAL truncation checkpoint are used; filesystem snapshots, backups, SSD remanence, previously downloaded exports, and transient in-memory work are outside its guarantee. Filesystem unlink and SQLite commit are not a single atomic transaction: I/O/commit/checkpoint failure or a crash can leave a failed request requiring operator inspection. A checkpoint error can occur after logical deletion has committed. Startup removes unreferenced blob files.

## Analyses, jobs, and idempotency

Create requires `documentId` (nonblank, at most 100 code units) and exact uppercase `profile`. Optional `sourceIds` must be an array of at most 50 nonblank string IDs, each at most 100 code units. The submission and **every source** must belong to the session's workspace and be `READY`. Explicit sources can be either document kind; the submission may not be its own source. Duplicate explicit IDs are deduplicated. An explicit empty array is accepted and compares against no sources; `null` is not omission.

When `sourceIds` is **omitted**, the server uses that workspace's currently ready `REFERENCE` documents only, excluding the submission itself. More than 50 eligible references causes `400`; no silent truncation occurs. The source corpus is limited to 1,000,000 UTF-16 code units by the engine, with a separate 200,000-unit submission limit. The preflight SQL length count uses Unicode code points, so some supplementary-character corpora can pass admission but subsequently fail the engine's UTF-16 budget.

| Profile | Actual behavior |
| --- | --- |
| `QUICK` | Exact normalized 12-word shingles; no lexical near pass. |
| `STANDARD` | Exact 8-word shingles plus lexical near windows. |
| `REFERENCE_AWARE` | Exact 6-word shingles plus lexical near windows; heuristically excludes trailing reference lists in submission and sources. |

```sh
curl -i -b cookies.txt http://localhost:8080/api/v1/analyses \
  -H 'Origin: http://localhost:8080' -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: paper-one-standard-v1' \
  --data '{"documentId":"30000000-0000-4000-8000-000000000002","sourceIds":["30000000-0000-4000-8000-000000000001"],"profile":"STANDARD"}'
```

A newly created analysis returns `202`:

```json
{
  "id":"40000000-0000-4000-8000-000000000001",
  "documentId":"30000000-0000-4000-8000-000000000002", "title":"submission.txt",
  "status":"QUEUED", "profile":"STANDARD", "createdAt":"2026-11-13T12:01:00Z",
  "completedAt":null, "error":null, "sourceCount":1
}
```

Optional `Idempotency-Key`: 1–128 printable ASCII characters without spaces (`!` through `~`). It is scoped to the workspace. The same key and canonical request return the same analysis with **202 even if already terminal**; a completed replay may include `result`. A conflicting request returns `409 CONFLICT`. Explicit source ordering/duplicates do not change request identity. Omitted sources and an explicit list are different requests. Replaying an omitted-source request does not reselect newly uploaded references. Idempotency is tied to the analysis row: deleting a document can remove it and free its key. There is no permanent idempotency ledger or deduplication without the header. A replay still consumes request/analysis rate-limit allowance.

```sh
curl -b cookies.txt 'http://localhost:8080/api/v1/analyses?page=1&size=20'
curl -b cookies.txt http://localhost:8080/api/v1/analyses/40000000-0000-4000-8000-000000000001/status
curl -b cookies.txt http://localhost:8080/api/v1/analyses/40000000-0000-4000-8000-000000000001
curl -b cookies.txt http://localhost:8080/api/v1/analyses/40000000-0000-4000-8000-000000000001/cancel \
  -H 'Origin: http://localhost:8080' -H 'Content-Type: application/json' --data '{}'
```

Analysis metadata always has the fields shown above. Lists, `/status`, and cancel omit `result`, including for a completed analysis. GET by ID includes nested engine `result` only when a stored result exists. Statuses are `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`; retries can move RUNNING back to QUEUED. `completedAt` is terminal time, including failure/cancellation, not just successful completion. A queued retry may already have an error string. There is no progress percentage, estimated completion, callback, or retry endpoint.

Cancel changes QUEUED/RUNNING to CANCELLED and prevents later publication; it returns 200 metadata. Cancelling an already terminal analysis is a 200 no-op. Poll a terminal status rather than assuming cancellation won a race with completion. Deletion and cancellation are serialized with result publication. Interrupted running work is recovered on restart, with at most **three total attempts**; eligible transient failures wait 1 then 2 seconds before retries. Recovery of interrupted attempts is immediate. Invalid input/output budgets and timeouts fail without automatic retry. The standalone environment configuration uses a 90-second computation timeout. It is not an upload timeout or end-to-end queue deadline.

## Reports and evidence shapes

A report is available only for a completed stored result (`409 CONFLICT` otherwise; `404` for missing/other-tenant/deleted analysis). GET `/reports/{analysisId}` and `/reports/{analysisId}/json` return the same immutable stored object. Exporting never reruns extraction or analysis. Reviewer notes are separate and do not mutate the snapshot.

Report fields are `id`, `analysisId` (same analysis ID), `documentId`, `title`, `createdAt`, `completedAt`, `profile`, `sourceCount`, `status` (COMPLETED), `documentText`, and nested `result`. `documentText` is the original **extracted submitted string**, not the original file's binary representation. It preserves offsets for frontend highlighting. It is not returned by document metadata endpoints.

Nested `result` has these fields:

| Field | Type / meaning |
| --- | --- |
| `engineVersion` | String, currently `local-evidence-1.0`. |
| `profile` | The selected profile. |
| `wordCount` | Normalized submitted word count. |
| `exactOverlap`, `nearOverlap`, `totalOverlap` | Fractions in `[0,1]`, **not percentages** or probabilities of misconduct. Exact is union coverage; near is additional lexical-window coverage beyond exact; total is their union. |
| `matches` | Array of evidence objects below, capped at 200. |
| `citations` | Array of heuristic observations below, capped at 300. |
| `limitations` | Array of methodological/budget explanations; retain and display them, including truncation notices. |
| `semanticStatus`, `aiWritingStatus` | Both currently `NOT_IMPLEMENTED`; not analysis scores. |

Every match has `sourceId`, `sourceTitle`, `sourceType` (REFERENCE or SUBMISSION), `submittedPassage`, `sourcePassage`, `method` (EXACT or LEXICAL_JACCARD), numeric `value` in `[0,1]`, `start`, `end`, `sourceStart`, `sourceEnd`, `page`, and `citationObservation`. Offsets are zero-based, half-open UTF-16 indexes into the corresponding **original extracted string**, never normalized text offsets. `page` is 1-based from form-feed separators; it is not independently verified rendered pagination. A near window does not mean every word in it was copied. Passage and retrieval caps can omit evidence.

Each citation has `text`, `kind` (a heuristic observation label), `start`, `end`, and boolean `uncertain`. It does not establish bibliographic validity, attribution, or exemption from matching. Zero returned matches is not a guarantee of originality.

```sh
curl -b cookies.txt http://localhost:8080/api/v1/reports/40000000-0000-4000-8000-000000000001
curl -b cookies.txt -o report.json http://localhost:8080/api/v1/reports/40000000-0000-4000-8000-000000000001/json
curl -b cookies.txt -o report.html http://localhost:8080/api/v1/reports/40000000-0000-4000-8000-000000000001/html
curl -b cookies.txt -o report.csv http://localhost:8080/api/v1/reports/40000000-0000-4000-8000-000000000001/csv
curl -b cookies.txt -o report.pdf http://localhost:8080/api/v1/reports/40000000-0000-4000-8000-000000000001/pdf
```

Exports use `Content-Disposition: attachment; filename="report.<format>"` and appropriate JSON, HTML, CSV, or PDF MIME types. HTML escapes user/source text. CSV is a match table, not the full snapshot; headers are `sourceId,sourceTitle,sourceType,method,value,submittedPassage,sourcePassage,start,end,citationObservation`. Cells are quoted, embedded quotes doubled, and potentially executable spreadsheet cells prefixed with an apostrophe. Keep JSON for unmodified evidence. PDF is a real PDFBox-generated report, limited to 20,000 prepared lines; unsupported standard-font glyphs become `?`. JSON/HTML preserve Unicode. PDF layout is basic, not a typeset reproduction of the uploaded document. Exports do not include subsequently appended review notes.

## Reviews

POST `/analyses/{analysisId}/reviews` appends a new review; it does not overwrite earlier decisions. Both GET and POST require reviewer/admin role. All three request fields are required:

```json
{"matchIndex":0,"decision":"NEEDS_REVIEW","note":"Check the attribution in context."}
```

`matchIndex` is a zero-based integer into the stored `result.matches` array and must exist. `decision` accepts 1–32 characters matching `[A-Z_]+`; it is not a server enum. The frontend's conventional values include `NEEDS_REVIEW`, `ACCEPTED`, `FLAGGED`, `DISMISSED`. `note` can be empty, at most 4,000 UTF-16 code units, without NUL. No raw HTML rendering is implied by accepting text.

201 response:

```json
{"id":"50000000-0000-4000-8000-000000000001","analysisId":"40000000-0000-4000-8000-000000000001","userId":"10000000-0000-4000-8000-000000000001","matchIndex":0,"decision":"NEEDS_REVIEW","note":"Check the attribution in context.","createdAt":"2026-11-13T12:02:00Z"}
```

GET `/analyses/{analysisId}/reviews?page=1&size=20` returns a review page, oldest first. Clients must page to obtain more than 20 notes. There is no review editing/deletion or individual review GET endpoint. Non-completed analysis review POSTs return `409`; invalid indexes return `400`. Review GET can return an empty page for a non-completed existing analysis.

## Dashboard, admin, and health

GET `/dashboard` returns actual workspace counts and recent records:

```json
{"totalDocuments":0,"completedAnalyses":0,"pendingAnalyses":0,"failedAnalyses":0,"storageBytes":0,"recentDocuments":[],"recentAnalyses":[],"activity":[]}
```

`storageBytes` sums stored original-document byte counts, **not** database/text/report/WAL size or total disk usage. Pending means QUEUED/RUNNING analyses, not extracting documents. Failed excludes CANCELLED. Recent documents/analyses are metadata arrays of at most ten each. Activity has at most twenty `{id, action, resourceId, createdAt}` records, newest first; `id` is numeric. These are retained audit actions, not a complete job-event stream.

Admin GETs:

* `/admin/health` returns `{"status":"UP","workerAlive":true,"queuedJobs":0,"runningJobs":0,"failedJobs":0}`. Status is UP or DEGRADED based on dispatcher liveness. Counts include extraction and analysis jobs in this workspace. A successfully executed health query returns 200 even when DEGRADED.
* `/admin/jobs?page=1&size=20` returns a page of `{id,kind,resourceId,status,attempts,availableAt,createdAt,error}`. Kind is EXTRACT/ANALYZE; status is QUEUED/RUNNING/COMPLETED/FAILED/CANCELLED; attempts counts starts, not retries alone. `availableAt` is epoch **milliseconds**, unlike ISO-8601 `createdAt`. Error is string or null. Resource deletion removes corresponding jobs.
* `/admin/audit-logs?page=1&size=20` returns a page of `{id,userId,action,resourceId,createdAt}`. ID is numeric; resourceId is nullable. Actions currently are REGISTER, LOGIN, LOGOUT, DOCUMENT_UPLOADED, DOCUMENT_DELETED, ANALYSIS_CREATED, ANALYSIS_CANCELLED, REVIEW_CREATED. No document passages or review-note content are copied into these rows. At most 2,000 rows per workspace are retained; this is not an append-only/compliance audit archive.

Public GET `/health/live` returns only `{"status":"UP"}` when handled. Public `/health/ready` returns only `{"status":"UP"}` (200) when the dispatcher is alive and SQLite `SELECT 1` works, otherwise `{"status":"DOWN"}` (503). It does **not** test free disk, storage quotas, parser isolation, queue latency, ingress TLS, or whether an uncooperative timed-out computation has released the only computation thread. Health probes still require the canonical Host and share the peer-IP request limiter; 429/403 are possible. Do not infer production readiness or full worker capacity from UP.

## Errors

Application errors use this shape and no stack trace:

```json
{"error":{"code":"NOT_FOUND","message":"Resource not found"}}
```

| HTTP | Code(s) | Meaning |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | Invalid fields, JSON, pagination, duplicate parameters/headers/cookies, source cap/self-source, malformed supported requests. |
| 401 | `UNAUTHENTICATED`, `INVALID_CREDENTIALS` | Missing/invalid/expired session, or failed login. |
| 403 | `INVALID_HOST`, `CSRF_REJECTED`, `FORBIDDEN` | Canonical Host, same-origin/Fetch Metadata, or role rejection. |
| 404 | `NOT_FOUND` | Missing/other-tenant/deleted resource or unimplemented path. |
| 405 | `METHOD_NOT_ALLOWED` | Recognized handler/asset with unsupported method; preceding auth/security checks can win. |
| 409 | `CONFLICT`, `DUPLICATE_DOCUMENT` | Readiness/report/review/idempotency/registration conflict, or duplicate original bytes. |
| 413 | `BODY_TOO_LARGE`, `STORAGE_LIMIT`, `ANALYSIS_TOO_LARGE`, `REPORT_TOO_LARGE` | Handler body, stored original bytes, source corpus, or PDF-rendering limit. Service also defines `UPLOAD_TOO_LARGE`; oversized HTTP uploads normally hit BODY_TOO_LARGE first. |
| 415 | `JSON_REQUIRED`, `UNSUPPORTED_ENCODING`, `UNSUPPORTED_DOCUMENT` | Wrong POST MIME, compressed body, or unsupported/mismatched upload. |
| 429 | `LIMIT_EXCEEDED` | Rate, key-table, count, queue, session, or PDF concurrency limit. All application 429s include `Retry-After: 60`, which is advisory, not a promise a persistent quota clears in 60 seconds. |
| 500 | `INTERNAL_ERROR` | Safe generic error for an unexpected storage/runtime failure. |
| 503 | `UNAVAILABLE` | Shutdown path; readiness failure instead returns the small DOWN object. |

Security/rate checks run before route work; for example, a missing Origin can return 403 before 401. A request that reaches a limiter consumes its allowance even if later validation fails. Async extraction/analysis failures instead appear in an HTTP-200 resource's `status` and `error`, not as a later error response to the original accepted request. Clients must check HTTP status before treating an export body as a file.

## Enforced limits and operational boundaries

### Rate limits

Fixed one-minute wall-clock windows, per process, reset on restart (not durable or distributed):

| Scope | Allowance/minute |
| --- | ---: |
| All requests per actual socket peer IP, including assets/health | 600 |
| Combined registration/login attempts per peer IP | 15 |
| Authenticated requests per user | 360 |
| Upload POSTs per user | 30 |
| Analysis POSTs per user, including idempotent replays | 30 |
| Report/export requests per user | 60 |

The limiter has at most 4,096 active keys across categories. `X-Forwarded-For`, `Forwarded`, and `X-Real-IP` are ignored, so spoofing them does not change a bucket. Behind one reverse proxy, users share its peer-IP buckets; configure admission/rate controls at the trusted ingress rather than assuming per-client forwarded-IP accounting. No trusted-proxy configuration exists.

### Storage and queue limits

| Resource | Per workspace | Whole server |
| --- | ---: | ---: |
| Documents | 2,000 | 10,000 |
| Original document bytes | 256 MiB | 2 GiB |
| Analyses | 2,000 | 20,000 |
| Pending jobs (QUEUED/RUNNING, extraction + analysis) | 100 | 200 |
| Reviewer notes | 10,000; also 1,000 per analysis | No separate global row-count quota |
| Retained audit rows | 2,000 | Bounded indirectly by workspace count |
| Workspaces | — | 1,000 |
| Sessions | Up to ten per user | 10,000 stored sessions after expiry cleanup |
| Stored report JSON length budget | 64 × 1,024² character units | 1,024 × 1,024² character units |

Report length budgets use SQLite `LENGTH` and the new Java string's length; they are **not exact UTF-8 byte quotas**. An individual newly computed snapshot is checked against 2 MiB UTF-8 before `completedAt` is appended; this is an internal output guard, not an exact final HTTP response length guarantee. SQLite main-file capacity is capped at 524,288 pages (normally 2 GiB at a 4 KiB page size); WAL, blobs, and backups are separate. Filling the database/filesystem can still produce 500 or failed jobs before a logical quota is reached. There is no automatic archival, analysis-delete endpoint, quota-editing API, or retention scheduler beyond bounded audit retention/session cleanup.

There are eight HTTP workers with a 32-request waiting queue and a 32-connection listen backlog, one computation worker, and at most two concurrent PDF renderers. JDK HTTP defaults are set to 64 connections, 64 headers, 16 KiB header size, 20-second request and 30-second response limits, and 30-second idle interval unless JVM properties were already supplied. These are JDK process-global controls, not hard per-operation isolation. Executor/transport overload can drop a connection instead of producing a JSON 429.

Timeout/cancellation stops publication and requests interruption, but Java parser/engine code is not forcibly killed. If it ignores interruption, the single computation slot remains occupied; no unbounded replacement workers are created. A process restart may be necessary. Queue wait time is not bounded by the 90-second computation timer. One process owns the data-directory lock; there is no multi-host/shared-volume orchestration or horizontal worker coordination.

TLS termination, ingress abuse controls, monitoring, filesystem permissions/ACLs, encrypted disks, private backups and restore testing, retention policy, parser/JVM patching, and process/container memory/CPU/disk limits remain operator responsibilities. Data is not encrypted by this application. Registration is open, emails are unverified, and there is no MFA, password recovery, account administration, or compliance/certification claim. Neither local comparison nor absence of returned matches establishes originality, attribution correctness, or authorship.
