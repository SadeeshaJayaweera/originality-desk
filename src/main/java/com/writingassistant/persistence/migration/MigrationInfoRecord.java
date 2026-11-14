package com.writingassistant.persistence.migration;

import java.io.Serializable;

/**
 * Metadata record for an individual Flyway migration.
 */
public record MigrationInfoRecord(
        String version,
        String description,
        String state,
        String installedOn,
        String script
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
