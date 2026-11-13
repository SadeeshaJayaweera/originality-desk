package com.writingassistant.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("ConfigManager creates app directory and default config.json")
    void testInitCreatesDirectoryAndFile() {
        Path customDir = tempDir.resolve(".test-writing-assistant");
        assertFalse(Files.exists(customDir));

        ConfigManager configManager = new ConfigManager(customDir);

        assertTrue(Files.exists(customDir), "App data directory should be created");
        assertTrue(Files.exists(configManager.getConfigFile()), "config.json should be created");
        assertTrue(Files.exists(configManager.getLogsDir()), "logs directory should be created");
        assertNotNull(configManager.getConfig());
        assertEquals("gemini-1.5-flash", configManager.getConfig().getGeminiModel());
        assertEquals("APA", configManager.getConfig().getDefaultCitationStyle());
    }

    @Test
    @DisplayName("ConfigManager persists and reloads configuration")
    void testSaveAndLoadConfig() {
        Path customDir = tempDir.resolve(".test-config-save");
        ConfigManager manager1 = new ConfigManager(customDir);

        manager1.getConfig().setGeminiApiKey("test-api-key-12345");
        manager1.getConfig().setDefaultCitationStyle("MLA");
        manager1.saveConfig();

        // Create new manager instance pointing to the same folder
        ConfigManager manager2 = new ConfigManager(customDir);
        assertEquals("test-api-key-12345", manager2.getConfig().getGeminiApiKey());
        assertEquals("MLA", manager2.getConfig().getDefaultCitationStyle());
    }

    @Test
    void offlineModePersistsAndOverridesAllKeys() {
        ConfigManager manager = new ConfigManager(tempDir);
        manager.getConfig().setGeminiApiKey("stored-secret");
        manager.getConfig().setOfflineMode(true);
        manager.saveConfig();
        ConfigManager reloaded = new ConfigManager(tempDir);
        assertTrue(reloaded.getConfig().isOfflineMode());
        assertNull(reloaded.getEffectiveApiKey());
        assertFalse(reloaded.isAiAvailable());
        assertTrue(reloaded.getAiUnavailableReason().contains("Offline mode is enabled"));
        reloaded.getConfig().setOfflineMode(false);
        assertEquals("stored-secret", reloaded.getEffectiveApiKey());
    }

    @Test
    void secretConfigIsOwnerOnlyAndExistingPermissionsAreHardened() throws Exception {
        assumeTrue(Files.getFileStore(tempDir).supportsFileAttributeView("posix"));
        ConfigManager manager = new ConfigManager(tempDir);
        manager.getConfig().setGeminiApiKey("secret");
        manager.saveConfig();
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(manager.getConfigFile()));
        Files.setPosixFilePermissions(manager.getConfigFile(), PosixFilePermissions.fromString("rw-r--r--"));
        ConfigManager reloaded = new ConfigManager(tempDir);
        assertEquals("secret", reloaded.getConfig().getGeminiApiKey());
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(manager.getConfigFile()));
    }

    @Test
    void malformedConfigurationFailsOfflineWithoutLoggingSecretValues() throws Exception {
        ConfigManager manager = new ConfigManager(tempDir);
        String secret = "do-not-log-this-secret";
        Files.writeString(manager.getConfigFile(), "{\"geminiApiKey\":\"" + secret + "\",\"offlineMode\":{}}");
        java.util.List<java.util.logging.LogRecord> records = new java.util.ArrayList<>();
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(ConfigManager.class.getName());
        java.util.logging.Handler handler = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        logger.addHandler(handler);
        try {
            ConfigManager reloaded = new ConfigManager(tempDir);
            assertTrue(reloaded.getConfig().isOfflineMode());
            assertFalse(reloaded.isAiAvailable());
            assertFalse(records.isEmpty());
            for (java.util.logging.LogRecord record : records) {
                assertFalse(record.getMessage().contains(secret));
                assertNull(record.getThrown());
            }
        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    @DisplayName("Provides clear explanation when AI key is missing")
    void testAiUnavailableReason() {
        Path customDir = tempDir.resolve(".test-config-unavail");
        ConfigManager manager = new ConfigManager(customDir);
        manager.getConfig().setGeminiApiKey(null);
        manager.saveConfig();

        if (System.getenv("GEMINI_API_KEY") == null || System.getenv("GEMINI_API_KEY").isBlank()) {
            assertFalse(manager.isAiAvailable());
            String reason = manager.getAiUnavailableReason();
            assertNotNull(reason);
            assertTrue(reason.contains("Gemini API key is not configured"));
            assertTrue(reason.contains("Offline similarity checking"));
        }
    }
}
