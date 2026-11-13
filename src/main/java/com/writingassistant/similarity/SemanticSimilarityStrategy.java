package com.writingassistant.similarity;

import com.writingassistant.similarity.onnx.EmbeddingEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Strategy contract for semantic vector similarity analysis powered by neural embeddings.
 * Supports ONNX Runtime Sentence Transformers and deterministic offline embeddings.
 */
public class SemanticSimilarityStrategy implements SimilarityStrategy {

    public static final String STRATEGY_NAME = "semantic-embeddings";
    private final EmbeddingEngine embeddingEngine;
    private final boolean explicitlyAvailable;
    private final String modelName;

    public SemanticSimilarityStrategy() {
        this((EmbeddingEngine) null);
    }

    public SemanticSimilarityStrategy(EmbeddingEngine embeddingEngine) {
        this.embeddingEngine = embeddingEngine;
        this.explicitlyAvailable = embeddingEngine != null && embeddingEngine.isAvailable();
        this.modelName = embeddingEngine != null ? embeddingEngine.getModelName() : "none";
    }

    public SemanticSimilarityStrategy(boolean available, String modelName) {
        this.embeddingEngine = null;
        this.explicitlyAvailable = available;
        this.modelName = modelName != null ? modelName : "none";
    }

    public Optional<EmbeddingEngine> getEmbeddingEngine() {
        return Optional.ofNullable(embeddingEngine);
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
        return explicitlyAvailable || (embeddingEngine != null && embeddingEngine.isAvailable());
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

        if (embeddingEngine == null) {
            return new StrategyMatchResult(
                    SimilarityStrategyType.SEMANTIC,
                    STRATEGY_NAME,
                    List.of(),
                    0.0,
                    List.of("Embedding engine instance is not available for inference."),
                    Map.of("status", "UNAVAILABLE", "model", modelName)
            );
        }

        float[] targetVector = embeddingEngine.embed(target.text());
        List<SimilaritySpan> spans = new ArrayList<>();
        double maxSimilarity = 0.0;
        double minThreshold = options != null ? options.minScoreThreshold() : 0.65;

        for (SimilaritySource source : corpus) {
            if (source.text() == null || source.text().isBlank()) continue;
            float[] sourceVector = embeddingEngine.embed(source.text());
            double cosine = embeddingEngine.similarity(targetVector, sourceVector);

            if (cosine > maxSimilarity) {
                maxSimilarity = cosine;
            }

            if (cosine >= minThreshold) {
                int targetLen = target.text().length();
                int sourceLen = source.text().length();
                String targetExcerpt = target.text().substring(0, Math.min(targetLen, 200));
                String sourceExcerpt = source.text().substring(0, Math.min(sourceLen, 200));

                spans.add(new SimilaritySpan(
                        source.sourceId(),
                        0,
                        targetLen,
                        0,
                        sourceLen,
                        "SEMANTIC",
                        cosine,
                        targetExcerpt,
                        sourceExcerpt
                ));
            }
        }

        Map<String, Object> telemetry = new HashMap<>();
        telemetry.put("status", "SUCCESS");
        telemetry.put("model", modelName);
        telemetry.put("dimensions", embeddingEngine.getDimensions());
        telemetry.put("corpus_size", corpus.size());
        telemetry.put("max_similarity", maxSimilarity);

        return new StrategyMatchResult(
                SimilarityStrategyType.SEMANTIC,
                STRATEGY_NAME,
                spans,
                maxSimilarity,
                List.of(),
                telemetry
        );
    }
}
