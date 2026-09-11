package com.writingassistant.core.citation;

import com.writingassistant.ai.AiResponse;
import com.writingassistant.ai.GeminiClient;
import com.writingassistant.ai.GeminiException;
import com.writingassistant.logging.AppLogger;

import java.util.logging.Logger;

/**
 * Service that transforms direct source quotes into genuinely restructured, citation-safe paraphrases.
 */
public class QuoteParaphraser {

    private static final Logger LOGGER = AppLogger.getLogger(QuoteParaphraser.class);

    private final GeminiClient geminiClient;

    public QuoteParaphraser() {
        this(new GeminiClient());
    }

    public QuoteParaphraser(GeminiClient geminiClient) {
        this.geminiClient = (geminiClient != null) ? geminiClient : new GeminiClient();
    }

    /**
     * Produces a restructured, citation-safe paraphrase of the given source quote.
     *
     * @param source SourceQuote containing the quotation
     * @return Clean plain text paraphrase
     * @throws GeminiException if API is unconfigured or request fails
     */
    public String paraphrase(SourceQuote source) throws GeminiException {
        if (source == null || source.getQuote() == null || source.getQuote().isBlank()) {
            return "";
        }
        return paraphrase(source.getQuote());
    }

    /**
     * Produces a restructured, citation-safe paraphrase of a raw quotation string.
     *
     * @param quote Raw quotation text
     * @return Clean plain text paraphrase
     * @throws GeminiException if API is unconfigured or request fails
     */
    public String paraphrase(String quote) throws GeminiException {
        if (quote == null || quote.isBlank()) {
            return "";
        }

        String trimmedQuote = quote.trim();
        LOGGER.info("Paraphrasing source quote (length=" + trimmedQuote.length() + ")");

        String systemInstruction = CitationPromptTemplates.getSystemInstruction();
        String prompt = CitationPromptTemplates.buildPrompt(trimmedQuote);

        AiResponse response = geminiClient.generateText(systemInstruction, prompt);
        return cleanParaphraseResponse(response.getContent(), trimmedQuote);
    }

    private String cleanParaphraseResponse(String rawContent, String fallback) {
        if (rawContent == null || rawContent.isBlank()) {
            return fallback;
        }

        String cleaned = rawContent.trim();

        // Strip markdown backticks
        if (cleaned.startsWith("```") && cleaned.endsWith("```")) {
            cleaned = cleaned.substring(3, cleaned.length() - 3).trim();
        }

        // Strip enclosing quotes
        if ((cleaned.startsWith("\"") && cleaned.endsWith("\"")) ||
            (cleaned.startsWith("'") && cleaned.endsWith("'")) && cleaned.length() >= 2) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }

        // Filter accidental "Paraphrase: " prefix
        if (cleaned.toLowerCase().startsWith("paraphrase:") || cleaned.toLowerCase().startsWith("paraphrased text:")) {
            int colonIndex = cleaned.indexOf(':');
            if (colonIndex != -1) {
                cleaned = cleaned.substring(colonIndex + 1).trim();
            }
        }

        return cleaned.trim();
    }

    public GeminiClient getGeminiClient() {
        return geminiClient;
    }
}
