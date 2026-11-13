package com.writingassistant.similarity.chunking;

import java.io.Serializable;
import java.util.Objects;

/**
 * A detected semantic match between a target document chunk and a source document chunk.
 */
public record ChunkMatch(
        EmbeddedChunk targetChunk,
        EmbeddedChunk sourceChunk,
        double score
) implements Serializable, Comparable<ChunkMatch> {

    private static final long serialVersionUID = 1L;

    public ChunkMatch {
        Objects.requireNonNull(targetChunk, "targetChunk must not be null");
        Objects.requireNonNull(sourceChunk, "sourceChunk must not be null");
    }

    @Override
    public int compareTo(ChunkMatch o) {
        return Double.compare(o.score, this.score); // descending order
    }
}
