package com.writingassistant.jobs;

import java.io.Serializable;
import java.util.Random;

/**
 * Calculates exponential backoff with jitter for transient job failures.
 */
public class BackoffPolicy implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final long DEFAULT_BASE_DELAY_MILLIS = 1000L;
    public static final long DEFAULT_MAX_DELAY_MILLIS = 300_000L; // 5 minutes
    public static final double DEFAULT_MULTIPLIER = 2.0;

    private final long baseDelayMillis;
    private final long maxDelayMillis;
    private final double multiplier;
    private final transient Random random;

    public BackoffPolicy() {
        this(DEFAULT_BASE_DELAY_MILLIS, DEFAULT_MAX_DELAY_MILLIS, DEFAULT_MULTIPLIER);
    }

    public BackoffPolicy(long baseDelayMillis, long maxDelayMillis, double multiplier) {
        if (baseDelayMillis <= 0) throw new IllegalArgumentException("baseDelayMillis must be > 0");
        if (maxDelayMillis < baseDelayMillis) throw new IllegalArgumentException("maxDelayMillis must be >= baseDelayMillis");
        if (multiplier < 1.0) throw new IllegalArgumentException("multiplier must be >= 1.0");

        this.baseDelayMillis = baseDelayMillis;
        this.maxDelayMillis = maxDelayMillis;
        this.multiplier = multiplier;
        this.random = new Random();
    }

    public static BackoffPolicy defaultPolicy() {
        return new BackoffPolicy();
    }

    /**
     * Computes delay in milliseconds for the given retry attempt number (1-indexed).
     *
     * @param attempt 1 for the first retry, 2 for the second, etc.
     * @return delay in milliseconds including slight jitter.
     */
    public long computeDelay(int attempt) {
        if (attempt <= 0) return 0L;

        // Exponential term: baseDelay * multiplier^(attempt - 1)
        double expDelay = baseDelayMillis * Math.pow(multiplier, attempt - 1);
        long cappedDelay = (long) Math.min(maxDelayMillis, expDelay);

        // Add 10% jitter to prevent thundering herd
        long jitter = (long) (cappedDelay * 0.10 * (random != null ? random.nextDouble() : 0.05));
        return Math.min(maxDelayMillis, cappedDelay + jitter);
    }

    /**
     * Computes the epoch millisecond when the job should next become available.
     */
    public long nextAvailableAt(int attempt) {
        return System.currentTimeMillis() + computeDelay(attempt);
    }

    public long getBaseDelayMillis() {
        return baseDelayMillis;
    }

    public long getMaxDelayMillis() {
        return maxDelayMillis;
    }
}
