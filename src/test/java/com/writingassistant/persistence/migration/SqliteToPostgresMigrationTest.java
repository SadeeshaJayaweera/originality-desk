package com.writingassistant.persistence.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SqliteToPostgresMigrationTest {

    @Test
    @DisplayName("Dry-run mode inspects source SQLite tables and row counts without modifying target")
    void dryRunInspectsSourceWithoutTargetModification(@TempDir Path tempDir) throws Exception {
        Path sourceDb = tempDir.resolve("source_dryrun.db");
        Path targetDb = tempDir.resolve("target_dryrun.db");
        String sourceUrl = "jdbc:sqlite:" + sourceDb.toAbsolutePath();
        String targetUrl = "jdbc:sqlite:" + targetDb.toAbsolutePath();

        // 1. Initialize and seed source
        DatabaseMigrationService.verifyOrMigrate(sourceUrl);
        seedTestData(sourceUrl);

        // 2. Execute dry-run
        SqliteToPostgresMigrator migrator = new SqliteToPostgresMigrator(sourceUrl, targetUrl);
        migrator.setDryRun(true);
        MigrationValidationReport report = migrator.migrate();

        assertNotNull(report);
        assertTrue(report.isSuccessful());
        assertEquals(MigrationValidationReport.STATUS_DRY_RUN_SUCCESS, report.status());
        assertTrue(report.dryRun());
        assertTrue(report.totalSourceRows() > 0);
        assertEquals(0L, report.totalTargetRows());

        // Target file should not even have user tables populated
        assertTrue(report.targetRowCounts().isEmpty());
    }

    @Test
    @DisplayName("Live migration transfers all records across tables with 100% row parity and 0 violations")
    void completeLiveMigration(@TempDir Path tempDir) throws Exception {
        Path sourceDb = tempDir.resolve("source_live.db");
        Path targetDb = tempDir.resolve("target_live.db");
        String sourceUrl = "jdbc:sqlite:" + sourceDb.toAbsolutePath();
        String targetUrl = "jdbc:sqlite:" + targetDb.toAbsolutePath();

        // 1. Initialize and seed source
        DatabaseMigrationService.verifyOrMigrate(sourceUrl);
        seedTestData(sourceUrl);

        // 2. Execute live migration
        SqliteToPostgresMigrator migrator = new SqliteToPostgresMigrator(sourceUrl, targetUrl);
        migrator.setDryRun(false);
        MigrationValidationReport report = migrator.migrate();

        assertNotNull(report);
        assertTrue(report.isSuccessful(), "Migration must succeed");
        assertEquals(MigrationValidationReport.STATUS_SUCCESS, report.status());
        assertFalse(report.dryRun());
        assertTrue(report.integrityViolations().isEmpty(), "Must have zero integrity violations");
        assertEquals(report.totalSourceRows(), report.totalTargetRows(), "Source and target row counts must match");

        // Verify specific tables
        assertTrue(report.sourceRowCounts().get("workspaces") >= 1);
        assertTrue(report.sourceRowCounts().get("users") >= 1);
        assertTrue(report.sourceRowCounts().get("documents") >= 1);
        assertTrue(report.sourceRowCounts().get("analyses") >= 1);
        assertTrue(report.sourceRowCounts().get("reviews") >= 1);
        assertTrue(report.sourceRowCounts().get("external_references") >= 1);
    }

    @Test
    @DisplayName("TypeTransformer converts ISO-8601 strings, timestamps, booleans, and nulls properly")
    void typeTransformerValidation() {
        assertNull(TypeTransformer.transform("created_at", null));
        assertNull(TypeTransformer.transform("created_at", ""));

        // ISO-8601 timestamp
        Object ts = TypeTransformer.transform("created_at", "2026-11-14T10:00:00Z");
        assertInstanceOf(java.sql.Timestamp.class, ts);

        // Epoch millis timestamp
        Object tsMillis = TypeTransformer.transform("created_at", 1700000000000L);
        assertInstanceOf(java.sql.Timestamp.class, tsMillis);

        // Boolean 0/1 integer
        assertEquals(1, TypeTransformer.transform("is_open_access", 1));
        assertEquals(0, TypeTransformer.transform("is_open_access", 0));

        // Normal text column
        assertEquals("Sample Title", TypeTransformer.transform("title", "Sample Title"));
    }

    @Test
    @DisplayName("TableMigrationOrder provides correct topological insert and reverse delete ordering")
    void tableMigrationOrderVerification() {
        assertNotNull(TableMigrationOrder.INSERT_ORDER);
        assertNotNull(TableMigrationOrder.DELETE_ORDER);

        assertEquals(TableMigrationOrder.INSERT_ORDER.size(), TableMigrationOrder.DELETE_ORDER.size());
        assertEquals("workspaces", TableMigrationOrder.INSERT_ORDER.get(0));
        assertEquals("workspaces", TableMigrationOrder.DELETE_ORDER.get(TableMigrationOrder.DELETE_ORDER.size() - 1));
        assertEquals("external_references", TableMigrationOrder.DELETE_ORDER.get(0));
    }

    private void seedTestData(String jdbcUrl) throws Exception {
        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            conn.setAutoCommit(false);

            // 1. Workspace
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO workspaces(id, name, created_at) VALUES(?, ?, ?)")) {
                ps.setString(1, "ws-mig-1");
                ps.setString(2, "University of Engineering");
                ps.setString(3, Instant.now().toString());
                ps.executeUpdate();
            }

            // 2. User
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users(id, workspace_id, name, email, role, password_hash, created_at) VALUES(?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, "usr-mig-1");
                ps.setString(2, "ws-mig-1");
                ps.setString(3, "Dr. Ada Lovelace");
                ps.setString(4, "ada@institution.edu");
                ps.setString(5, "INSTITUTION_ADMIN");
                ps.setString(6, "hash_pass");
                ps.setString(7, Instant.now().toString());
                ps.executeUpdate();
            }

            // 3. Document
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO documents(id, workspace_id, title, filename, kind, status, error, word_count, page_count, bytes, created_at, storage_name, sha256, text) "
                            + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, "doc-mig-1");
                ps.setString(2, "ws-mig-1");
                ps.setString(3, "Analytical Engine Manuscript");
                ps.setString(4, "manuscript.txt");
                ps.setString(5, "SUBMISSION");
                ps.setString(6, "READY");
                ps.setString(7, null);
                ps.setInt(8, 250);
                ps.setInt(9, 1);
                ps.setLong(10, 1500L);
                ps.setString(11, Instant.now().toString());
                ps.setString(12, "store_name_1");
                ps.setString(13, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
                ps.setString(14, "Text of manuscript");
                ps.executeUpdate();
            }

            // 4. Analysis
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO analyses(id, workspace_id, document_id, title, status, profile, created_at, completed_at, source_count, result_json, request_hash) "
                            + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, "ana-mig-1");
                ps.setString(2, "ws-mig-1");
                ps.setString(3, "doc-mig-1");
                ps.setString(4, "Analysis Title");
                ps.setString(5, "COMPLETED");
                ps.setString(6, "STANDARD");
                ps.setString(7, Instant.now().toString());
                ps.setString(8, Instant.now().toString());
                ps.setInt(9, 1);
                ps.setString(10, "{\"matches\":[]}");
                ps.setString(11, "req_hash_1");
                ps.executeUpdate();
            }

            // 5. Review
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO reviews(id, workspace_id, analysis_id, user_id, match_index, decision, note, created_at) VALUES(?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, "rev-mig-1");
                ps.setString(2, "ws-mig-1");
                ps.setString(3, "ana-mig-1");
                ps.setString(4, "usr-mig-1");
                ps.setInt(5, 0);
                ps.setString(6, "APPROVED");
                ps.setString(7, "Exemplary historical documentation.");
                ps.setString(8, Instant.now().toString());
                ps.executeUpdate();
            }

            // 6. External Reference
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO external_references(workspace_id, document_id, provider_id, external_id, doi, title, is_open_access, citation_count, created_at) "
                            + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, "ws-mig-1");
                ps.setString(2, "doc-mig-1");
                ps.setString(3, "openalex");
                ps.setString(4, "W12345");
                ps.setString(5, "10.1000/182");
                ps.setString(6, "Sketch of the Analytical Engine");
                ps.setInt(7, 1);
                ps.setInt(8, 42);
                ps.setString(9, Instant.now().toString());
                ps.executeUpdate();
            }

            conn.commit();
        }
    }
}
