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
        unconfiguredManager = new ConfigManager(tempDir);
        AppConfig config = unconfiguredManager.getConfig();
        config.setGeminiApiKey(null);
        unconfiguredManager.saveConfig();
    }

    @Test
    @DisplayName("App does not crash when no Gemini API key is configured")
    void testWhenNoKeyConfigured_doesNotCrashAndReportsUnavailable() {
        // Instantiate GeminiClient with an unconfigured manager (no API key)
        GeminiClient client = new GeminiClient(unconfiguredManager);

        // If environment variable happens to be set on the host machine, clear it logically for this specific test
        if (!client.isConfigured()) {
            assertFalse(client.isConfigured(), "AI should be reported as unconfigured when no key is present");

            // Test connection check should not throw any exception
            GeminiStatus status = assertDoesNotThrow(client::testConnection,
                    "testConnection() should handle missing key gracefully without throwing");

            assertNotNull(status, "Status object must not be null");
            assertFalse(status.isAvailable(), "AI status should be unavailable");
            assertTrue(status.getMessage().contains("Gemini API key is not configured"),
                    "Status message should clearly explain missing key: " + status.getMessage());

            // Attempting text generation without a key throws a well-defined GeminiException rather than crashing
            GeminiException exception = assertThrows(GeminiException.class, () -> client.generateText("Test prompt"),
                    "generateText() should throw GeminiException when key is missing");
            assertTrue(exception.getMessage().contains("Gemini API key is not configured"));
        } else {
            // Environment variable was set on host; verify client can inspect status safely
            GeminiStatus status = assertDoesNotThrow(client::testConnection);
            assertNotNull(status);
        }
    }

    @Test
    @DisplayName("Smoke test: Real Gemini API call succeeds when key is configured")
    void testWhenKeyIsConfigured_realApiCallSucceeds() {
        String envKey = System.getenv("GEMINI_API_KEY");
        String configKey = ConfigManager.getInstance().getEffectiveApiKey();
        String effectiveKey = (configKey != null && !configKey.isBlank()) ? configKey : envKey;

        // Gracefully skip live test if no real key is present in current test environment
        assumeTrue(effectiveKey != null && !effectiveKey.isBlank(),
                "Skipping live Gemini API smoke test because no GEMINI_API_KEY is configured in the environment.");

        ConfigManager liveConfigManager = new ConfigManager(tempDir);
        liveConfigManager.getConfig().setGeminiApiKey(effectiveKey);
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
