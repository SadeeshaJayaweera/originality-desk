package com.writingassistant.persistence.migration;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * High-level status snapshot of the database migration history.
 */
public record MigrationStatus(
        DatabaseDialect dialect,
        String currentVersion,
        int appliedCount,
        int pendingCount,
        boolean isUpToDate,
        List<MigrationInfoRecord> migrations
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public MigrationStatus {
        Objects.requireNonNull(dialect, "dialect must not be null");
        currentVersion = currentVersion != null ? currentVersion : "NONE";
        migrations = migrations != null ? List.copyOf(migrations) : List.of();
    }
}
