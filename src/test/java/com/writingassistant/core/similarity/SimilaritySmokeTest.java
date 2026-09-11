package com.writingassistant.core.similarity;

import com.writingassistant.core.citation.DefaultCitationEngine;
import com.writingassistant.core.citation.QuoteParaphraser;
import com.writingassistant.core.citation.SourceQuote;
import com.writingassistant.core.citation.SourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SimilaritySmokeTest {

    private DefaultSimilarityChecker checker;

    @BeforeEach
    void setUp() {
        checker = new DefaultSimilarityChecker();
    }

    @Test
    @DisplayName("Smoke Test: Evaluates multiple real academic quotes and confirms genuine paraphrases score LOW risk")
    void testRealAcademicQuoteParaphraseQuality() {
        Map<String, String> quoteToParaphraseMap = new LinkedHashMap<>();

        // Example 1: Daniel Kahneman (Cognitive Psychology)
        quoteToParaphraseMap.put(
                "A reliable way to make people believe in falsehoods is frequent repetition, because familiarity is not easily distinguished from truth.",
                "Because people struggle to differentiate familiarity from factual accuracy, hearing an assertion repeatedly makes them more likely to accept it as true."
        );

        // Example 2: Ashish Vaswani et al. (Machine Learning)
        quoteToParaphraseMap.put(
                "The dominant sequence transduction models are based on complex recurrent or convolutional neural networks that include an encoder and a decoder.",
                "Standard architectures for sequence conversion rely primarily on encoder-decoder structures powered by recurrent or convolutional networks."
        );

        // Example 3: Jakob Nielsen (Usability & Design)
        quoteToParaphraseMap.put(
                "Consistency is one of the most powerful usability principles: when things always behave the same, users do not have to worry about what will happen.",
                "Predictable software behavior reduces cognitive friction, allowing individuals to interact confidently without second-guessing system outcomes."
        );

        System.out.println("================================================================");
        System.out.println("  Similarity Quality Smoke Test on Genuine Paraphrases");
        System.out.println("================================================================");

        for (Map.Entry<String, String> entry : quoteToParaphraseMap.entrySet()) {
            String originalQuote = entry.getKey();
            String genuineParaphrase = entry.getValue();

            SimilarityReport report = checker.checkSimilarity(originalQuote, genuineParaphrase);

            System.out.println("Source:     \"" + originalQuote + "\"");
            System.out.println("Paraphrase: \"" + genuineParaphrase + "\"");
            System.out.println("Score:      " + report.getScorePercentage() + " (" + report.getRisk().getLabel() + ")");
            System.out.println("Details:    N-gram=" + String.format("%.1f%%", report.getNGramOverlap())
                    + ", LCS=" + String.format("%.1f%%", report.getLcsRatio())
                    + ", Edit=" + String.format("%.1f%%", report.getEditSimilarity()));
            System.out.println("----------------------------------------------------------------");

            // Assertions
            assertEquals(SimilarityRisk.LOW, report.getRisk(),
                    "Genuinely restructured paraphrase must achieve LOW risk: " + report.getSummary());
            assertTrue(report.getSimilarityScore() < 30.0,
                    "Similarity score must be < 30% for quality paraphrase, got: " + report.getSimilarityScore());
        }
    }
}
