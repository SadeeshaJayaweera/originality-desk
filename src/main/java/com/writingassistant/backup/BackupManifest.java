package com.writingassistant.backup;

import java.io.Serializable;
import java.util.Objects;

/**
 * Cryptographically verified metadata manifest accompanying a database backup snapshot.
 */
public record BackupManifest(
        String backupId,
        String databaseType,
        String timestamp,
        long fileSizeBytes,
        String sha256Checksum,
        int tableCount,
        long totalRowCount,
        String originalDatabaseName
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public BackupManifest {
        Objects.requireNonNull(backupId, "backupId must not be null");
        Objects.requireNonNull(databaseType, "databaseType must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(sha256Checksum, "sha256Checksum must not be null");
    }
}
