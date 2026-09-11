package com.writingassistant.core.rewrite;

/**
 * Rewriting modes for stylistic and structural enhancements.
 */
public enum RewriteMode {
    TIGHTEN("Tighten", "Cut wordiness, eliminate filler, and improve conciseness while preserving meaning"),
    CLARITY("Clarity", "Fix confusing phrasing and awkward syntax; ideal for ESL writers while preserving intent"),
    FORMAL("Formal", "Shift tone to an academic and professional register"),
    CASUAL("Casual", "Shift tone to a conversational, natural, and engaging style"),
    GRAMMAR_FIX("Grammar Fix", "Correct grammatical, punctuation, and syntactical errors without altering voice");

    private final String displayName;
    private final String description;

    RewriteMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Helper to safely map string/legacy names (like GRAMMAR -> GRAMMAR_FIX).
     */
    public static RewriteMode fromString(String name) {
        if (name == null || name.isBlank()) {
            return CLARITY;
        }
        String normalized = name.trim().toUpperCase().replace(" ", "_").replace("-", "_");
        if ("GRAMMAR".equals(normalized)) {
            return GRAMMAR_FIX;
        }
        try {
            return RewriteMode.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return CLARITY;
        }
    }
}
