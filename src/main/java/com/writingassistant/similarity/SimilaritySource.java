package com.writingassistant.similarity;

import java.io.Serializable;
import java.util.Objects;

/**
 * A reference document or candidate comparison text in the similarity evaluation corpus.
 */
public record SimilaritySource(
        String sourceId,
        String title,
        String text,
        String sourceType,
        int activeLimit
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public SimilaritySource {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(text, "text must not be null");
        if (sourceType == null || sourceType.isBlank()) sourceType = "REFERENCE";
        if (activeLimit <= 0 || activeLimit > text.length()) {
            activeLimit = text.length();
        }
    }

    public static SimilaritySource of(String sourceId, String title, String text, String sourceType) {
        return new SimilaritySource(sourceId, title, text, sourceType, text != null ? text.length() : 0);
    }
}
