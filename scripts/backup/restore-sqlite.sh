#!/bin/sh
set -e

BACKUP_FILE="$1"
TARGET_DB="${2:-data/originality_desk.db}"

if [ -z "${BACKUP_FILE}" ]; then
    echo "Usage: $0 <path_to_backup.db> [target_db_path]"
    exit 1
fi

SHA_FILE="${BACKUP_FILE}.sha256"

echo "[$(date -Iseconds)] Restoring SQLite database from ${BACKUP_FILE} to ${TARGET_DB}..."

if [ -f "${SHA_FILE}" ]; then
    echo "[$(date -Iseconds)] Verifying SHA-256 checksum..."
    if command -v sha256sum > /dev/null 2>&1; then
        sha256sum -c "${SHA_FILE}"
    elif command -v shasum > /dev/null 2>&1; then
        shasum -a 256 -c "${SHA_FILE}"
    fi
fi

# Atomic replacement
TMP_TARGET="${TARGET_DB}.restore.tmp"
cp "${BACKUP_FILE}" "${TMP_TARGET}"
mv "${TMP_TARGET}" "${TARGET_DB}"

echo "[$(date -Iseconds)] SQLite database restored successfully: ${TARGET_DB}"
