package com.writingassistant.review;

import java.io.Serializable;

/**
 * Standard human reviewer decisions for similarity matches and document submissions.
 * Provides clear, auditable status values for academic integrity workflows.
 */
public enum ReviewDecision implements Serializable {
    PENDING("Awaiting initial reviewer evaluation", 0),
    APPROVED("Confirmed as legitimate academic use (proper citation, common knowledge, or acceptable phrasing)", 1),
    EXEMPTED("Exempted from similarity calculations (assignment prompt, institutional template, or bibliography)", 1),
    FLAGGED("Identified as suspicious or unattributed overlap requiring scrutiny", 2),
    ESCALATED("Referred to senior integrity officer, academic board, or department head for formal resolution", 3);

    private final String description;
    private final int severityRank;

    ReviewDecision(String description, int severityRank) {
        this.description = description;
        this.severityRank = severityRank;
    }

    public String getDescription() {
        return description;
    }

    public int getSeverityRank() {
        return severityRank;
    }

    public boolean isActionable() {
        return this == FLAGGED || this == ESCALATED;
    }

    public static ReviewDecision parse(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        try {
            return ReviewDecision.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown review decision: '" + value + "'. Valid decisions: APPROVED, FLAGGED, EXEMPTED, ESCALATED, PENDING");
        }
    }
}
