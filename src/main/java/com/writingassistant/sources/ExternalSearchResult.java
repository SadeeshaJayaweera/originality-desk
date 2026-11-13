package com.writingassistant.sources;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Result collection returned by an external scholarly source provider.
 */
public record ExternalSearchResult(
        String providerId,
        List<ExternalScholarlyWork> works,
        long totalEstimatedResults,
        long durationMs
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public ExternalSearchResult {
        Objects.requireNonNull(providerId, "providerId must not be null");
        works = works == null ? List.of() : List.copyOf(works);
        if (totalEstimatedResults < 0) totalEstimatedResults = 0;
        if (durationMs < 0) durationMs = 0;
    }

    public static ExternalSearchResult empty(String providerId) {
        return new ExternalSearchResult(providerId, List.of(), 0L, 0L);
    }
}
