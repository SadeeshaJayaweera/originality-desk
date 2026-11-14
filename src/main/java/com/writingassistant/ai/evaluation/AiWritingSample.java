package com.writingassistant.ai.evaluation;

import java.io.Serializable;
import java.util.Objects;

/**
 * An annotated benchmark sample representing a writing passage categorized by authorship source.
 */
public record AiWritingSample(
        String sampleId,
        String category,
        String title,
        String text,
        StylometryMetrics metrics
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String CATEGORY_HUMAN_NATIVE = "HUMAN_NATIVE";
    public static final String CATEGORY_HUMAN_ESL = "HUMAN_ESL";
    public static final String CATEGORY_AI_GENERATED = "AI_GENERATED";
    public static final String CATEGORY_AI_PARAPHRASED = "AI_PARAPHRASED";

    public AiWritingSample {
        Objects.requireNonNull(sampleId, "sampleId must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(text, "text must not be null");
        if (metrics == null) {
            metrics = PerplexityMetricsCalculator.calculate(text);
        }
    }

    public static AiWritingSample of(String sampleId, String category, String title, String text) {
        return new AiWritingSample(sampleId, category, title, text, PerplexityMetricsCalculator.calculate(text));
    }
}
