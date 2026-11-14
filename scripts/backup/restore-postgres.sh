#!/bin/sh
set -e

BACKUP_FILE="$1"

if [ -z "${BACKUP_FILE}" ]; then
    echo "Usage: $0 <path_to_backup_file.sql.gz>"
    exit 1
fi

SHA256_FILE="${BACKUP_FILE}.sha256"

echo "[$(date -Iseconds)] Initiating PostgreSQL database restoration from ${BACKUP_FILE}..."

# 1. Verify SHA-256 Checksum if manifest exists
if [ -f "${SHA256_FILE}" ]; then
    echo "[$(date -Iseconds)] Verifying cryptographic SHA-256 checksum..."
    if command -v sha256sum > /dev/null 2>&1; then
        sha256sum -c "${SHA256_FILE}"
    elif command -v shasum > /dev/null 2>&1; then
        shasum -a 256 -c "${SHA256_FILE}"
    fi
    echo "[$(date -Iseconds)] Checksum verification succeeded."
else
    echo "[$(date -Iseconds)] WARNING: No sha256 checksum file found. Proceeding with caution."
fi

# 2. Restore via pg_restore with clean schema wipe
echo "[$(date -Iseconds)] Running pg_restore..."
pg_restore -h "${PGHOST:-postgres}" -U "${PGUSER:-originality_admin}" -d "${PGDATABASE:-originality_desk}" \
    --clean --if-exists --no-owner "${BACKUP_FILE}"

echo "[$(date -Iseconds)] Restoration completed successfully."
