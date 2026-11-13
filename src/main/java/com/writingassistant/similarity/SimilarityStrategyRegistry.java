package com.writingassistant.similarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry managing available and active similarity detection strategies.
 */
public class SimilarityStrategyRegistry {

    private final Map<SimilarityStrategyType, SimilarityStrategy> byType = new ConcurrentHashMap<>();
    private final Map<String, SimilarityStrategy> byName = new ConcurrentHashMap<>();

    public SimilarityStrategyRegistry() {
    }

    public static SimilarityStrategyRegistry createDefault() {
        SimilarityStrategyRegistry registry = new SimilarityStrategyRegistry();
        registry.register(new ExactSimilarityStrategy());
        registry.register(new NearDuplicateSimilarityStrategy());
        registry.register(new HeuristicSimilarityStrategy());
        registry.register(new SemanticSimilarityStrategy());
        return registry;
    }

    /**
     * Registers a new similarity strategy, updating lookups by type and by name.
     */
    public void register(SimilarityStrategy strategy) {
        Objects.requireNonNull(strategy, "strategy must not be null");
        byType.put(strategy.getType(), strategy);
        byName.put(strategy.getName(), strategy);
    }

    /**
     * Retrieves the primary strategy registered for the given strategy type.
     */
    public Optional<SimilarityStrategy> getStrategy(SimilarityStrategyType type) {
        return Optional.ofNullable(byType.get(type));
    }

    /**
     * Retrieves a strategy by its unique string identifier.
     */
    public Optional<SimilarityStrategy> getStrategy(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    /**
     * Returns an unmodifiable snapshot of all registered strategies.
     */
    public List<SimilarityStrategy> getAllStrategies() {
        return List.copyOf(byType.values());
    }

    /**
     * Returns an unmodifiable list of only currently active and operational strategies.
     */
    public List<SimilarityStrategy> getAvailableStrategies() {
        List<SimilarityStrategy> available = new ArrayList<>();
        for (SimilarityStrategy strategy : byType.values()) {
            if (strategy.isAvailable()) {
                available.add(strategy);
            }
        }
        return List.copyOf(available);
    }

    /**
     * Checks whether an operational strategy is registered for the specified type.
     */
    public boolean isAvailable(SimilarityStrategyType type) {
        SimilarityStrategy strategy = byType.get(type);
        return strategy != null && strategy.isAvailable();
    }
}
