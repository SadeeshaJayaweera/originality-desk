package com.writingassistant.similarity;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Standardized result envelope returned by a SimilarityStrategy execution.
 */
public record StrategyMatchResult(
        SimilarityStrategyType strategyType,
        String strategyName,
        List<SimilaritySpan> spans,
        double coverageRatio,
        List<String> limitations,
        Map<String, Object> telemetry
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public StrategyMatchResult {
        Objects.requireNonNull(strategyType, "strategyType must not be null");
        Objects.requireNonNull(strategyName, "strategyName must not be null");
        spans = spans == null ? List.of() : List.copyOf(spans);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
        telemetry = telemetry == null ? Map.of() : Map.copyOf(telemetry);
    }

    public static StrategyMatchResult empty(SimilarityStrategyType type, String name) {
        return new StrategyMatchResult(type, name, List.of(), 0.0, List.of(), Map.of());
    }
}
