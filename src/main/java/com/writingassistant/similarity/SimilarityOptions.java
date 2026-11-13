package com.writingassistant.similarity;

import java.io.Serializable;
import java.util.Map;

/**
 * Execution parameters and threshold options for similarity analysis strategies.
 */
public record SimilarityOptions(
        int shingleWords,
        boolean includeNear,
        double minScoreThreshold,
        int maxMatches,
        Map<String, Object> parameters
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public SimilarityOptions {
        if (shingleWords <= 0) shingleWords = 8;
        if (maxMatches <= 0) maxMatches = 200;
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    public static SimilarityOptions defaults() {
        return new SimilarityOptions(8, true, 0.2, 200, Map.of());
    }

    public static SimilarityOptions quick() {
        return new SimilarityOptions(12, false, 0.5, 200, Map.of());
    }

    public static SimilarityOptions standard() {
        return new SimilarityOptions(8, true, 0.2, 200, Map.of());
    }

    public static SimilarityOptions referenceAware() {
        return new SimilarityOptions(6, true, 0.2, 200, Map.of("referenceAware", true));
    }
}
