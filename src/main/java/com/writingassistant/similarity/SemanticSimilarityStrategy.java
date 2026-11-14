package com.writingassistant.similarity;

import com.writingassistant.similarity.onnx.EmbeddingEngine;
import com.writingassistant.similarity.semantic.SemanticAnalysisResult;
import com.writingassistant.similarity.semantic.SemanticSimilarityAnalyzer;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Strategy implementation for semantic vector similarity analysis powered by neural embeddings.
 * Utilizes sentence-aware chunking, batch vector inference, and interval-union coverage.
 */
public class SemanticSimilarityStrategy implements SimilarityStrategy {

    public static final String STRATEGY_NAME = "semantic-embeddings";

    private final SemanticSimilarityAnalyzer analyzer;
    private final boolean explicitlyAvailable;
    private final String modelName;

    public SemanticSimilarityStrategy() {
        this((SemanticSimilarityAnalyzer) null);
    }

    public SemanticSimilarityStrategy(EmbeddingEngine embeddingEngine) {
        this(embeddingEngine != null ? new SemanticSimilarityAnalyzer(embeddingEngine) : null);
    }

    public SemanticSimilarityStrategy(SemanticSimilarityAnalyzer analyzer) {
        this.analyzer = analyzer;
        this.explicitlyAvailable = analyzer != null && analyzer.getPipeline().getEmbeddingEngine().isAvailable();
        this.modelName = analyzer != null ? analyzer.getPipeline().getEmbeddingEngine().getModelName() : "none";
    }

    public SemanticSimilarityStrategy(boolean available, String modelName) {
        this.analyzer = null;
        this.explicitlyAvailable = available;
        this.modelName = modelName != null ? modelName : "none";
    }

    public Optional<SemanticSimilarityAnalyzer> getAnalyzer() {
        return Optional.ofNullable(analyzer);
    }

    public Optional<EmbeddingEngine> getEmbeddingEngine() {
        return analyzer != null ? Optional.of(analyzer.getPipeline().getEmbeddingEngine()) : Optional.empty();
    }

    @Override
    public SimilarityStrategyType getType() {
        return SimilarityStrategyType.SEMANTIC;
    }

    @Override
    public String getName() {
        return STRATEGY_NAME;
    }

    @Override
    public String getDescription() {
        return "Deep neural representation and vector cosine similarity via Sentence Transformers (" + modelName + ").";
    }

    @Override
    public boolean isAvailable() {
        return explicitlyAvailable || (analyzer != null && analyzer.getPipeline().getEmbeddingEngine().isAvailable());
    }

    @Override
    public StrategyMatchResult analyze(SimilarityTarget target, List<SimilaritySource> corpus, SimilarityOptions options) {
        Objects.requireNonNull(target, "target must not be null");
        if (!isAvailable()) {
            return new StrategyMatchResult(
                    SimilarityStrategyType.SEMANTIC,
                    STRATEGY_NAME,
                    List.of(),
                    0.0,
                    List.of("Semantic embedding engine is not configured; vector analysis disabled."),
                    Map.of("status", "NOT_CONFIGURED", "model", modelName)
            );
        }

        if (corpus == null || corpus.isEmpty() || target.text().isBlank()) {
            return StrategyMatchResult.empty(SimilarityStrategyType.SEMANTIC, STRATEGY_NAME);
        }

        if (analyzer == null) {
            return new StrategyMatchResult(
                    SimilarityStrategyType.SEMANTIC,
                    STRATEGY_NAME,
                    List.of(),
                    0.0,
                    List.of("Semantic analyzer instance is not configured."),
                    Map.of("status", "UNAVAILABLE", "model", modelName)
            );
        }

        SemanticAnalysisResult result = analyzer.analyze(target, corpus, options);

        return new StrategyMatchResult(
                SimilarityStrategyType.SEMANTIC,
                STRATEGY_NAME,
                result.similaritySpans(),
                result.coverageRatio(),
                List.of(),
                result.telemetry()
        );
    }
}
