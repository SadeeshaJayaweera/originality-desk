package com.writingassistant.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void everyConnectionEnforcesForeignKeys() throws Exception {
        DatabaseManager manager = new DatabaseManager(tempDir.resolve("fk.db"));
        for (int i = 0; i < 3; i++) {
            try (Connection conn = manager.getConnection(); Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys")) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                }
                assertThrows(SQLException.class, () -> stmt.executeUpdate(
                        "INSERT INTO document_citations VALUES ('missing-document', 'missing-citation')"));
            }
        }
    }

    @Test
    void initializationFailsClosedForUnexpectedMigrationFailure() throws Exception {
        String url = "jdbc:sqlite:" + tempDir.resolve("bad-schema.db");
        try (Connection conn = DriverManager.getConnection(url); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE VIEW documents AS SELECT 'id' AS id");
        }
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> new DatabaseManager(url));
        assertTrue(error.getMessage().contains(url));
        assertTrue(error.getMessage().contains("permissions"));
        assertInstanceOf(SQLException.class, error.getCause());
        try (Connection conn = DriverManager.getConnection(url); Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='citations'")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "Partial initialization must roll back");
        }
    }

    @Test
    void duplicateColumnsRemainIdempotent() {
        DatabaseManager manager = new DatabaseManager(tempDir.resolve("repeat.db"));
        assertDoesNotThrow(manager::initializeSchema);
    }

    @Test
    @DisplayName("DatabaseManager creates SQLite database file and initializes tables")
    void testDatabaseInitialization() throws Exception {
        Path dbPath = tempDir.resolve("test_writing_assistant.db");
        DatabaseManager dbManager = new DatabaseManager(dbPath);

        try (Connection conn = dbManager.getConnection(); Statement stmt = conn.createStatement()) {
            assertTrue(conn.isValid(2), "Database connection should be valid");

            // Verify tables exist
            try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='documents';")) {
                assertTrue(rs.next(), "Table 'documents' should exist");
            }

            try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='citations';")) {
                assertTrue(rs.next(), "Table 'citations' should exist");
            }

            try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='rewrite_history';")) {
                assertTrue(rs.next(), "Table 'rewrite_history' should exist");
            }
        }
    }
}
