package com.writingassistant.similarity;

import java.util.List;

/**
 * Common pluggable strategy interface decoupling exact, near-duplicate, heuristic,
 * and semantic similarity detection algorithms.
 */
public interface SimilarityStrategy {

    /**
     * The category of similarity detection implemented by this strategy.
     */
    SimilarityStrategyType getType();

    /**
     * Unique identifier for this strategy engine (e.g. "exact-shingle", "near-window", "sentence-transformers-minilm").
     */
    String getName();

    /**
     * Human-readable description of the strategy and its algorithmic methodology.
     */
    String getDescription();

    /**
     * Whether this strategy is currently operational and ready to process comparisons.
     */
    boolean isAvailable();

    /**
     * Analyzes similarity between a target document and a corpus of comparison sources.
     *
     * @param target  the submitted work being evaluated
     * @param corpus  the list of reference documents to compare against
     * @param options algorithm thresholds and parameters
     * @return structured match result containing spans, coverage, and limitations
     */
    StrategyMatchResult analyze(SimilarityTarget target, List<SimilaritySource> corpus, SimilarityOptions options);
}
