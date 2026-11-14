package com.writingassistant.persistence.migration;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * High-performance, zero-data-loss database migrator transitioning institutional data
 * from local SQLite instances to enterprise PostgreSQL databases.
 */
public class SqliteToPostgresMigrator {

    private static final Logger LOGGER = Logger.getLogger(SqliteToPostgresMigrator.class.getName());
    private static final int BATCH_SIZE = 500;

    private final String sourceSqliteUrl;
    private final String targetPostgresUrl;
    private final String targetUser;
    private final String targetPassword;
    private boolean dryRun = false;

    public SqliteToPostgresMigrator(String sourceSqliteUrl, String targetPostgresUrl) {
        this(sourceSqliteUrl, targetPostgresUrl, null, null);
    }

    public SqliteToPostgresMigrator(String sourceSqliteUrl, String targetPostgresUrl, String targetUser, String targetPassword) {
        this.sourceSqliteUrl = Objects.requireNonNull(sourceSqliteUrl, "sourceSqliteUrl must not be null");
        this.targetPostgresUrl = Objects.requireNonNull(targetPostgresUrl, "targetPostgresUrl must not be null");
        this.targetUser = targetUser;
        this.targetPassword = targetPassword;
    }

    public SqliteToPostgresMigrator setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
        return this;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    /**
     * Executes the migration process with pre-flight inspections, bulk batch copy,
     * sequence updates, and post-migration validation checks.
     */
    public MigrationValidationReport migrate() {
        long startTime = System.currentTimeMillis();
        List<String> auditMessages = new ArrayList<>();
        List<String> integrityViolations = new ArrayList<>();
        Map<String, Long> sourceCounts = new LinkedHashMap<>();
        Map<String, Long> targetCounts = new LinkedHashMap<>();

        auditMessages.add("Starting SQLite to PostgreSQL migration workflow.");
        auditMessages.add("Source SQLite URL: " + sourceSqliteUrl);
        auditMessages.add("Target PostgreSQL URL: " + targetPostgresUrl);
        auditMessages.add("Mode: " + (dryRun ? "DRY-RUN (inspection only)" : "LIVE EXECUTION"));

        // 1. Inspect source database and record row counts
        try (Connection sourceConn = DriverManager.getConnection(sourceSqliteUrl)) {
            for (String table : TableMigrationOrder.INSERT_ORDER) {
                if (tableExists(sourceConn, table)) {
                    long count = getRowCount(sourceConn, table);
                    sourceCounts.put(table, count);
                    auditMessages.add("Source table '" + table + "' contains " + count + " records.");
                } else {
                    sourceCounts.put(table, 0L);
                    auditMessages.add("Source table '" + table + "' not found in SQLite schema.");
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to connect to source SQLite database: " + e.getMessage(), e);
            throw new MigrationException("Cannot inspect source SQLite database: " + e.getMessage(), e);
        }

        long totalSourceRows = sourceCounts.values().stream().mapToLong(Long::longValue).sum();
        auditMessages.add("Total source rows identified across all tables: " + totalSourceRows);

        if (dryRun) {
            auditMessages.add("Dry-run complete. No target database changes were made.");
            return new MigrationValidationReport(
                    MigrationValidationReport.STATUS_DRY_RUN_SUCCESS,
                    true,
                    totalSourceRows,
                    0L,
                    sourceCounts,
                    Map.of(),
                    List.of(),
                    auditMessages,
                    System.currentTimeMillis() - startTime
            );
        }

        // 2. Ensure target database schema is migrated and up-to-date
        auditMessages.add("Verifying target PostgreSQL database schema...");
        DatabaseMigrationService targetMigrationService = new DatabaseMigrationService(targetPostgresUrl, targetUser, targetPassword);
        targetMigrationService.migrate();
        targetMigrationService.validate();
        auditMessages.add("Target PostgreSQL schema successfully verified.");

        // 3. Perform data transfer within an atomic transaction
        try (Connection sourceConn = DriverManager.getConnection(sourceSqliteUrl);
             Connection targetConn = targetUser != null
                     ? DriverManager.getConnection(targetPostgresUrl, targetUser, targetPassword)
                     : DriverManager.getConnection(targetPostgresUrl)) {

            targetConn.setAutoCommit(false);

            try {
                for (String table : TableMigrationOrder.INSERT_ORDER) {
                    long count = sourceCounts.getOrDefault(table, 0L);
                    if (count == 0) continue;

                    copyTableData(sourceConn, targetConn, table, auditMessages);
                }

                // If target is PostgreSQL, update sequences
                if (targetPostgresUrl.toLowerCase().contains("postgresql") || targetPostgresUrl.toLowerCase().contains("postgres")) {
                    resetPostgresSequence(targetConn, "audit_logs", "id", auditMessages);
                }

                targetConn.commit();
                auditMessages.add("All table batches committed successfully to target database.");
            } catch (Exception e) {
                targetConn.rollback();
                LOGGER.log(Level.SEVERE, "Migration transaction failed. Target state rolled back: " + e.getMessage(), e);
                throw new MigrationException("Migration transaction failed and was rolled back: " + e.getMessage(), e);
            }

            // 4. Post-migration data integrity validation
            auditMessages.add("Starting post-migration validation checks...");
            for (String table : TableMigrationOrder.INSERT_ORDER) {
                if (tableExists(targetConn, table)) {
                    long tCount = getRowCount(targetConn, table);
                    targetCounts.put(table, tCount);
                    long sCount = sourceCounts.getOrDefault(table, 0L);
                    if (tCount != sCount) {
                        integrityViolations.add("Row count mismatch for table '" + table + "': source=" + sCount + ", target=" + tCount);
                    }
                } else {
                    targetCounts.put(table, 0L);
                    if (sourceCounts.getOrDefault(table, 0L) > 0) {
                        integrityViolations.add("Table '" + table + "' missing in target database.");
                    }
                }
            }

            // Referential integrity check: orphan users
            try (Statement st = targetConn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users u LEFT JOIN workspaces w ON u.workspace_id = w.id WHERE w.id IS NULL")) {
                if (rs.next() && rs.getInt(1) > 0) {
                    integrityViolations.add("Foreign key violation: " + rs.getInt(1) + " orphan users without valid workspace.");
                }
            }

            // Referential integrity check: orphan documents
            try (Statement st = targetConn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM documents d LEFT JOIN workspaces w ON d.workspace_id = w.id WHERE w.id IS NULL")) {
                if (rs.next() && rs.getInt(1) > 0) {
                    integrityViolations.add("Foreign key violation: " + rs.getInt(1) + " orphan documents without valid workspace.");
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error during transfer or validation: " + e.getMessage(), e);
            throw new MigrationException("Database error during transfer: " + e.getMessage(), e);
        }

        long totalTargetRows = targetCounts.values().stream().mapToLong(Long::longValue).sum();
        boolean success = integrityViolations.isEmpty();
        if (success) {
            auditMessages.add("Post-migration validation PASSED with 0 integrity violations.");
        } else {
            auditMessages.add("Post-migration validation FAILED with " + integrityViolations.size() + " integrity violations.");
        }

        return new MigrationValidationReport(
                success ? MigrationValidationReport.STATUS_SUCCESS : MigrationValidationReport.STATUS_FAILED,
                false,
                totalSourceRows,
                totalTargetRows,
                sourceCounts,
                targetCounts,
                integrityViolations,
                auditMessages,
                System.currentTimeMillis() - startTime
        );
    }

    private void copyTableData(Connection sourceConn, Connection targetConn, String table, List<String> auditMessages) throws SQLException {
        String selectSql = "SELECT * FROM " + table;
        try (Statement st = sourceConn.createStatement();
             ResultSet rs = st.executeQuery(selectSql)) {

            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            List<String> columnNames = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                columnNames.add(meta.getColumnName(i));
            }

            StringBuilder insertSql = new StringBuilder("INSERT INTO ").append(table).append(" (");
            StringBuilder placeholders = new StringBuilder("VALUES (");
            for (int i = 0; i < columnNames.size(); i++) {
                insertSql.append(columnNames.get(i));
                placeholders.append("?");
                if (i < columnNames.size() - 1) {
                    insertSql.append(", ");
                    placeholders.append(", ");
                }
            }
            insertSql.append(") ").append(placeholders).append(")");

            try (PreparedStatement insertStmt = targetConn.prepareStatement(insertSql.toString())) {
                int batchCount = 0;
                long totalCopied = 0;

                while (rs.next()) {
                    for (int i = 1; i <= columnCount; i++) {
                        String colName = columnNames.get(i - 1);
                        Object val = rs.getObject(i);
                        Object transformed = TypeTransformer.transform(colName, val);
                        insertStmt.setObject(i, transformed);
                    }
                    insertStmt.addBatch();
                    batchCount++;
                    totalCopied++;

                    if (batchCount >= BATCH_SIZE) {
                        insertStmt.executeBatch();
                        batchCount = 0;
                    }
                }

                if (batchCount > 0) {
                    insertStmt.executeBatch();
                }

                auditMessages.add("Copied " + totalCopied + " rows into table '" + table + "'.");
            }
        }
    }

    private void resetPostgresSequence(Connection conn, String table, String idCol, List<String> auditMessages) {
        try (Statement st = conn.createStatement()) {
            st.execute("SELECT setval(pg_get_serial_sequence('" + table + "', '" + idCol + "'), COALESCE(MAX(" + idCol + "), 1)) FROM " + table);
            auditMessages.add("PostgreSQL sequence for " + table + "." + idCol + " synchronized.");
        } catch (SQLException e) {
            LOGGER.warning("Could not reset sequence for " + table + "." + idCol + ": " + e.getMessage());
        }
    }

    private boolean tableExists(Connection conn, String table) throws SQLException {
        DatabaseMetaData md = conn.getMetaData();
        try (ResultSet rs = md.getTables(null, null, table, null)) {
            if (rs.next()) return true;
        }
        // Try uppercase for cases where DB identifiers are folded
        try (ResultSet rs = md.getTables(null, null, table.toUpperCase(), null)) {
            return rs.next();
        }
    }

    private long getRowCount(Connection conn, String table) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }
}
