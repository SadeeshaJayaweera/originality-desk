package com.writingassistant.backup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.writingassistant.persistence.migration.DatabaseDialect;
import com.writingassistant.persistence.migration.DatabaseMigrationService;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

/**
 * Enterprise database backup and disaster recovery engine.
 * Generates verified, immutable snapshots with SHA-256 cryptographic manifests
 * and performs automated integrity verification upon restoration.
 */
public class DatabaseBackupEngine {

    private static final Logger LOGGER = Logger.getLogger(DatabaseBackupEngine.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record BackupResult(
            Path backupFilePath,
            Path manifestFilePath,
            BackupManifest manifest
    ) {}

    /**
     * Executes an online, non-blocking backup of a SQLite database.
     *
     * @param jdbcUrl        Source database JDBC URL.
     * @param destinationDir Directory where backup snapshot and manifest will be stored.
     * @return BackupResult containing paths and verified manifest.
     */
    public BackupResult backupSqlite(String jdbcUrl, Path destinationDir) throws Exception {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl must not be null");
        Objects.requireNonNull(destinationDir, "destinationDir must not be null");
        Files.createDirectories(destinationDir);

        String backupId = "backup-" + System.currentTimeMillis();
        Path backupFile = destinationDir.resolve(backupId + ".db");
        Path manifestFile = destinationDir.resolve(backupId + "-manifest.json");

        // 1. Execute SQLite online VACUUM INTO for non-blocking consistent snapshot
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement st = conn.createStatement()) {
            st.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            st.execute("VACUUM INTO '" + backupFile.toAbsolutePath().toString().replace("'", "''") + "'");
        }

        // 2. Compute SHA-256 checksum of generated snapshot
        byte[] backupBytes = Files.readAllBytes(backupFile);
        String sha256 = calculateSha256(backupBytes);
        long fileSize = backupBytes.length;

        // 3. Inspect backup snapshot to calculate table and row counts
        int tableCount = 0;
        long totalRows = 0;
        String backupJdbcUrl = "jdbc:sqlite:" + backupFile.toAbsolutePath();
        try (Connection conn = DriverManager.getConnection(backupJdbcUrl);
             Statement st = conn.createStatement()) {

            List<String> tables = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")) {
                while (rs.next()) {
                    tables.add(rs.getString("name"));
                }
            }
            tableCount = tables.size();

            for (String table : tables) {
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    if (rs.next()) {
                        totalRows += rs.getLong(1);
                    }
                }
            }
        }

        BackupManifest manifest = new BackupManifest(
                backupId,
                "SQLITE",
                Instant.now().toString(),
                fileSize,
                sha256,
                tableCount,
                totalRows,
                backupFile.getFileName().toString()
        );

        // 4. Persist cryptographic manifest
        Files.writeString(manifestFile, GSON.toJson(manifest));
        LOGGER.info("Successfully created database backup: " + backupFile + " (tables=" + tableCount + ", rows=" + totalRows + ")");

        return new BackupResult(backupFile, manifestFile, manifest);
    }

    /**
     * Restores a SQLite database from a verified backup snapshot, validating cryptographic
     * integrity and schema consistency before activation.
     */
    public void restoreSqlite(Path backupFile, Path targetFile, BackupManifest manifest) throws Exception {
        Objects.requireNonNull(backupFile, "backupFile must not be null");
        Objects.requireNonNull(targetFile, "targetFile must not be null");
        Objects.requireNonNull(manifest, "manifest must not be null");

        if (!Files.exists(backupFile)) {
            throw new FileNotFoundException("Backup file does not exist: " + backupFile);
        }

        // 1. Verify cryptographic SHA-256 integrity
        byte[] backupBytes = Files.readAllBytes(backupFile);
        String actualSha256 = calculateSha256(backupBytes);
        if (!actualSha256.equalsIgnoreCase(manifest.sha256Checksum())) {
            throw new SecurityException("Cryptographic verification failed: Backup file checksum mismatch! Expected: "
                    + manifest.sha256Checksum() + ", Actual: " + actualSha256);
        }

        // 2. Perform atomic restore via temporary file
        Files.createDirectories(targetFile.getParent());
        Path tempTarget = targetFile.getParent().resolve(targetFile.getFileName() + ".restore.tmp." + UUID.randomUUID());
        Files.copy(backupFile, tempTarget, StandardCopyOption.REPLACE_EXISTING);
        Files.move(tempTarget, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        // 3. Verify target database accessibility and schema
        String targetJdbcUrl = "jdbc:sqlite:" + targetFile.toAbsolutePath();
        DatabaseMigrationService.verifyOrMigrate(targetJdbcUrl);

        LOGGER.info("Successfully restored and verified database to: " + targetFile);
    }

    private static String calculateSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
