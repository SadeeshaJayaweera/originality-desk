package com.writingassistant.similarity;

import java.io.Serializable;
import java.util.Objects;

/**
 * A detected overlapping or semantically matched passage interval with coordinates
 * mapping both submitted text and source document.
 */
public record SimilaritySpan(
        String sourceId,
        int targetStart,
        int targetEnd,
        int sourceStart,
        int sourceEnd,
        String method,
        double score,
        String targetPassage,
        String sourcePassage
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public SimilaritySpan {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(method, "method must not be null");
        if (targetStart < 0 || targetEnd < targetStart) {
            throw new IllegalArgumentException("Invalid target coordinates: " + targetStart + ".." + targetEnd);
        }
        if (sourceStart < 0 || sourceEnd < sourceStart) {
            throw new IllegalArgumentException("Invalid source coordinates: " + sourceStart + ".." + sourceEnd);
        }
    }
}
