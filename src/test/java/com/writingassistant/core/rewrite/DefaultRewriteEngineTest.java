package com.writingassistant.core.rewrite;

import com.writingassistant.ai.AiFeatureUnavailableException;
import com.writingassistant.ai.AiResponse;
import com.writingassistant.ai.GeminiClient;
import com.writingassistant.ai.GeminiException;
import com.writingassistant.config.AppConfig;
import com.writingassistant.config.ConfigManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DefaultRewriteEngineTest {

    @TempDir
    Path tempDir;

    static class MockGeminiClient extends GeminiClient {
        final AtomicInteger callCount = new AtomicInteger(0);
        String cannedResponse = "1. First alternative rewrite.\n2. Second alternative rewrite.";
        boolean configured = true;

        MockGeminiClient(ConfigManager configManager) {
            super(configManager);
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public AiResponse generateText(String systemInstruction, String prompt) throws GeminiException {
            if (!configured) {
                throw new AiFeatureUnavailableException("AI features disabled. API key missing.");
            }
            callCount.incrementAndGet();
            return AiResponse.success(cannedResponse, "test-model");
        }
    }

    @Test
    @DisplayName("RewriteEngine returns structured result and caches identical queries")
    void testRewriteAndCaching() throws GeminiException {
        ConfigManager configManager = new ConfigManager(tempDir);
        MockGeminiClient mockClient = new MockGeminiClient(configManager);
        RewriteCache cache = new RewriteCache();
        DefaultRewriteEngine engine = new DefaultRewriteEngine(mockClient, cache);

        String input = "Due to the fact that it was raining, the event was postponed.";

        // First call -> calls AI client
        RewriteResult result1 = engine.rewrite(input, RewriteMode.TIGHTEN);
        assertNotNull(result1);
        assertEquals(2, result1.getAlternatives().size());
        assertEquals("First alternative rewrite.", result1.getAlternatives().get(0));
        assertEquals("Second alternative rewrite.", result1.getAlternatives().get(1));
        assertEquals(RewriteMode.TIGHTEN, result1.getMode());
        assertEquals(1, mockClient.callCount.get());

        // Second call with same text and mode -> returned from cache, callCount remains 1
        RewriteResult result2 = engine.rewrite(input, RewriteMode.TIGHTEN);
        assertEquals(1, mockClient.callCount.get(), "Client should not be called again on cache hit");
        assertEquals(result1.getAlternatives(), result2.getAlternatives());

        // Third call with different mode -> triggers new AI client call
        RewriteResult result3 = engine.rewrite(input, RewriteMode.FORMAL);
        assertEquals(2, mockClient.callCount.get(), "Different mode should trigger a new API call");
        assertEquals(RewriteMode.FORMAL, result3.getMode());
    }

    @Test
    @DisplayName("Throws AiFeatureUnavailableException when Gemini key is not configured")
    void testThrowsWhenUnconfigured() {
        ConfigManager configManager = new ConfigManager(tempDir);
        MockGeminiClient mockClient = new MockGeminiClient(configManager);
        mockClient.configured = false;

        DefaultRewriteEngine engine = new DefaultRewriteEngine(mockClient, new RewriteCache());

        AiFeatureUnavailableException ex = assertThrows(AiFeatureUnavailableException.class, () ->
                engine.rewrite("Some passage", RewriteMode.CLARITY));

        assertTrue(ex.getMessage().contains("AI features disabled"));
    }

    @Test
    @DisplayName("Handles null or blank input text gracefully without calling API")
    void testBlankInputReturnsEmptyResult() throws GeminiException {
        ConfigManager configManager = new ConfigManager(tempDir);
        MockGeminiClient mockClient = new MockGeminiClient(configManager);
        DefaultRewriteEngine engine = new DefaultRewriteEngine(mockClient, new RewriteCache());

        RewriteResult result = engine.rewrite("   ", RewriteMode.CLARITY);
        assertNotNull(result);
        assertTrue(result.getAlternatives().isEmpty());
        assertEquals(0, mockClient.callCount.get());
    }
}
