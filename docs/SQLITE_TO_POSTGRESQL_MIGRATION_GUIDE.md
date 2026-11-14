# Institutional SQLite to PostgreSQL Migration Guide

## 1. Executive Overview

Originality Desk supports two persistence architectures:
1. **Embedded SQLite (WAL mode)**: Designed for desktop workstations, single-node standalone evaluation, and departmental deployments ($< 50,000$ documents).
2. **Enterprise PostgreSQL (with pgvector)**: Designed for institutional production clusters requiring high concurrency, multi-server load balancing, institutional Single Sign-On, and large-scale semantic vector indexing.

This guide details the verified, zero-data-loss procedure for migrating institutional repositories from SQLite to PostgreSQL.

---

## 2. Migration Architecture and Safety Mechanisms

The migration toolchain (`com.writingassistant.persistence.migration.SqliteToPostgresMigrator`) guarantees data integrity through:
- **Topological Dependency Ordering**: Entities are extracted and inserted in strict relational order (`workspaces` $\to$ `users` $\to$ `documents` $\to$ `analyses` $\to$ `reviews`), eliminating foreign key constraint failures.
- **Atomic Transaction Rollback**: Data insertion occurs within an uncommitted transaction; any batch error triggers an immediate `targetConn.rollback()`, ensuring the target database is never left in an inconsistent partial state.
- **Data Type Transformations**: `TypeTransformer` automatically converts SQLite strings into PostgreSQL `TIMESTAMPTZ`, aligns integer flags, and maps binary data.
- **Sequence Synchronization**: Automatically calls `setval()` on all PostgreSQL `BIGSERIAL` sequences (e.g., `audit_logs.id`) to match the highest migrated ID.
- **Automated Post-Migration Validation**: Compares row count parity per table, verifies zero orphaned records, and confirms cryptographic hash consistency before declaring success.

---

## 3. Step-by-Step Migration Runbook

### Step 1: Create Verified SQLite Backup
Before initiating migration, flush write-ahead logs and create an immutable copy:

```bash
# Checkpoint and truncate WAL
sqlite3 data/originality_desk.db "PRAGMA wal_checkpoint(TRUNCATE);"

# Create timestamped cold backup
cp data/originality_desk.db data/originality_desk.db.backup.$(date +%s)
```

### Step 2: Put Application in Maintenance Mode
Stop all web server instances and background worker processes to guarantee zero concurrent writes:

```bash
# Example systemd / docker stop
docker compose stop originality-desk-server
```

### Step 3: Run Pre-Flight Dry-Run Inspection
Verify connectivity, inspect source row counts, and validate target schema compatibility without altering the target database:

```bash
java -cp target/writing-assistant-1.0.0-SNAPSHOT.jar \
  com.writingassistant.persistence.migration.SqliteToPostgresMigrationCli \
  --source data/originality_desk.db \
  --target jdbc:postgresql://postgres.institutional.internal:5432/originality_desk \
  --user originality_admin \
  --password "$DB_PASSWORD" \
  --dry-run
```

**Verify output:**
- Status must report `DRY_RUN_SUCCESS`.
- Total source rows must match expected institutional record counts.

### Step 4: Execute Live Migration
Run the live batch migration:

```bash
java -cp target/writing-assistant-1.0.0-SNAPSHOT.jar \
  com.writingassistant.persistence.migration.SqliteToPostgresMigrationCli \
  --source data/originality_desk.db \
  --target jdbc:postgresql://postgres.institutional.internal:5432/originality_desk \
  --user originality_admin \
  --password "$DB_PASSWORD"
```

**Expected output:**
```
Table Transfer Summary:
  workspaces           : 1 source rows -> 1 target rows
  users                : 28 source rows -> 28 target rows
  documents            : 1420 source rows -> 1420 target rows
  analyses             : 1418 source rows -> 1418 target rows
  reviews              : 312 source rows -> 312 target rows
  audit_logs           : 4890 source rows -> 4890 target rows
  external_references  : 1105 source rows -> 1105 target rows
----------------------------------------------------------
Status:   SUCCESS
Post-migration validation PASSED with 0 integrity violations.
```

### Step 5: Update Application Configuration
Update `config.properties` or environment variables to point to PostgreSQL:

```properties
db.url=jdbc:postgresql://postgres.institutional.internal:5432/originality_desk
db.user=originality_admin
db.password=SecretPasswordHere
```

### Step 6: Restart Application and Verify Health
Start the application and verify connectivity:

```bash
curl -f http://localhost:8080/api/health
```

---

## 4. Rollback Plan

If post-migration validation detects integrity violations or target infrastructure encounters unrecoverable issues:
1. Stop the application server pointing to PostgreSQL.
2. Revert `config.properties` to point to the local SQLite database (`jdbc:sqlite:data/originality_desk.db`).
3. If SQLite was touched, restore from `data/originality_desk.db.backup.<timestamp>`.
4. Restart the application on SQLite. Zero data loss is incurred because SQLite was held read-only during the transfer window.
