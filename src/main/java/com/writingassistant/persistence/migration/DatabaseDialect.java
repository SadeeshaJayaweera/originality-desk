package com.writingassistant.persistence.migration;

/**
 * Supported database dialects for Flyway schema evolution.
 */
public enum DatabaseDialect {
    SQLITE("classpath:db/migration/sqlite"),
    POSTGRESQL("classpath:db/migration/postgresql");

    private final String migrationLocation;

    DatabaseDialect(String migrationLocation) {
        this.migrationLocation = migrationLocation;
    }

    public String getMigrationLocation() {
        return migrationLocation;
    }

    public static DatabaseDialect fromJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            throw new IllegalArgumentException("JDBC URL must not be null");
        }
        String lower = jdbcUrl.toLowerCase();
        if (lower.startsWith("jdbc:sqlite:")) {
            return SQLITE;
        } else if (lower.startsWith("jdbc:postgresql:") || lower.startsWith("jdbc:postgres:")) {
            return POSTGRESQL;
        } else {
            throw new IllegalArgumentException("Unsupported database dialect for JDBC URL: " + jdbcUrl);
        }
    }
}
