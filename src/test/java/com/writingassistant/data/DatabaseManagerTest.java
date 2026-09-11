package com.writingassistant.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseManagerTest {

    @TempDir
    Path tempDir;

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
