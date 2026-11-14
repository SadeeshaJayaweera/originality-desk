package com.writingassistant.similarity.hybrid;

import java.io.Serializable;

/**
 * Objective, decomposed textual and semantic overlap metrics without conflation or double-counting.
 * All ratios are in range [0.0, 1.0].
 */
public record DecomposedOverlapMetrics(
        double exactCoverageRatio,
        double nearDuplicateCoverageRatio,
        double semanticCoverageRatio,
        double quotedCoverageRatio,
        double citedCoverageRatio,
        double totalUnionRatio,
        double netUnattributedRatio,
        int totalCharacters,
        int totalWords
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public DecomposedOverlapMetrics {
        exactCoverageRatio = clamp(exactCoverageRatio);
        nearDuplicateCoverageRatio = clamp(nearDuplicateCoverageRatio);
        semanticCoverageRatio = clamp(semanticCoverageRatio);
        quotedCoverageRatio = clamp(quotedCoverageRatio);
        citedCoverageRatio = clamp(citedCoverageRatio);
        totalUnionRatio = clamp(totalUnionRatio);
        netUnattributedRatio = clamp(netUnattributedRatio);
    }

    private static double clamp(double val) {
        if (Double.isNaN(val) || val < 0.0) return 0.0;
        return Math.min(1.0, val);
    }
}
