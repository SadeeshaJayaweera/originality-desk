package com.writingassistant.similarity;

/**
 * Classification of similarity detection strategies employed by the Originality Desk engine.
 */
public enum SimilarityStrategyType {
    /**
     * Exact verbatim word-sequence matching via fixed-length shingles (n-grams).
     */
    EXACT,

    /**
     * Near-duplicate paraphrase matching via lexical window overlaps and shingle set Jaccard distance.
     */
    NEAR_DUPLICATE,

    /**
     * Vector embedding and neural representation similarity (Sentence Transformers, ONNX Runtime, pgvector).
     */
    SEMANTIC,

    /**
     * Deterministic string metric combination (N-gram overlap, Longest Common Subsequence, Levenshtein distance).
     */
    HEURISTIC
}
