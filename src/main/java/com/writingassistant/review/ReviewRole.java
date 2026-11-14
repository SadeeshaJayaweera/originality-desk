package com.writingassistant.review;

import java.io.Serializable;

/**
 * Reviewer roles within institutional academic integrity review workflows.
 */
public enum ReviewRole implements Serializable {
    PEER_REVIEWER("Standard academic peer or teaching assistant reviewer", 1),
    SENIOR_REVIEWER("Course instructor, lead faculty, or senior reviewer", 2),
    INTEGRITY_OFFICER("Institutional academic integrity panel member", 3),
    ADMINISTRATOR("System administrator with authority to override or resolve escalated reviews", 4);

    private final String title;
    private final int hierarchyLevel;

    ReviewRole(String title, int hierarchyLevel) {
        this.title = title;
        this.hierarchyLevel = hierarchyLevel;
    }

    public String getTitle() {
        return title;
    }

    public int getHierarchyLevel() {
        return hierarchyLevel;
    }

    public boolean canResolveEscalation() {
        return hierarchyLevel >= SENIOR_REVIEWER.hierarchyLevel;
    }

    public static ReviewRole parse(String value) {
        if (value == null || value.isBlank()) {
            return PEER_REVIEWER;
        }
        try {
            return ReviewRole.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PEER_REVIEWER;
        }
    }
}
