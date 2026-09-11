package com.writingassistant.core.similarity;

/**
 * Risk levels indicating closeness between a paraphrase and the source text.
 */
public enum SimilarityRisk {
    LOW("Low Risk", "Passage is substantially restructured with minimal textual overlap."),
    MODERATE("Moderate Risk", "Noticeable shared phrasing or sequence structure. Revision recommended."),
    HIGH("High Risk", "High verbatim overlap or close structural mirroring. Direct quotation or thorough rewrite needed.");

    private final String label;
    private final String advice;

    SimilarityRisk(String label, String advice) {
        this.label = label;
        this.advice = advice;
    }

    public String getLabel() {
        return label;
    }

    public String getAdvice() {
        return advice;
    }
}
