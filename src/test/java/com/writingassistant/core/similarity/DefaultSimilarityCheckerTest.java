package com.writingassistant.core.similarity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultSimilarityCheckerTest {

    private DefaultSimilarityChecker checker;

    private static final String ORIGINAL_SOURCE =
            "A reliable way to make people believe in falsehoods is frequent repetition, because familiarity is not easily distinguished from truth.";

    @BeforeEach
    void setUp() {
        checker = new DefaultSimilarityChecker();
    }

    @Test
    @DisplayName("Near-identical text scores HIGH risk (>= 55%) with high n-gram overlap")
    void testNearIdenticalTextScoresHighRisk() {
        // Only 1-2 words swapped; structure and nearly all phrasing identical
        String nearIdentical =
                "A dependable way to make people believe in falsehoods is frequent repetition, because familiarity is not easily separated from truth.";

        SimilarityReport report = checker.checkSimilarity(ORIGINAL_SOURCE, nearIdentical);

        assertNotNull(report);
        assertEquals(SimilarityRisk.HIGH, report.getRisk(),
                "Near-identical text must score HIGH risk: " + report.getSummary());
        assertTrue(report.getSimilarityScore() >= 55.0,
                "Score should be >= 55%, got: " + report.getSimilarityScore());
        assertTrue(report.getNGramOverlap() >= 50.0,
                "N-Gram overlap should be high for near-identical text");
        assertFalse(report.getMatchedSpans().isEmpty(),
                "Should flag multiple verbatim/overlapping spans");

        System.out.println("=== High Risk Test Report ===");
        System.out.println(report.getSummary());
    }

    @Test
    @DisplayName("Genuinely restructured paraphrase scores LOW risk (< 25%)")
    void testWellRestructuredParaphraseScoresLowRisk() {
        // Genuinely restructured: new sentence order, distinct phrasing, same meaning
        String goodParaphrase =
                "Because people struggle to differentiate familiarity from factual accuracy, hearing an assertion repeatedly makes them more likely to accept it as true.";

        SimilarityReport report = checker.checkSimilarity(ORIGINAL_SOURCE, goodParaphrase);

        assertNotNull(report);
        assertEquals(SimilarityRisk.LOW, report.getRisk(),
                "Genuinely restructured paraphrase must score LOW risk: " + report.getSummary());
        assertTrue(report.getSimilarityScore() < 25.0,
                "Score should be < 25%, got: " + report.getSimilarityScore());
        assertTrue(report.getNGramOverlap() < 10.0,
                "N-Gram overlap should be very low for genuine paraphrase");

        System.out.println("=== Low Risk Test Report ===");
        System.out.println(report.getSummary());
    }

    @Test
    @DisplayName("Synonym-swap patchwriting scores at least MODERATE risk (>= 25%)")
    void testSynonymSwapPatchwritingScoresModerateRisk() {
        // Structural mirroring: same clause order, punctuation, and rhythm with 1-to-1 word substitutions
        String synonymSwap =
                "A trustworthy way to cause humans to trust in untruths is regular repetition, because acquaintance is not readily separated from reality.";

        SimilarityReport report = checker.checkSimilarity(ORIGINAL_SOURCE, synonymSwap);

        assertNotNull(report);
        assertEquals(SimilarityRisk.MODERATE, report.getRisk(),
                "Synonym-swap patchwriting must score MODERATE risk: " + report.getSummary());
        assertTrue(report.getSimilarityScore() >= 25.0,
                "Score should be >= 25% for synonym-swap patchwriting, got: " + report.getSimilarityScore());

        System.out.println("=== Moderate Risk Test Report ===");
        System.out.println(report.getSummary());
    }

    @Test
    @DisplayName("N-Gram overlap accurately detects shared word sequences")
    void testNGramOverlapAlgorithm() {
        List<String> sourceWords = List.of("deep", "residual", "learning", "for", "image", "recognition");
        List<String> paraWords = List.of("we", "use", "deep", "residual", "learning", "for", "our", "model");

        // 3-grams in para: ("we use deep", "use deep residual", "deep residual learning", "residual learning for", "learning for our", "for our model") -> 6 total
        // matches with source: "deep residual learning", "residual learning for" -> 2 matches -> 2/6 = 33.3%
        double trigramOverlap = checker.computeSingleNGramOverlap(sourceWords, paraWords, 3);
        assertEquals(2.0 / 6.0, trigramOverlap, 0.01);
    }

    @Test
    @DisplayName("Word LCS computation correctly calculates longest ordered subsequence")
    void testLcsRatioAlgorithm() {
        List<String> sourceWords = List.of("the", "quick", "brown", "fox", "jumps", "over", "the", "lazy", "dog");
        List<String> paraWords = List.of("a", "quick", "fox", "leaps", "over", "a", "lazy", "hound");

        // Common subsequence: ["quick", "fox", "over", "lazy"] -> length 4 out of 8 words in para
        double lcsRatio = checker.computeLcsRatio(sourceWords, paraWords);
        assertEquals(4.0 / 8.0, lcsRatio, 0.01);
    }

    @Test
    @DisplayName("Levenshtein similarity returns 1.0 for identical strings and scales with distance")
    void testLevenshteinSimilarity() {
        assertEquals(1.0, checker.computeLevenshteinSimilarity("exact match", "exact match"));
        assertEquals(0.0, checker.computeLevenshteinSimilarity("", "some text"));

        double sim = checker.computeLevenshteinSimilarity("thinking fast and slow", "thinking fast or slow");
        assertTrue(sim > 0.85 && sim < 1.0, "1 character difference in 22 chars should yield > 85% similarity");
    }

    @Test
    @DisplayName("Flagged spans map back to exact character offsets in original raw paraphrase")
    void testFlaggedSpanOffsets() {
        String source = "The quick brown fox jumps over the lazy dog and runs away.";
        String para = "In the morning, the quick brown fox jumps over the river.";

        SimilarityReport report = checker.checkSimilarity(source, para);
        List<MatchedSpan> spans = report.getMatchedSpans();

        assertFalse(spans.isEmpty(), "Should find overlapping phrase span");
        MatchedSpan firstSpan = spans.get(0);

        // Verify the extracted span matches exact character substring
        String extracted = para.substring(firstSpan.getStartIndex(), firstSpan.getEndIndex());
        assertEquals(firstSpan.getMatchedText(), extracted);
        assertTrue(firstSpan.getMatchedText().contains("quick brown fox"));
    }

    @Test
    @DisplayName("Handles empty, blank, and null inputs gracefully without crashing")
    void testEdgeCases() {
        SimilarityReport r1 = checker.checkSimilarity("", "");
        assertEquals(0.0, r1.getSimilarityScore());
        assertEquals(SimilarityRisk.LOW, r1.getRisk());

        SimilarityReport r2 = checker.checkSimilarity(null, "some text");
        assertEquals(0.0, r2.getSimilarityScore());

        SimilarityReport r3 = checker.checkSimilarity("some text", null);
        assertEquals(0.0, r3.getSimilarityScore());
    }
}
