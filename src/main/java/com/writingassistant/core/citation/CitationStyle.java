package com.writingassistant.core.citation;

/**
 * Supported academic and professional citation formatting styles.
 */
public enum CitationStyle {
    APA("APA 7th Edition"),
    MLA("MLA 9th Edition"),
    CHICAGO("Chicago 17th Edition (Author-Date)");

    private final String displayName;

    CitationStyle(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
