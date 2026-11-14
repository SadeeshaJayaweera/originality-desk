package com.writingassistant.jobs;

import java.io.Serializable;

/**
 * Priority levels for background job execution.
 */
public enum JobPriority implements Serializable {
    LOW(1),
    NORMAL(2),
    HIGH(3),
    CRITICAL(4);

    private final int level;

    JobPriority(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public static JobPriority parse(String value) {
        if (value == null || value.isBlank()) return NORMAL;
        try {
            return JobPriority.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }
}
