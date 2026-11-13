package com.writingassistant.similarity.chunking;

import java.util.List;

/**
 * Strategy interface for decomposing document text into discrete, traceable chunks.
 */
public interface ChunkingStrategy {

    /**
     * Splits document text into traceable chunks.
     *
     * @param documentId unique document identifier
     * @param text       full document text
     * @param options    chunking parameters
     * @return ordered list of document chunks
     */
    List<DocumentChunk> chunk(String documentId, String text, ChunkingOptions options);
}
