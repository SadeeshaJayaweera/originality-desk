package com.writingassistant.similarity.semantic;

import com.writingassistant.similarity.SimilaritySpan;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Detailed output from semantic similarity analysis, including matches, highlights,
 * consolidated similarity spans, and interval-union coverage.
 */
public record SemanticAnalysisResult(
        List<SemanticMatch> matches,
        List<SemanticHighlight> highlights,
        List<SimilaritySpan> similaritySpans,
        double coverageRatio,
        double maxSimilarity,
        Map<String, Object> telemetry
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public SemanticAnalysisResult {
        matches = matches != null ? List.copyOf(matches) : List.of();
        highlights = highlights != null ? List.copyOf(highlights) : List.of();
        similaritySpans = similaritySpans != null ? List.copyOf(similaritySpans) : List.of();
        telemetry = telemetry != null ? Map.copyOf(telemetry) : Map.of();
    }

    public static SemanticAnalysisResult empty() {
        return new SemanticAnalysisResult(List.of(), List.of(), List.of(), 0.0, 0.0, Map.of("status", "EMPTY"));
    }
}
