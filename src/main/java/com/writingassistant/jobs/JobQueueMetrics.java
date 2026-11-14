package com.writingassistant.jobs;

import java.io.Serializable;

/**
 * Operational telemetry snapshot for background job queue health and capacity monitoring.
 */
public record JobQueueMetrics(
        int queuedCount,
        int runningCount,
        int retryingCount,
        int completedCount,
        int failedCount,
        int deadLetterCount,
        int staleRecoveredCount,
        long oldestQueuedAgeMillis
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public int totalPending() {
        return queuedCount + runningCount + retryingCount;
    }
}
