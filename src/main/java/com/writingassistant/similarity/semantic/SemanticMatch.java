package com.writingassistant.similarity.semantic;

import java.io.Serializable;
import java.util.Objects;

/**
 * A detected semantic match between a target document passage and a source document passage.
 */
public record SemanticMatch(
        String sourceId,
        int targetStart,
        int targetEnd,
        int sourceStart,
        int sourceEnd,
        String targetText,
        String sourceText,
        double score,
        String matchType
) implements Serializable, Comparable<SemanticMatch> {

    private static final long serialVersionUID = 1L;

    public SemanticMatch {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(targetText, "targetText must not be null");
        Objects.requireNonNull(sourceText, "sourceText must not be null");
        Objects.requireNonNull(matchType, "matchType must not be null");
        if (targetStart < 0 || targetEnd < targetStart) {
            throw new IllegalArgumentException("Invalid target offsets: " + targetStart + ".." + targetEnd);
        }
        if (sourceStart < 0 || sourceEnd < sourceStart) {
            throw new IllegalArgumentException("Invalid source offsets: " + sourceStart + ".." + sourceEnd);
        }
    }

    @Override
    public int compareTo(SemanticMatch o) {
        return Double.compare(o.score, this.score); // descending by similarity
    }
}
