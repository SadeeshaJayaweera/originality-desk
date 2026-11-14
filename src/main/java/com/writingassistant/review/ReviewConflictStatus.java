package com.writingassistant.review;

import java.io.Serializable;

/**
 * Multi-reviewer consensus and conflict state for a reviewable target.
 */
public enum ReviewConflictStatus implements Serializable {
    NO_REVIEWS("No reviewer decisions have been recorded"),
    UNANIMOUS("All reviewers are in consensus with identical decisions"),
    CONFLICT("Reviewers have submitted conflicting decisions (e.g. APPROVED vs FLAGGED)"),
    RESOLVED("Previous conflicting decisions have been adjudicated and resolved by a senior reviewer");

    private final String description;

    ReviewConflictStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isConflicted() {
        return this == CONFLICT;
    }
}
