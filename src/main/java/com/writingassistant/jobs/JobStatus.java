package com.writingassistant.jobs;

import java.io.Serializable;

/**
 * Lifecycle states for background jobs.
 */
public enum JobStatus implements Serializable {
    QUEUED,
    RUNNING,
    RETRYING,
    COMPLETED,
    FAILED,
    CANCELLED,
    DEAD_LETTER;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == DEAD_LETTER;
    }

    public boolean isActive() {
        return this == QUEUED || this == RUNNING || this == RETRYING;
    }

    public static JobStatus parse(String value) {
        if (value == null || value.isBlank()) return QUEUED;
        try {
            return JobStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return QUEUED;
        }
    }
}
