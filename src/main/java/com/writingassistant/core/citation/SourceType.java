package com.writingassistant.core.citation;

/**
 * Types of cited academic and professional sources.
 */
public enum SourceType {
    BOOK("Book"),
    JOURNAL("Journal Article"),
    WEBSITE("Website / Webpage"),
    CONFERENCE("Conference Proceedings"),
    OTHER("General Source");

    private final String displayName;

    SourceType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static SourceType fromString(String type) {
        if (type == null || type.isBlank()) {
            return OTHER;
        }
        try {
            return SourceType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }
}
