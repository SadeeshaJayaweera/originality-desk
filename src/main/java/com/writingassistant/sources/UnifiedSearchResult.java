package com.writingassistant.sources;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Result collection of a federated external scholarly search across multiple providers.
 */
public record UnifiedSearchResult(
        List<ExternalScholarlyWork> works,
        Map<String, Integer> resultsPerProvider,
        List<String> failedProviders,
        long totalEstimatedResults,
        long durationMs
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public UnifiedSearchResult {
        works = works == null ? List.of() : List.copyOf(works);
        resultsPerProvider = resultsPerProvider == null ? Map.of() : Map.copyOf(resultsPerProvider);
        failedProviders = failedProviders == null ? List.of() : List.copyOf(failedProviders);
        if (totalEstimatedResults < 0) totalEstimatedResults = 0;
        if (durationMs < 0) durationMs = 0;
    }

    public static UnifiedSearchResult empty() {
        return new UnifiedSearchResult(List.of(), Map.of(), List.of(), 0L, 0L);
    }
}
