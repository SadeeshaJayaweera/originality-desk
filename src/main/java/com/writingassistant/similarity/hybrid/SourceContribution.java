package com.writingassistant.similarity.hybrid;

import java.io.Serializable;
import java.util.Objects;

/**
 * Breakdown of an individual reference or external scholarly source's overlap contribution.
 */
public record SourceContribution(
        String sourceId,
        String sourceTitle,
        String sourceType,
        double exactOverlapRatio,
        double nearOverlapRatio,
        double semanticOverlapRatio,
        double totalUnionRatio,
        int matchedSpansCount
) implements Serializable, Comparable<SourceContribution> {

    private static final long serialVersionUID = 1L;

    public SourceContribution {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(sourceTitle, "sourceTitle must not be null");
        Objects.requireNonNull(sourceType, "sourceType must not be null");
    }

    @Override
    public int compareTo(SourceContribution o) {
        return Double.compare(o.totalUnionRatio, this.totalUnionRatio); // descending by contribution
    }
}
