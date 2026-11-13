package com.writingassistant.similarity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * High-level similarity engine coordinator dispatching across decoupled strategies,
 * aggregating match spans, and calculating decomposed, non-inflated coverage metrics.
 */
public class CompositeSimilarityEngine {

    private final SimilarityStrategyRegistry registry;

    public CompositeSimilarityEngine() {
        this(SimilarityStrategyRegistry.createDefault());
    }

    public CompositeSimilarityEngine(SimilarityStrategyRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    public SimilarityStrategyRegistry getRegistry() {
        return registry;
    }

    /**
     * Executes the configured similarity strategies against the target and corpus.
     */
    public CompositeSimilarityResult analyze(SimilarityTarget target, List<SimilaritySource> corpus, SimilarityOptions options) {
        Objects.requireNonNull(target, "target must not be null");
        SimilarityOptions effectiveOptions = options != null ? options : SimilarityOptions.defaults();

        Map<SimilarityStrategyType, StrategyMatchResult> resultsByType = new LinkedHashMap<>();
        List<SimilaritySpan> aggregatedSpans = new ArrayList<>();
        List<String> limitations = new ArrayList<>();

        double exactCoverage = 0.0;
        double nearCoverage = 0.0;
        double semanticCoverage = 0.0;

        // 1. Exact Matching
        OptionalStrategy<ExactSimilarityStrategy> exactOpt = findStrategy(SimilarityStrategyType.EXACT);
        if (exactOpt.isPresent()) {
            StrategyMatchResult exactResult = exactOpt.strategy().analyze(target, corpus, effectiveOptions);
            resultsByType.put(SimilarityStrategyType.EXACT, exactResult);
            aggregatedSpans.addAll(exactResult.spans());
            limitations.addAll(exactResult.limitations());
            exactCoverage = exactResult.coverageRatio();
        }

        // 2. Near-Duplicate Matching (if enabled in options)
        if (effectiveOptions.includeNear()) {
            OptionalStrategy<NearDuplicateSimilarityStrategy> nearOpt = findStrategy(SimilarityStrategyType.NEAR_DUPLICATE);
            if (nearOpt.isPresent()) {
                StrategyMatchResult nearResult = nearOpt.strategy().analyze(target, corpus, effectiveOptions);
                resultsByType.put(SimilarityStrategyType.NEAR_DUPLICATE, nearResult);
                aggregatedSpans.addAll(nearResult.spans());
                limitations.addAll(nearResult.limitations());
                nearCoverage = nearResult.coverageRatio();
            }
        }

        // 3. Semantic Similarity (if available)
        OptionalStrategy<SemanticSimilarityStrategy> semanticOpt = findStrategy(SimilarityStrategyType.SEMANTIC);
        if (semanticOpt.isPresent() && semanticOpt.strategy().isAvailable()) {
            StrategyMatchResult semanticResult = semanticOpt.strategy().analyze(target, corpus, effectiveOptions);
            resultsByType.put(SimilarityStrategyType.SEMANTIC, semanticResult);
            aggregatedSpans.addAll(semanticResult.spans());
            limitations.addAll(semanticResult.limitations());
            semanticCoverage = semanticResult.coverageRatio();
        }

        // Sort aggregated spans by position in target, then source, then method
        aggregatedSpans.sort(Comparator.comparingInt(SimilaritySpan::targetStart)
                .thenComparing(SimilaritySpan::sourceId)
                .thenComparing(SimilaritySpan::method)
                .thenComparingInt(SimilaritySpan::sourceStart));

        // Deduplicate limitation notices
        List<String> distinctLimitations = limitations.stream().distinct().toList();

        // Calculate total coverage as union (bounded by exact + near + semantic, max 1.0)
        double totalCoverage = Math.min(1.0, exactCoverage + nearCoverage + semanticCoverage);

        return new CompositeSimilarityResult(
                exactCoverage,
                nearCoverage,
                semanticCoverage,
                totalCoverage,
                aggregatedSpans,
                distinctLimitations,
                resultsByType
        );
    }

    @SuppressWarnings("unchecked")
    private <T extends SimilarityStrategy> OptionalStrategy<T> findStrategy(SimilarityStrategyType type) {
        return registry.getStrategy(type)
                .map(s -> new OptionalStrategy<>((T) s))
                .orElse(new OptionalStrategy<>(null));
    }

    private record OptionalStrategy<T extends SimilarityStrategy>(T strategy) {
        boolean isPresent() { return strategy != null; }
    }

    public record CompositeSimilarityResult(
            double exactCoverage,
            double nearCoverage,
            double semanticCoverage,
            double totalCoverage,
            List<SimilaritySpan> aggregatedSpans,
            List<String> limitations,
            Map<SimilarityStrategyType, StrategyMatchResult> resultsByType
    ) {}
}
