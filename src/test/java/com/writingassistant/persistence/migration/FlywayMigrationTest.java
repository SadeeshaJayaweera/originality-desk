package com.writingassistant.persistence.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class FlywayMigrationTest {

    @Test
    @DisplayName("DatabaseDialect resolves correctly from JDBC URLs")
    void dialectResolution() {
        assertEquals(DatabaseDialect.SQLITE, DatabaseDialect.fromJdbcUrl("jdbc:sqlite:/tmp/test.db"));
        assertEquals(DatabaseDialect.SQLITE, DatabaseDialect.fromJdbcUrl("jdbc:sqlite::memory:"));
        assertEquals(DatabaseDialect.POSTGRESQL, DatabaseDialect.fromJdbcUrl("jdbc:postgresql://localhost:5432/originality_desk"));
        assertEquals(DatabaseDialect.POSTGRESQL, DatabaseDialect.fromJdbcUrl("jdbc:postgres://localhost:5432/originality_desk"));

        assertThrows(IllegalArgumentException.class, () -> DatabaseDialect.fromJdbcUrl("jdbc:mysql://localhost:3306/db"));
        assertThrows(IllegalArgumentException.class, () -> DatabaseDialect.fromJdbcUrl(null));
    }

    @Test
    @DisplayName("Flyway applies V1 migration cleanly to empty SQLite database and creates all tables")
    void cleanDatabaseMigration(@TempDir Path tempDir) throws Exception {
        Path dbPath = tempDir.resolve("clean_test.db");
        String jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();

        DatabaseMigrationService service = new DatabaseMigrationService(jdbcUrl);
        int executed = service.migrate();

        assertEquals(1, executed, "V1 migration should be executed");
        service.validate();

        MigrationStatus status = service.getStatus();
        assertEquals("1", status.currentVersion());
        assertEquals(1, status.appliedCount());
        assertEquals(0, status.pendingCount());
        assertTrue(status.isUpToDate());
        assertEquals(DatabaseDialect.SQLITE, status.dialect());

        // Verify tables exist by querying SQLite catalog
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")) {

            java.util.Set<String> tables = new java.util.HashSet<>();
            while (rs.next()) {
                tables.add(rs.getString("name"));
            }

            assertTrue(tables.contains("workspaces"), "workspaces table must exist");
            assertTrue(tables.contains("users"), "users table must exist");
            assertTrue(tables.contains("sessions"), "sessions table must exist");
            assertTrue(tables.contains("documents"), "documents table must exist");
            assertTrue(tables.contains("analyses"), "analyses table must exist");
            assertTrue(tables.contains("analysis_sources"), "analysis_sources table must exist");
            assertTrue(tables.contains("jobs"), "jobs table must exist");
            assertTrue(tables.contains("reviews"), "reviews table must exist");
            assertTrue(tables.contains("audit_logs"), "audit_logs table must exist");
            assertTrue(tables.contains("external_references"), "external_references table must exist");
            assertTrue(tables.contains("flyway_schema_history"), "flyway_schema_history must exist");
        }
    }

    @Test
    @DisplayName("Flyway migration is strictly idempotent; second execution executes 0 migrations")
    void migrationIdempotency(@TempDir Path tempDir) {
        Path dbPath = tempDir.resolve("idempotent_test.db");
        String jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();

        DatabaseMigrationService service = new DatabaseMigrationService(jdbcUrl);
        assertEquals(1, service.migrate());

        // Second run must be idempotent
        assertEquals(0, service.migrate());
        assertDoesNotThrow(service::validate);

        MigrationStatus status = service.getStatus();
        assertEquals("1", status.currentVersion());
        assertEquals(1, status.appliedCount());
        assertTrue(status.isUpToDate());
    }

    @Test
    @DisplayName("PostgreSQL V1 initial schema migration script is valid and well-formed")
    void postgresqlMigrationScriptSyntax() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/db/migration/postgresql/V1__initial_schema.sql")) {
            assertNotNull(is, "PostgreSQL V1 migration resource must exist on classpath");
            String ddl = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(ddl.contains("CREATE TABLE workspaces"), "Must define workspaces table");
            assertTrue(ddl.contains("CREATE TABLE users"), "Must define users table");
            assertTrue(ddl.contains("CREATE TABLE documents"), "Must define documents table");
            assertTrue(ddl.contains("CREATE TABLE analyses"), "Must define analyses table");
            assertTrue(ddl.contains("CREATE TABLE reviews"), "Must define reviews table");
            assertTrue(ddl.contains("CREATE TABLE audit_logs"), "Must define audit_logs table");
            assertTrue(ddl.contains("BIGSERIAL PRIMARY KEY"), "Must use PostgreSQL BIGSERIAL for audit log sequence");
            assertTrue(ddl.contains("TIMESTAMPTZ"), "Must use standard TIMESTAMPTZ timestamp types");
        }
    }

    @Test
    @DisplayName("DatabaseMigrationService verifyOrMigrate helper succeeds on clean and existing databases")
    void verifyOrMigrateUtility(@TempDir Path tempDir) {
        Path dbPath = tempDir.resolve("verify_test.db");
        String jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();

        assertDoesNotThrow(() -> DatabaseMigrationService.verifyOrMigrate(jdbcUrl));
        assertDoesNotThrow(() -> DatabaseMigrationService.verifyOrMigrate(jdbcUrl));
    }
}
