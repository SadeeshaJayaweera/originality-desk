package com.writingassistant.analysis;

import java.io.Serializable;

/**
 * Configuration and resource limit parameters for OCR processing.
 */
public record OcrConfig(
        int maxPages,
        int dpi,
        long perPageTimeoutMs,
        long totalTimeoutMs,
        float minConfidenceThreshold,
        int maxConcurrentTasks
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_MAX_PAGES = 50;
    public static final int DEFAULT_DPI = 200;
    public static final long DEFAULT_PER_PAGE_TIMEOUT_MS = 30_000L;
    public static final long DEFAULT_TOTAL_TIMEOUT_MS = 120_000L;
    public static final float DEFAULT_MIN_CONFIDENCE = 0.50f;
    public static final int DEFAULT_MAX_CONCURRENT = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);

    public static final OcrConfig DEFAULT = new OcrConfig(
            DEFAULT_MAX_PAGES,
            DEFAULT_DPI,
            DEFAULT_PER_PAGE_TIMEOUT_MS,
            DEFAULT_TOTAL_TIMEOUT_MS,
            DEFAULT_MIN_CONFIDENCE,
            DEFAULT_MAX_CONCURRENT
    );

    public OcrConfig {
        if (maxPages <= 0) throw new IllegalArgumentException("maxPages must be positive");
        if (dpi < 72 || dpi > 600) throw new IllegalArgumentException("dpi must be between 72 and 600");
        if (perPageTimeoutMs <= 0) throw new IllegalArgumentException("perPageTimeoutMs must be positive");
        if (totalTimeoutMs < perPageTimeoutMs) throw new IllegalArgumentException("totalTimeoutMs must be >= perPageTimeoutMs");
        if (minConfidenceThreshold < 0.0f || minConfidenceThreshold > 1.0f) {
            throw new IllegalArgumentException("minConfidenceThreshold must be in range [0.0, 1.0]");
        }
        if (maxConcurrentTasks <= 0) throw new IllegalArgumentException("maxConcurrentTasks must be positive");
    }
}
