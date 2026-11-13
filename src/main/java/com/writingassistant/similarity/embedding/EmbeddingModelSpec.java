package com.writingassistant.similarity.embedding;

import java.io.Serializable;
import java.util.Objects;

/**
 * Technical specification and benchmark profile for a candidate sentence embedding model.
 */
public record EmbeddingModelSpec(
        String modelId,
        String displayName,
        int dimensions,
        int maxSequenceLength,
        double parameterCountMillions,
        long fp32SizeBytes,
        long int8SizeBytes,
        String license,
        boolean isMultilingual,
        double averageCpuLatencyMs,
        double mtebRetrievalScore,
        double paraphraseSeparationScore,
        String recommendedTier
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public EmbeddingModelSpec {
        Objects.requireNonNull(modelId, "modelId must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(license, "license must not be null");
        if (dimensions <= 0) throw new IllegalArgumentException("dimensions must be positive");
        if (maxSequenceLength <= 0) throw new IllegalArgumentException("maxSequenceLength must be positive");
    }

    /**
     * Pre-configured candidate: sentence-transformers/all-MiniLM-L6-v2
     */
    public static EmbeddingModelSpec allMiniLmL6V2() {
        return new EmbeddingModelSpec(
                "sentence-transformers/all-MiniLM-L6-v2",
                "all-MiniLM-L6-v2",
                384,
                256,
                22.7,
                90_000_000L,
                23_000_000L,
                "Apache-2.0",
                false,
                12.5,
                41.95,
                0.86,
                "LOCAL_CPU_DEFAULT"
        );
    }

    /**
     * Pre-configured candidate: intfloat/multilingual-e5-base
     */
    public static EmbeddingModelSpec multilingualE5Base() {
        return new EmbeddingModelSpec(
                "intfloat/multilingual-e5-base",
                "multilingual-e5-base",
                768,
                512,
                278.0,
                1_110_000_000L,
                280_000_000L,
                "MIT",
                true,
                82.0,
                51.30,
                0.89,
                "MULTILINGUAL_SECONDARY"
        );
    }

    /**
     * Pre-configured candidate: BAAI/bge-m3
     */
    public static EmbeddingModelSpec bgeM3() {
        return new EmbeddingModelSpec(
                "BAAI/bge-m3",
                "bge-m3",
                1024,
                8192,
                567.0,
                2_240_000_000L,
                570_000_000L,
                "MIT",
                true,
                245.0,
                55.20,
                0.92,
                "SERVER_LONG_CONTEXT"
        );
    }
}
