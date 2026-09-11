package com.writingassistant.core.citation;

import com.writingassistant.ai.GeminiClient;
import com.writingassistant.config.ConfigManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CitationSmokeTest {

    @Test
    @DisplayName("Smoke Test: Live quote paraphrase and citation formatting when GEMINI_API_KEY is available")
    void testLiveQuoteParaphraseQuality() throws Exception {
        String effectiveKey = ConfigManager.getInstance().getEffectiveApiKey();
        assumeTrue(effectiveKey != null && !effectiveKey.isBlank(),
                "Skipping live smoke test because no GEMINI_API_KEY is configured.");

        GeminiClient client = new GeminiClient(ConfigManager.getInstance());
        DefaultCitationEngine engine = new DefaultCitationEngine(client);

        SourceQuote sample = new SourceQuote(
                "A reliable way to make people believe in falsehoods is frequent repetition, because familiarity is not easily distinguished from truth.",
                "Daniel Kahneman", "2011", "Thinking, Fast and Slow", "Farrar, Straus and Giroux", "62", null, SourceType.BOOK);

        CitedParaphrase result = engine.paraphraseWithCitation(sample, CitationStyle.APA);
        assertNotNull(result);
        assertNotNull(result.getParaphrasedText());
        assertFalse(result.getParaphrasedText().isBlank());

        System.out.println("=== Live Citation Paraphrase Smoke Test ===");
        System.out.println("Original Quote:   " + sample.getQuote());
        System.out.println("Paraphrased Text: " + result.getParaphrasedText());
        System.out.println("APA In-Text:      " + result.getInTextCitation());
        System.out.println("APA Reference:    " + result.getBibliographyEntry());
        System.out.println("Full Insert:      " + result.getFullFormattedPassage());

        assertEquals("(Kahneman, 2011, p. 62)", result.getInTextCitation());
        assertEquals("Kahneman, D. (2011). *Thinking, Fast and Slow*. Farrar, Straus and Giroux.", result.getBibliographyEntry());
    }

    @Test
    @DisplayName("Simulated Quality Smoke Test: Verifies end-to-end paraphrasing and formatting on real academic quotes")
    void testSimulatedQuoteParaphraseQuality() throws Exception {
        QuoteParaphraser mockParaphraser = new QuoteParaphraser(new GeminiClient()) {
            @Override
            public String paraphrase(String quote) {
                return "Because people struggle to differentiate familiarity from factual accuracy, hearing an assertion repeatedly makes them more likely to accept it as true.";
            }
        };

        DefaultCitationEngine engine = new DefaultCitationEngine(mockParaphraser);

        SourceQuote sample = new SourceQuote(
                "A reliable way to make people believe in falsehoods is frequent repetition, because familiarity is not easily distinguished from truth.",
                "Daniel Kahneman", "2011", "Thinking, Fast and Slow", "Farrar, Straus and Giroux", "62", null, SourceType.BOOK);

        // APA Verification
        CitedParaphrase apaResult = engine.paraphraseWithCitation(sample, CitationStyle.APA);
        assertEquals("(Kahneman, 2011, p. 62)", apaResult.getInTextCitation());
        assertEquals("Kahneman, D. (2011). *Thinking, Fast and Slow*. Farrar, Straus and Giroux.", apaResult.getBibliographyEntry());
        assertEquals("Because people struggle to differentiate familiarity from factual accuracy, hearing an assertion repeatedly makes them more likely to accept it as true (Kahneman, 2011, p. 62).",
                apaResult.getFullFormattedPassage());

        // MLA Verification
        CitedParaphrase mlaResult = engine.paraphraseWithCitation(sample, CitationStyle.MLA);
        assertEquals("(Kahneman 62)", mlaResult.getInTextCitation());
        assertEquals("Kahneman, Daniel. *Thinking, Fast and Slow*. Farrar, Straus and Giroux, 2011.", mlaResult.getBibliographyEntry());
        assertEquals("Because people struggle to differentiate familiarity from factual accuracy, hearing an assertion repeatedly makes them more likely to accept it as true (Kahneman 62).",
                mlaResult.getFullFormattedPassage());

        // Chicago Verification
        CitedParaphrase chicagoResult = engine.paraphraseWithCitation(sample, CitationStyle.CHICAGO);
        assertEquals("(Kahneman 2011, 62)", chicagoResult.getInTextCitation());
        assertEquals("Kahneman, Daniel. 2011. *Thinking, Fast and Slow*. Farrar, Straus and Giroux.", chicagoResult.getBibliographyEntry());
        assertEquals("Because people struggle to differentiate familiarity from factual accuracy, hearing an assertion repeatedly makes them more likely to accept it as true (Kahneman 2011, 62).",
                chicagoResult.getFullFormattedPassage());
    }
}
