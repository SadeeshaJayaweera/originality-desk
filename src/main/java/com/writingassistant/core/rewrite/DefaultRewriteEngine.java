package com.writingassistant.core.rewrite;

import com.writingassistant.ai.AiResponse;
import com.writingassistant.ai.GeminiClient;
import com.writingassistant.ai.GeminiException;
import com.writingassistant.data.DatabaseManager;
import com.writingassistant.logging.AppLogger;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Concrete implementation of the style rewriting engine (Phase 1).
 * Orchestrates template prompt generation, Gemini API communication, defensive response parsing, and session caching.
 */
public class DefaultRewriteEngine implements RewriteEngine {

    private static final Logger LOGGER = AppLogger.getLogger(DefaultRewriteEngine.class);

    private final GeminiClient geminiClient;
    private final RewriteCache cache;

    public DefaultRewriteEngine() {
        this(new GeminiClient(), new RewriteCache(DatabaseManager.getInstance()));
    }

    public DefaultRewriteEngine(GeminiClient geminiClient) {
        this(geminiClient, new RewriteCache());
    }

    public DefaultRewriteEngine(GeminiClient geminiClient, RewriteCache cache) {
        this.geminiClient = geminiClient;
        this.cache = (cache != null) ? cache : new RewriteCache();
    }

    @Override
    public RewriteResult rewrite(String text, RewriteMode mode) throws GeminiException {
        if (text == null || text.isBlank()) {
            return new RewriteResult(text, mode != null ? mode : RewriteMode.CLARITY, Collections.emptyList());
        }

        RewriteMode targetMode = (mode != null) ? mode : RewriteMode.CLARITY;
        String trimmedText = text.trim();

        // 1. Check session cache first
        Optional<RewriteResult> cached = cache.get(trimmedText, targetMode);
        if (cached.isPresent()) {
            LOGGER.info("Returning cached rewrite alternatives for mode=" + targetMode);
            return cached.get();
        }

        LOGGER.info("Requesting AI rewrite for mode=" + targetMode + " (text length=" + trimmedText.length() + ")");

        // 2. Build prompt with centralized template
        String systemInstruction = RewritePromptTemplates.getSystemInstruction();
        String prompt = RewritePromptTemplates.buildPrompt(trimmedText, targetMode);

        // 3. Call Gemini AI client (throws AiFeatureUnavailableException if key is missing)
        AiResponse response = geminiClient.generateText(systemInstruction, prompt);

        // 4. Defensively parse response into 2-3 clean alternatives
        List<String> alternatives = RewriteResponseParser.parseAlternatives(response.getContent(), trimmedText);

        RewriteResult result = new RewriteResult(trimmedText, targetMode, alternatives);

        // 5. Store in session cache
        cache.put(trimmedText, targetMode, result);

        return result;
    }

    public RewriteCache getCache() {
        return cache;
    }

    public GeminiClient getGeminiClient() {
        return geminiClient;
    }
}
