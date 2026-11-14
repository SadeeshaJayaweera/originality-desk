#!/bin/sh
set -e

BACKUP_DIR="${BACKUP_DIR:-/backups}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/originality_desk_${TIMESTAMP}.sql.gz"
SHA256_FILE="${BACKUP_FILE}.sha256"
RETENTION_DAYS="${RETENTION_DAYS:-30}"

echo "[$(date -Iseconds)] Starting PostgreSQL database backup..."
mkdir -p "${BACKUP_DIR}"

# 1. Execute compressed custom-format pg_dump
pg_dump -h "${PGHOST:-postgres}" -U "${PGUSER:-originality_admin}" -d "${PGDATABASE:-originality_desk}" \
    --format=custom --compress=9 --file="${BACKUP_FILE}"

# 2. Compute cryptographic SHA-256 checksum
if command -v sha256sum > /dev/null 2>&1; then
    sha256sum "${BACKUP_FILE}" > "${SHA256_FILE}"
elif command -v shasum > /dev/null 2>&1; then
    shasum -a 256 "${BACKUP_FILE}" > "${SHA256_FILE}"
fi

echo "[$(date -Iseconds)] Backup created successfully: ${BACKUP_FILE}"

# 3. Rotate older backup files
echo "[$(date -Iseconds)] Cleaning backups older than ${RETENTION_DAYS} days..."
find "${BACKUP_DIR}" -name "originality_desk_*.sql.gz*" -mtime "+${RETENTION_DAYS}" -exec rm -f {} \;

echo "[$(date -Iseconds)] Backup routine completed."
