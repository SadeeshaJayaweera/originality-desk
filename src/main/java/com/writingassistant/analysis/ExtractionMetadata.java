package com.writingassistant.analysis;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable metadata describing the outcome of a document extraction process.
 */
public record ExtractionMetadata(
        String format,
        int characterCount,
        int wordCount,
        int pageCount,
        boolean isOcr,
        long parseDurationMs
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public ExtractionMetadata {
        Objects.requireNonNull(format, "format must not be null");
        if (characterCount < 0) throw new IllegalArgumentException("characterCount cannot be negative");
        if (wordCount < 0) throw new IllegalArgumentException("wordCount cannot be negative");
        if (pageCount < 0) throw new IllegalArgumentException("pageCount cannot be negative");
        if (parseDurationMs < 0) throw new IllegalArgumentException("parseDurationMs cannot be negative");
    }

    public static ExtractionMetadata of(String format, String text, int pageCount, boolean isOcr, long parseDurationMs) {
        int charCount = text == null ? 0 : text.length();
        int wordCount = computeWordCount(text);
        return new ExtractionMetadata(format, charCount, wordCount, pageCount, isOcr, parseDurationMs);
    }

    public static int computeWordCount(String text) {
        if (text == null || text.isBlank()) return 0;
        int count = 0;
        boolean inWord = false;
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                if (!inWord) {
                    count++;
                    inWord = true;
                }
            } else {
                inWord = false;
            }
        }
        return count;
    }
}
