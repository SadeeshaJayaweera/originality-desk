package com.writingassistant.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

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
