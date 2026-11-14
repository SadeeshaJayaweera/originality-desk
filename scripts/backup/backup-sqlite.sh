#!/bin/sh
set -e

SOURCE_DB="${1:-data/originality_desk.db}"
DEST_DIR="${2:-data/backups}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
DEST_FILE="${DEST_DIR}/originality_desk_${TIMESTAMP}.db"
SHA_FILE="${DEST_FILE}.sha256"

mkdir -p "${DEST_DIR}"

echo "[$(date -Iseconds)] Backing up SQLite database ${SOURCE_DB} to ${DEST_FILE}..."

# Checkpoint WAL first
sqlite3 "${SOURCE_DB}" "PRAGMA wal_checkpoint(TRUNCATE);"
# Online non-blocking vacuum snapshot
sqlite3 "${SOURCE_DB}" "VACUUM INTO '${DEST_FILE}';"

if command -v sha256sum > /dev/null 2>&1; then
    sha256sum "${DEST_FILE}" > "${SHA_FILE}"
elif command -v shasum > /dev/null 2>&1; then
    shasum -a 256 "${DEST_FILE}" > "${SHA_FILE}"
fi

echo "[$(date -Iseconds)] SQLite backup created: ${DEST_FILE}"
