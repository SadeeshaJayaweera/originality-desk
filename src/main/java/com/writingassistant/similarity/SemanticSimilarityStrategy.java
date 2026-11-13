package com.writingassistant.similarity;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Strategy contract for semantic vector similarity analysis powered by neural embeddings.
 * Lays the architectural foundation for ONNX Runtime Sentence Transformers and pgvector integration.
 */
public class SemanticSimilarityStrategy implements SimilarityStrategy {

    public static final String STRATEGY_NAME = "semantic-embeddings";
    private final boolean available;
    private final String modelName;

    public SemanticSimilarityStrategy() {
        this(false, "none");
    }

    public SemanticSimilarityStrategy(boolean available, String modelName) {
        this.available = available;
        this.modelName = modelName != null ? modelName : "none";
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
        return available;
    }

    @Override
    public StrategyMatchResult analyze(SimilarityTarget target, List<SimilaritySource> corpus, SimilarityOptions options) {
        Objects.requireNonNull(target, "target must not be null");
        if (!available) {
            return new StrategyMatchResult(
                    SimilarityStrategyType.SEMANTIC,
                    STRATEGY_NAME,
                    List.of(),
                    0.0,
                    List.of("Semantic embedding engine is not configured; vector analysis disabled."),
                    Map.of("status", "NOT_CONFIGURED", "model", modelName)
            );
        }

        // When enabled in subsequent PRs, vector similarity retrieval executes here
        return StrategyMatchResult.empty(SimilarityStrategyType.SEMANTIC, STRATEGY_NAME);
    }
}
