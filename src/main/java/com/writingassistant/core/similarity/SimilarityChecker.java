package com.writingassistant.core.similarity;

/**
 * Interface for the local, offline paraphrase-similarity checker (Phase 3).
 * Uses deterministic algorithms (n-gram overlap, longest common subsequence, edit distance).
 */
public interface SimilarityChecker {

    /**
     * Analyzes similarity between a candidate paraphrase and the source text.
     * Operates 100% offline without network calls or API keys.
     *
     * @param sourceText     Original source passage
     * @param paraphraseText Candidate rewritten/paraphrased passage
     * @return SimilarityReport containing risk rating, score, and flagged overlapping spans
     */
    SimilarityReport checkSimilarity(String sourceText, String paraphraseText);
}
