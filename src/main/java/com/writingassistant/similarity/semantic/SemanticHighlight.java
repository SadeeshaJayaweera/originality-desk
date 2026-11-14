package com.writingassistant.similarity.semantic;

import java.io.Serializable;
import java.util.Objects;

/**
 * Coordinate interval for rendering semantic paraphrase highlights in the UI.
 */
public record SemanticHighlight(
        int startOffset,
        int endOffset,
        double confidence,
        String sourceId,
        String label,
        String matchedText
) implements Serializable, Comparable<SemanticHighlight> {

    private static final long serialVersionUID = 1L;

    public SemanticHighlight {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(label, "label must not be null");
        Objects.requireNonNull(matchedText, "matchedText must not be null");
        if (startOffset < 0 || endOffset < startOffset) {
            throw new IllegalArgumentException("Invalid highlight offsets: " + startOffset + ".." + endOffset);
        }
    }

    public int length() {
        return endOffset - startOffset;
    }

    @Override
    public int compareTo(SemanticHighlight o) {
        int cmp = Integer.compare(this.startOffset, o.startOffset);
        if (cmp != 0) return cmp;
        return Integer.compare(this.endOffset, o.endOffset);
    }
}
