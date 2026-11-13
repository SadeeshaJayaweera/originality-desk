package com.writingassistant.ai;

import com.writingassistant.config.AppConfig;
import com.writingassistant.config.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GeminiConnectivityTest {

    @TempDir
    Path tempDir;

    private ConfigManager unconfiguredManager;

    @BeforeEach
    void setUp() {
        unconfiguredManager = new ConfigManager(tempDir) {
            @Override
            public String getEffectiveApiKey() {
                return null;
            }
        };
        AppConfig config = unconfiguredManager.getConfig();
        config.setGeminiApiKey(null);
        unconfiguredManager.saveConfig();
    }

    @Test
    @DisplayName("App does not crash when no Gemini API key is configured")
    void testWhenNoKeyConfigured_doesNotCrashAndReportsUnavailable() {
        // The fixture disables environment-key lookup, so this remains offline on developer machines too.
        GeminiClient client = new GeminiClient(unconfiguredManager);
        assertFalse(client.isConfigured());
        GeminiStatus status = assertDoesNotThrow(client::testConnection);
        assertNotNull(status);
        assertFalse(status.isAvailable());
        assertTrue(status.getMessage().contains("Gemini API key is not configured"));
        GeminiException exception = assertThrows(AiFeatureUnavailableException.class,
                () -> client.generateText("Test prompt"));
        assertTrue(exception.getMessage().contains("Gemini API key is not configured"));
    }

    @Test
    @DisplayName("Smoke test: Real Gemini API call succeeds when key is configured")
    void testWhenKeyIsConfigured_realApiCallSucceeds() {
        assumeTrue(Boolean.getBoolean("writingassistant.liveTests"),
                "Live Gemini calls require -Dwritingassistant.liveTests=true (network access and API usage charges).");
        String effectiveKey = System.getenv("GEMINI_API_KEY");

        // Gracefully skip live test if no real key is present in current test environment
        assumeTrue(effectiveKey != null && !effectiveKey.isBlank(),
                "Skipping live Gemini API smoke test because no GEMINI_API_KEY is configured in the environment.");

        ConfigManager liveConfigManager = new ConfigManager(tempDir);
        liveConfigManager.getConfig().setGeminiApiKey(effectiveKey);
        liveConfigManager.getConfig().setOfflineMode(false);
        liveConfigManager.saveConfig();

        GeminiClient client = new GeminiClient(liveConfigManager);
        assertTrue(client.isConfigured(), "Client should be configured when valid key is provided");

        // Verify connection test
        GeminiStatus status = client.testConnection();
        assertTrue(status.isAvailable(), "Connectivity test should succeed with real API key: " + status.getMessage());

        // Verify actual generation call
        assertDoesNotThrow(() -> {
            AiResponse response = client.generateText("Please reply with the exact word 'PONG'.");
            assertNotNull(response, "Response should not be null");
            assertTrue(response.isSuccessful(), "Response should be marked successful");
            assertNotNull(response.getContent(), "Generated content should not be null");
            assertFalse(response.getContent().isBlank(), "Generated content should not be blank");
        });
    }
}
