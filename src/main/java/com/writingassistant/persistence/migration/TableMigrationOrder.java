package com.writingassistant.persistence.migration;

import java.util.List;

/**
 * Defines the strict topological dependency order for extracting and inserting tables
 * to guarantee that foreign key constraints are never violated during migration.
 */
public final class TableMigrationOrder {

    private TableMigrationOrder() {}

    /**
     * Tables ordered from roots (no dependencies) to leaves (dependent on prior tables).
     */
    public static final List<String> INSERT_ORDER = List.of(
            "workspaces",
            "users",
            "sessions",
            "documents",
            "analyses",
            "analysis_sources",
            "jobs",
            "reviews",
            "audit_logs",
            "external_references"
    );

    /**
     * Tables ordered in reverse for clean rollback/deletion without FK constraint violations.
     */
    public static final List<String> DELETE_ORDER = List.of(
            "external_references",
            "audit_logs",
            "reviews",
            "jobs",
            "analysis_sources",
            "analyses",
            "documents",
            "sessions",
            "users",
            "workspaces"
    );
}
