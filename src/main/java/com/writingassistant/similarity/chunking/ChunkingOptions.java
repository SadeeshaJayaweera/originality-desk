package com.writingassistant.similarity.chunking;

import java.io.Serializable;

/**
 * Configuration options governing sentence-aware document chunking.
 */
public record ChunkingOptions(
        int targetTokens,
        int minTokens,
        int maxTokens,
        int overlapSentences,
        boolean preserveParagraphs
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_TARGET_TOKENS = 120;
    public static final int DEFAULT_MIN_TOKENS = 25;
    public static final int DEFAULT_MAX_TOKENS = 256;
    public static final int DEFAULT_OVERLAP_SENTENCES = 1;

    public ChunkingOptions {
        if (targetTokens <= 0) targetTokens = DEFAULT_TARGET_TOKENS;
        if (minTokens <= 0) minTokens = DEFAULT_MIN_TOKENS;
        if (maxTokens < targetTokens) maxTokens = Math.max(DEFAULT_MAX_TOKENS, targetTokens);
        if (overlapSentences < 0) overlapSentences = 0;
    }

    public static ChunkingOptions defaults() {
        return new ChunkingOptions(DEFAULT_TARGET_TOKENS, DEFAULT_MIN_TOKENS, DEFAULT_MAX_TOKENS, DEFAULT_OVERLAP_SENTENCES, true);
    }

    public static ChunkingOptions fineGrained() {
        return new ChunkingOptions(60, 15, 128, 1, false);
    }

    public static ChunkingOptions longPassage() {
        return new ChunkingOptions(200, 40, 384, 2, true);
    }
}
