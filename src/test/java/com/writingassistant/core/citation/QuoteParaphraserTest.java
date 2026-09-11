package com.writingassistant.core.citation;

import com.writingassistant.ai.AiFeatureUnavailableException;
import com.writingassistant.ai.AiResponse;
import com.writingassistant.ai.GeminiClient;
import com.writingassistant.ai.GeminiException;
import com.writingassistant.config.ConfigManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class QuoteParaphraserTest {

    @TempDir
    Path tempDir;

    static class MockGeminiClient extends GeminiClient {
        final AtomicReference<String> capturedSystemInstruction = new AtomicReference<>();
        final AtomicReference<String> capturedPrompt = new AtomicReference<>();
        String cannedResponse = "Repeated exposure increases belief in false claims because familiar statements are easily mistaken for facts.";
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
                throw new AiFeatureUnavailableException("Gemini API key is not configured.");
            }
            capturedSystemInstruction.set(systemInstruction);
            capturedPrompt.set(prompt);
            return AiResponse.success(cannedResponse, "test-model");
        }
    }

    @Test
    @DisplayName("Prompt builder includes mandatory anti-patchwriting directives and quote text")
    void testPromptStructure() {
        String quote = "A reliable way to make people believe in falsehoods is frequent repetition.";
        String prompt = CitationPromptTemplates.buildPrompt(quote);

        assertNotNull(prompt);
        assertTrue(prompt.contains(quote), "Prompt must embed original quotation");
        assertTrue(prompt.toLowerCase().contains(
                "do not simply substitute synonyms while keeping the same sentence structure; restructure the sentence and reorganize ideas while preserving factual accuracy".toLowerCase()),
                "Prompt must contain explicit restructuring directive");
        assertTrue(prompt.contains("Preserve the original meaning, claims, numerical figures, and factual nuances"),
                "Prompt must contain factual preservation directive");
        assertTrue(prompt.contains("Return ONLY the plain text paraphrase"),
                "Prompt must instruct plain text output");
    }

    @Test
    @DisplayName("QuoteParaphraser transforms quote into restructured text using Gemini client")
    void testParaphraseExecution() throws GeminiException {
        ConfigManager configManager = new ConfigManager(tempDir);
        MockGeminiClient mockClient = new MockGeminiClient(configManager);
        QuoteParaphraser paraphraser = new QuoteParaphraser(mockClient);

        SourceQuote source = new SourceQuote(
                "A reliable way to make people believe in falsehoods is frequent repetition, because familiarity is not easily distinguished from truth.",
                "Daniel Kahneman", "2011", "Thinking, Fast and Slow", "Farrar, Straus and Giroux");

        String result = paraphraser.paraphrase(source);
        assertNotNull(result);
        assertEquals("Repeated exposure increases belief in false claims because familiar statements are easily mistaken for facts.", result);

        // Verify captured prompt
        assertTrue(mockClient.capturedPrompt.get().contains("A reliable way to make people believe"));
        assertTrue(mockClient.capturedSystemInstruction.get().contains("ethical, citation-safe source paraphrasing"));
    }

    @Test
    @DisplayName("QuoteParaphraser cleans enclosing quotes, backticks, and prefix labels")
    void testParaphraseResponseCleaning() throws GeminiException {
        ConfigManager configManager = new ConfigManager(tempDir);
        MockGeminiClient mockClient = new MockGeminiClient(configManager);
        mockClient.cannedResponse = "```\n\"Paraphrase: Clean restructured statement without quotes.\"\n```";
        QuoteParaphraser paraphraser = new QuoteParaphraser(mockClient);

        String result = paraphraser.paraphrase("Original quote.");
        assertEquals("Clean restructured statement without quotes.", result);
    }

    @Test
    @DisplayName("QuoteParaphraser throws AiFeatureUnavailableException when client is unconfigured")
    void testThrowsWhenUnconfigured() {
        ConfigManager configManager = new ConfigManager(tempDir);
        MockGeminiClient mockClient = new MockGeminiClient(configManager);
        mockClient.configured = false;

        QuoteParaphraser paraphraser = new QuoteParaphraser(mockClient);
        SourceQuote source = new SourceQuote("Quote", "Author", "2020", "Title", "Pub");

        AiFeatureUnavailableException ex = assertThrows(AiFeatureUnavailableException.class, () ->
                paraphraser.paraphrase(source));
        assertTrue(ex.getMessage().contains("Gemini API key is not configured"));
    }
}
