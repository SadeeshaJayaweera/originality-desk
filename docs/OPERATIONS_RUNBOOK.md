# Originality Desk — Operations & Maintenance Runbook

This runbook guides system administrators in monitoring, maintaining, and troubleshooting Originality Desk in production.

---

## 1. System Health & Telemetry

### 1.1 Health Probes

Originality Desk exposes two unauthenticated lightweight health check endpoints:

| Endpoint | Method | Purpose | Normal Response | Failure Response |
|---|---|---|---|---|
| `/health/live` | `GET` | Container / process liveness probe. Checks if the HTTP server is accepting connections. | `200 OK` `{"status":"UP"}` | Connection refused / `503` |
| `/health/ready` | `GET` | Readiness probe for load balancers. Verifies that the database is reachable and the background job worker dispatcher thread is active. | `200 OK` `{"status":"UP"}` | `503 Service Unavailable` `{"status":"DOWN"}` |

### 1.2 Administrative System Health Endpoint

Authorized institution administrators can inspect live operational metrics via the REST API:

```http
GET /api/v1/admin/health
Cookie: desk_session=<token>
```

Example response:
```json
{
  "status": "UP",
  "workerAlive": true,
  "queueDepth": 0,
  "storageBytes": 5242880,
  "totalDocuments": 42,
  "totalAnalyses": 38,
  "totalUsers": 12,
  "jvmFreeMemoryBytes": 184549376,
  "jvmTotalMemoryBytes": 268435456,
  "jvmMaxMemoryBytes": 536870912
}
```

### 1.3 Structured Request Logging

The HTTP server emits structured JSON request logs to stdout for ingestion by Fluentd, Vector, Datadog, or CloudWatch:

```json
{
  "event": "request_completed",
  "method": "POST",
  "routeCategory": "analyses",
  "status": 202,
  "durationMs": 4,
  "requestId": "87a06323-deed-4770-af89-29fd2b522518"
}
```

---

## 2. Common Operational Incidents & Remediation

### Incident 1: Background Job Worker Down (`/health/ready` returns 503)

**Symptoms:**
- `/health/ready` returns `{"status":"DOWN"}`
- New document uploads remain indefinitely in `EXTRACTING` status
- Analyses remain indefinitely in `QUEUED` status

**Root Cause:**
The dispatcher thread encountered an unhandled fatal condition or the JVM experienced severe resource exhaustion.

**Remediation:**
1. Check service logs: `journalctl -u originality-desk -n 100 --no-pager`
2. Restart the application service:
   ```sh
   sudo systemctl restart originality-desk
   ```
3. Automatic Recovery: On startup, `JobWorker.recover()` automatically inspects the database:
   - Any job marked `RUNNING` from the interrupted process is reset to `QUEUED` (if attempts $< 3$) or marked `FAILED` (if attempts $\ge 3$).
   - Any orphaned blob files in `documents/` without matching database records are pruned.

---

### Incident 2: "Server data directory is already in use" on Startup

**Symptoms:**
The application fails to start with:
`java.io.IOException: Server data directory is already in use`

**Root Cause:**
An existing server process is already running, or a previous process was abruptly killed (e.g., `kill -9`) without releasing the channel file lock.

**Remediation:**
1. Check for running Java processes:
   ```sh
   pgrep -af "OriginalityDeskServer"
   ```
2. If another process is running, stop it cleanly.
3. If no process is running, verify the file lock status in `DESK_DATA_DIR`:
   ```sh
   fuser /var/lib/originality-desk/server.lock
   ```
4. If no OS process holds the lock, remove the stale file:
   ```sh
   rm -f /var/lib/originality-desk/server.lock
   ```
5. Restart the server.

---

### Incident 3: High Rate of `403 CSRF_REJECTED` Errors

**Symptoms:**
Users encounter errors when uploading documents or starting analyses; server logs show `CSRF_REJECTED`.

**Root Cause:**
- The client's browser sends an `Origin` header that does not match `DESK_PUBLIC_ORIGIN`.
- Common cause: Mismatch between proxy port/scheme and `DESK_PUBLIC_ORIGIN` (e.g., proxy terminates HTTPS on 443, but `DESK_PUBLIC_ORIGIN` is configured with `http://` or port `8080`).

**Remediation:**
1. Inspect the incoming `Origin` and `Host` headers in the proxy logs.
2. Update `/etc/originality-desk.env` or Docker `.env` so `DESK_PUBLIC_ORIGIN` matches the exact browser URL.
3. Restart the server.

---

### Incident 4: `429 RATE_LIMITED` on PDF Export

**Symptoms:**
Users downloading large PDF reports intermittently receive HTTP 429.

**Root Cause:**
Originality Desk uses a strict concurrency semaphore (`pdfExports = new Semaphore(2)`) to prevent CPU/memory exhaustion from simultaneous PDFBox vector rendering tasks.

**Remediation:**
The server responds with header `Retry-After: 60`. Clients and frontend UI automatically handle this by informing the user to retry in a few moments. If institutional demand requires higher concurrency and server RAM permits ($> 4$ GB), increase JVM `-Xmx` accordingly.

---

## 3. Routine Maintenance Tasks

### 3.1 SQLite WAL Truncation & Optimization
While the server handles checkpoints automatically, you may run manual maintenance during off-peak hours:

```sh
# Issue WAL checkpoint
sudo -u desk sqlite3 /var/lib/originality-desk/server.sqlite3 "PRAGMA wal_checkpoint(TRUNCATE);"

# Optimize database statistics
sudo -u desk sqlite3 /var/lib/originality-desk/server.sqlite3 "PRAGMA optimize;"
```

### 3.2 Data Retention Sweeps
Configure `DESK_RETENTION_DAYS` in your environment (e.g. `90`). The worker executes a batch sweep every 60 seconds to purge expired documents and zero-fill deleted blob contents.

---

## 4. Architectural Boundaries & Horizontal Scaling Path

The current release is optimized as a high-integrity, zero-external-dependency single-node platform utilizing:
- In-process SQLite with WAL mode and atomic file locking
- Local disk blob storage with POSIX permissions
- In-process bounded queue worker

**When to migrate to multi-node clustering:**
If institutional requirements exceed:
- $10,000$ active student submissions per day
- Multi-host active-active load balancing behind a cluster
- Distributed object storage requirements (AWS S3, Google Cloud Storage)

For high-scale horizontal clustering:
1. Transition database persistence from SQLite to PostgreSQL with row-level tenant security.
2. Replace local disk blob storage with S3-compatible object storage.
3. Decouple the background job worker into a dedicated worker pool coordinated via RabbitMQ, Redis BullMQ, or PostgreSQL listen/notify.

