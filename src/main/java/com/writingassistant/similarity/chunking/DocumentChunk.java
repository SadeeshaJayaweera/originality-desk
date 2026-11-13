package com.writingassistant.similarity.chunking;

import java.io.Serializable;
import java.util.Objects;

/**
 * A discrete, traceable text chunk within a document, mapped to exact character offsets.
 */
public record DocumentChunk(
        String chunkId,
        String documentId,
        int chunkIndex,
        int startOffset,
        int endOffset,
        String text,
        int sentenceCount,
        int tokenCount
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public DocumentChunk {
        Objects.requireNonNull(chunkId, "chunkId must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(text, "text must not be null");
        if (chunkIndex < 0) throw new IllegalArgumentException("chunkIndex must be non-negative");
        if (startOffset < 0 || endOffset < startOffset) {
            throw new IllegalArgumentException("Invalid offsets: " + startOffset + ".." + endOffset);
        }
        if (sentenceCount < 0) throw new IllegalArgumentException("sentenceCount must be non-negative");
        if (tokenCount < 0) throw new IllegalArgumentException("tokenCount must be non-negative");
    }

    public static DocumentChunk of(String documentId, int chunkIndex, int startOffset, int endOffset, String text, int sentenceCount) {
        int tokens = text == null || text.isBlank() ? 0 : text.strip().split("\\s+").length;
        String id = documentId + ":chk:" + chunkIndex;
        return new DocumentChunk(id, documentId, chunkIndex, startOffset, endOffset, text, sentenceCount, tokens);
    }
}
