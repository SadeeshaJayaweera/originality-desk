# Production Deployment and Backup Readiness Guide

## 1. Production Architecture Overview

The production deployment of Originality Desk runs as a containerized multi-tier cluster using Docker Compose:

```
                          [ Ingress / TLS Reverse Proxy ]
                                         │
                                         ▼
                      ┌────────────────────────────────────┐
                      │    originality-desk (Java 17)     │
                      │  - HTTP API Server (8080)          │
                      │  - Background JobWorker Engine     │
                      └─────────────┬────────────────┬─────┘
                                    │                │
                                    ▼                ▼
                     ┌──────────────────┐    ┌──────────────────┐
                     │ PostgreSQL 16    │    │ SeaweedFS        │
                     │ + pgvector       │    │ S3 Object Store  │
                     └────────┬─────────┘    └──────────────────┘
                              │
                              ▼
                     ┌──────────────────┐
                     │ backup-sidecar   │
                     │ (Daily Crond)    │
                     └──────────────────┘
```

---

## 2. Service Definitions (`docker-compose.prod.yml`)

1. **`originality-desk`**:
   - Packaged from shaded JAR on minimal JRE image (`eclipse-temurin:17-jre-jammy`).
   - Runs as non-root user `desk:desk` (UID 1001).
   - Resource limits: 4 CPUs, 4096 MB RAM (`-XX:MaxRAMPercentage=75.0`).
   - Liveness (`/health/live`) and readiness (`/health/ready`) probes.
2. **`postgres`**:
   - `pgvector/pgvector:pg16` image providing PostgreSQL 16 with native vector similarity operations.
   - Healthcheck: `pg_isready -U originality_admin -d originality_desk`.
3. **`seaweedfs`**:
   - S3-compatible object storage server on port 8333.
   - Persisted on dedicated `seaweed_data` volume.
4. **`backup-sidecar`**:
   - Automated Alpine cron container executing nightly compressed backups with SHA-256 checksums and 30-day retention rotations.

---

## 3. Health Checks and Operational Readiness

| Probe Endpoint | Purpose | Criteria |
|---|---|---|
| `GET /health/live` | Container liveness | Server socket is accepting HTTP requests (HTTP 200). |
| `GET /health/ready` | Traffic readiness | Database connection active AND JobWorker background thread running (HTTP 200). Returns 503 if degraded. |
| `GET /api/v1/admin/health` | Diagnostic telemetry | Authenticated admin endpoint providing detailed CPU, memory, thread pool, and queue metrics. |

---

## 4. Disaster Recovery Runbook

### Recovery Metrics
- **Target RTO (Recovery Time Objective)**: $< 15$ minutes.
- **Target RPO (Recovery Point Objective)**: $< 24$ hours (or $< 5$ minutes with continuous WAL archiving).

### Step-by-Step Restoration Procedure

1. **Stop Application Container**:
   ```bash
   docker compose -f docker-compose.prod.yml stop originality-desk
   ```
2. **Verify Backup Integrity and Restore**:
   ```bash
   # Execute restore script inside postgres container
   docker compose -f docker-compose.prod.yml exec postgres \
       /scripts/restore-postgres.sh /backups/originality_desk_20261114_020000.sql.gz
   ```
3. **Restart Application Container and Verify Readiness**:
   ```bash
   docker compose -f docker-compose.prod.yml start originality-desk
   curl -f http://localhost:8080/health/ready
   ```

---

## 5. Verification and Test Suite

Verified via `DatabaseBackupTest`:
1. `backupCreationAndManifest`: Confirms online non-blocking backup creation, SHA-256 checksum generation, and manifest generation.
2. `restoreAndVerifyParity`: Tests full database restoration into clean target path and verifies data parity.
3. `rejectsTamperedBackupFile`: Simulates corrupted or tampered backup bytes and verifies immediate security rejection prior to schema application.
