# Originality Desk — Backup and Disaster Recovery Runbook

This runbook outlines procedures for backing up, verifying, and recovering Originality Desk data in institutional production environments.

---

## 1. Data Assets & Storage Layout

All persistent data for Originality Desk is contained within the directory defined by `DESK_DATA_DIR` (default: `./desk-server-data` or `/var/lib/originality-desk`):

```
${DESK_DATA_DIR}/
├── server.lock           # Atomic process file lock (do not back up)
├── server.sqlite3        # SQLite primary relational database
├── server.sqlite3-wal    # Write-Ahead Log journal (active transactions)
├── server.sqlite3-shm    # Shared memory index (ephemeral)
└── documents/            # Content-addressed binary document storage
    ├── 4a8b...01fe       # Raw uploaded file blobs (opaque random filenames)
    └── 9c2d...88aa
```

### Relational Database (`server.sqlite3`)
Stores workspaces, users, PBKDF2 password hashes, session hashes, document metadata, extracted normalized text, analysis snapshots, passage matches, citations, reviews, and audit logs.

### Document Blobs (`documents/`)
Stores original uploaded PDF, DOCX, and TXT files. Files are verified for SHA-256 integrity upon ingestion and securely wiped (zero-filled) upon user deletion.

---

## 2. Backup Procedures

### 2.1 Live Online SQLite Backup (Recommended)

Because Originality Desk operates in SQLite Write-Ahead Logging (`WAL`) mode with `synchronous=FULL`, you can safely perform online backups while the server is actively handling requests without stopping the service:

```sh
#!/usr/bin/env bash
set -euo pipefail

DATA_DIR="/var/lib/originality-desk"
BACKUP_DIR="/var/backups/originality-desk"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
TARGET="${BACKUP_DIR}/${TIMESTAMP}"

mkdir -p "${TARGET}"
chmod 700 "${TARGET}"

echo "[INFO] Initiating online database backup to ${TARGET}..."
sqlite3 "${DATA_DIR}/server.sqlite3" ".backup '${TARGET}/server.sqlite3'"

echo "[INFO] Backing up binary document blobs via rsync..."
rsync -a --delete "${DATA_DIR}/documents/" "${TARGET}/documents/"

echo "[INFO] Running database integrity check on backup..."
CHECK_RESULT=$(sqlite3 "${TARGET}/server.sqlite3" "PRAGMA integrity_check;")
if [ "${CHECK_RESULT}" != "ok" ]; then
    echo "[ERROR] Database backup failed integrity check: ${CHECK_RESULT}" >&2
    exit 1
fi

echo "[INFO] Creating compressed, encrypted archive..."
tar -czf "${TARGET}.tar.gz" -C "${BACKUP_DIR}" "${TIMESTAMP}"
rm -rf "${TARGET}"
chmod 600 "${TARGET}.tar.gz"

echo "[SUCCESS] Backup completed successfully: ${TARGET}.tar.gz"
```

### 2.2 Volume / Snapshot-Based Backup (Container & VM Environments)

For AWS EBS, Google Cloud Persistent Disk, or ZFS/LVM:
1. Issue a SQLite WAL checkpoint:
   ```sh
   sqlite3 /var/lib/originality-desk/server.sqlite3 "PRAGMA wal_checkpoint(TRUNCATE);"
   ```
2. Trigger the storage volume snapshot.

---

## 3. Disaster Recovery & Restoration Procedure

Follow these steps to restore service following hardware failure, catastrophic storage loss, or corruption:

### Step 1: Terminate Existing Service
Ensure no running process holds access to the target data directory:
```sh
sudo systemctl stop originality-desk
# Or for Docker:
docker compose down
```

### Step 2: Unpack Backup Archive
Extract the verified backup into a clean target directory:
```sh
RESTORE_TIMESTAMP="20261113_033000"
BACKUP_ARCHIVE="/var/backups/originality-desk/${RESTORE_TIMESTAMP}.tar.gz"
TARGET_DIR="/var/lib/originality-desk"

# Clean target directory
sudo rm -rf "${TARGET_DIR:?}"/*

# Unpack
sudo tar -xzf "${BACKUP_ARCHIVE}" -C /tmp/
sudo cp -r "/tmp/${RESTORE_TIMESTAMP}/"* "${TARGET_DIR}/"
sudo rm -rf "/tmp/${RESTORE_TIMESTAMP}"
```

### Step 3: Enforce Correct Permissions
The server enforces strict POSIX file permissions on startup and will reject unsafe permissions:
```sh
sudo chown -R desk:desk "${TARGET_DIR}"
sudo chmod 700 "${TARGET_DIR}"
sudo chmod 700 "${TARGET_DIR}/documents"
sudo chmod 600 "${TARGET_DIR}/server.sqlite3"

# Remove any stale lock file from previous crashed instance
sudo rm -f "${TARGET_DIR}/server.lock" "${TARGET_DIR}/server.sqlite3-wal" "${TARGET_DIR}/server.sqlite3-shm"
```

### Step 4: Verify Database Integrity
```sh
sudo -u desk sqlite3 "${TARGET_DIR}/server.sqlite3" "PRAGMA integrity_check;"
sudo -u desk sqlite3 "${TARGET_DIR}/server.sqlite3" "PRAGMA foreign_key_check;"
```
Both queries must output `ok` or return empty for `foreign_key_check`.

### Step 5: Start Service & Verify Health
```sh
sudo systemctl start originality-desk
sudo systemctl status originality-desk

# Check readiness probe
curl -s http://127.0.0.1:8080/health/ready
```

Expected response: `{"status":"UP"}`.

---

## 4. Backup Retention & SLA Guidelines

| Metric | Target | Notes |
|---|---|---|
| **Recovery Point Objective (RPO)** | 1 Hour | Hourly online SQLite backups ensure minimal loss in case of volume failure. |
| **Recovery Time Objective (RTO)** | < 15 Minutes | Single archive unpack and integrity check takes under 5 minutes on standard SSDs. |
| **Local Retention** | 14 Days | Store rolling daily snapshots on local backup storage. |
| **Offsite Retention** | 90 Days | Encrypted, air-gapped offsite replication (S3 Glacier, institutional tape). |

