package com.writingassistant.persistence.vector;

import java.io.Serializable;
import java.util.Objects;

/**
 * A matched document chunk retrieved via dense vector similarity search.
 */
public record VectorSearchResult(
        String chunkId,
        String documentId,
        int chunkIndex,
        int startOffset,
        int endOffset,
        String text,
        double score,
        String modelName
) implements Serializable, Comparable<VectorSearchResult> {

    private static final long serialVersionUID = 1L;

    public VectorSearchResult {
        Objects.requireNonNull(chunkId, "chunkId must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(text, "text must not be null");
    }

    @Override
    public int compareTo(VectorSearchResult o) {
        return Double.compare(o.score, this.score); // descending order
    }
}
