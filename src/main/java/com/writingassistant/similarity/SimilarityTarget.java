package com.writingassistant.similarity;

import java.io.Serializable;
import java.util.Objects;

/**
 * The submitted document or query passage being evaluated for textual or semantic similarity.
 */
public record SimilarityTarget(
        String documentId,
        String title,
        String text,
        int activeLimit
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public SimilarityTarget {
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(text, "text must not be null");
        if (activeLimit <= 0 || activeLimit > text.length()) {
            activeLimit = text.length();
        }
    }

    public static SimilarityTarget of(String documentId, String title, String text) {
        return new SimilarityTarget(documentId, title, text, text != null ? text.length() : 0);
    }
}
