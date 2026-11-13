package com.writingassistant.analysis;

import java.io.Serializable;
import java.util.Objects;

/**
 * Structured outcome of an OCR process, containing extracted text, quality metrics, and performance telemetry.
 */
public record OcrResult(
        String text,
        float confidence,
        int pagesProcessed,
        int characterCount,
        int wordCount,
        boolean acceptableQuality,
        long durationMs
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public OcrResult {
        Objects.requireNonNull(text, "text must not be null");
        if (confidence < 0.0f || confidence > 1.0f) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        if (pagesProcessed < 0) throw new IllegalArgumentException("pagesProcessed cannot be negative");
        if (characterCount < 0) throw new IllegalArgumentException("characterCount cannot be negative");
        if (wordCount < 0) throw new IllegalArgumentException("wordCount cannot be negative");
        if (durationMs < 0) throw new IllegalArgumentException("durationMs cannot be negative");
    }

    public static OcrResult of(String text, float confidence, int pagesProcessed, float minConfidence, long durationMs) {
        String safeText = text != null ? text.trim() : "";
        int charCount = safeText.length();
        int wordCount = ExtractionMetadata.computeWordCount(safeText);
        boolean acceptable = confidence >= minConfidence && wordCount > 0;
        return new OcrResult(safeText, confidence, pagesProcessed, charCount, wordCount, acceptable, durationMs);
    }
}
