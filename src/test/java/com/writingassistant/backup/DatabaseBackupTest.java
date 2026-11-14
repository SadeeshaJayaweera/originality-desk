package com.writingassistant.backup;

import com.writingassistant.persistence.migration.DatabaseMigrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseBackupTest {

    @Test
    @DisplayName("DatabaseBackupEngine creates verified snapshot with accurate manifest and SHA-256 checksum")
    void backupCreationAndManifest(@TempDir Path tempDir) throws Exception {
        Path dbPath = tempDir.resolve("original.db");
        Path backupDir = tempDir.resolve("backups");
        String jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();

        DatabaseMigrationService.verifyOrMigrate(jdbcUrl);
        seedData(jdbcUrl);

        DatabaseBackupEngine engine = new DatabaseBackupEngine();
        DatabaseBackupEngine.BackupResult result = engine.backupSqlite(jdbcUrl, backupDir);

        assertNotNull(result);
        assertTrue(Files.exists(result.backupFilePath()));
        assertTrue(Files.exists(result.manifestFilePath()));

        BackupManifest manifest = result.manifest();
        assertEquals("SQLITE", manifest.databaseType());
        assertTrue(manifest.tableCount() >= 10);
        assertTrue(manifest.totalRowCount() >= 2);
        assertTrue(manifest.fileSizeBytes() > 0);
        assertNotNull(manifest.sha256Checksum());
        assertEquals(64, manifest.sha256Checksum().length());
    }

    @Test
    @DisplayName("DatabaseBackupEngine restores backup snapshot and verifies data parity")
    void restoreAndVerifyParity(@TempDir Path tempDir) throws Exception {
        Path sourceDb = tempDir.resolve("source.db");
        Path backupDir = tempDir.resolve("backups");
        Path restoredDb = tempDir.resolve("restored.db");
        String sourceUrl = "jdbc:sqlite:" + sourceDb.toAbsolutePath();

        DatabaseMigrationService.verifyOrMigrate(sourceUrl);
        seedData(sourceUrl);

        DatabaseBackupEngine engine = new DatabaseBackupEngine();
        DatabaseBackupEngine.BackupResult result = engine.backupSqlite(sourceUrl, backupDir);

        // Restore to target
        engine.restoreSqlite(result.backupFilePath(), restoredDb, result.manifest());
        assertTrue(Files.exists(restoredDb));

        // Verify restored database contents
        String restoredUrl = "jdbc:sqlite:" + restoredDb.toAbsolutePath();
        try (Connection conn = DriverManager.getConnection(restoredUrl);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM workspaces WHERE id='ws-b1'")) {
            assertTrue(rs.next());
            assertEquals("Backup Test University", rs.getString("name"));
        }
    }

    @Test
    @DisplayName("DatabaseBackupEngine rejects tampered backup files with SecurityException")
    void rejectsTamperedBackupFile(@TempDir Path tempDir) throws Exception {
        Path sourceDb = tempDir.resolve("source_tamper.db");
        Path backupDir = tempDir.resolve("backups_tamper");
        Path restoredDb = tempDir.resolve("restored_tamper.db");
        String sourceUrl = "jdbc:sqlite:" + sourceDb.toAbsolutePath();

        DatabaseMigrationService.verifyOrMigrate(sourceUrl);
        seedData(sourceUrl);

        DatabaseBackupEngine engine = new DatabaseBackupEngine();
        DatabaseBackupEngine.BackupResult result = engine.backupSqlite(sourceUrl, backupDir);

        // Tamper with backup file by appending corrupt byte
        Files.write(result.backupFilePath(), new byte[]{0x00, 0x01}, java.nio.file.StandardOpenOption.APPEND);

        // Restoration must detect checksum violation and abort
        SecurityException ex = assertThrows(SecurityException.class, () ->
                engine.restoreSqlite(result.backupFilePath(), restoredDb, result.manifest()));

        assertTrue(ex.getMessage().contains("Cryptographic verification failed"));
        assertFalse(Files.exists(restoredDb), "Target database must not be created upon tampering");
    }

    private void seedData(String jdbcUrl) throws Exception {
        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO workspaces(id, name, created_at) VALUES('ws-b1', 'Backup Test University', ?)")) {
                ps.setString(1, Instant.now().toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users(id, workspace_id, name, email, role, password_hash, created_at) "
                            + "VALUES('usr-b1', 'ws-b1', 'Admin', 'admin@backup.edu', 'INSTITUTION_ADMIN', 'hash', ?)")) {
                ps.setString(1, Instant.now().toString());
                ps.executeUpdate();
            }
        }
    }
}
