package com.writingassistant.ai.evaluation;

import java.io.Serializable;

/**
 * Objective statistical stylometry metrics measuring text entropy, sentence length variance,
 * burstiness, and lexical diversity.
 */
public record StylometryMetrics(
        double entropyEstimate,
        double burstiness,
        double typeTokenRatio,
        double hapaxLegomenaRatio,
        double averageSentenceLength,
        int sentenceCount,
        int wordCount
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public StylometryMetrics {
        entropyEstimate = clamp(entropyEstimate, 0.0, 20.0);
        burstiness = clamp(burstiness, 0.0, 10.0);
        typeTokenRatio = clamp(typeTokenRatio, 0.0, 1.0);
        hapaxLegomenaRatio = clamp(hapaxLegomenaRatio, 0.0, 1.0);
        averageSentenceLength = Math.max(0.0, averageSentenceLength);
        sentenceCount = Math.max(0, sentenceCount);
        wordCount = Math.max(0, wordCount);
    }

    private static double clamp(double val, double min, double max) {
        if (Double.isNaN(val)) return min;
        return Math.max(min, Math.min(max, val));
    }
}
