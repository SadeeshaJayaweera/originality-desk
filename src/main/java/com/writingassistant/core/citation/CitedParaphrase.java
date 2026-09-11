package com.writingassistant.core.citation;

/**
 * Encapsulates the complete result of a citation-safe paraphrase:
 * genuinely restructured text, in-text citation, and full bibliography entry.
 */
public class CitedParaphrase {

    private final String originalQuote;
    private final String paraphrasedText;
    private final String inTextCitation;
    private final String bibliographyEntry;
    private final CitationStyle citationStyle;
    private final SourceQuote source;

    public CitedParaphrase(String originalQuote, String paraphrasedText, String inTextCitation,
                           String bibliographyEntry, CitationStyle citationStyle, SourceQuote source) {
        this.originalQuote = originalQuote != null ? originalQuote : "";
        this.paraphrasedText = paraphrasedText != null ? paraphrasedText : "";
        this.inTextCitation = inTextCitation != null ? inTextCitation : "";
        this.bibliographyEntry = bibliographyEntry != null ? bibliographyEntry : "";
        this.citationStyle = citationStyle != null ? citationStyle : CitationStyle.APA;
        this.source = source;
    }

    public String getOriginalQuote() {
        return originalQuote;
    }

    public String getParaphrasedText() {
        return paraphrasedText;
    }

    public String getInTextCitation() {
        return inTextCitation;
    }

    public String getBibliographyEntry() {
        return bibliographyEntry;
    }

    public CitationStyle getCitationStyle() {
        return citationStyle;
    }

    public SourceQuote getSource() {
        return source;
    }

    public SourceQuote getSourceQuote() {
        return source;
    }

    /**
     * Returns the full formatted passage ready for insertion into a draft document:
     * e.g., "Paraphrased text here (Smith, 2023, p. 42)."
     */
    public String getFullFormattedPassage() {
        if (inTextCitation == null || inTextCitation.isBlank()) {
            return paraphrasedText;
        }
        if (paraphrasedText.endsWith(".")) {
            // Strip trailing period so in-text citation can be appended before the sentence terminal period
            String withoutPeriod = paraphrasedText.substring(0, paraphrasedText.length() - 1).trim();
            return withoutPeriod + " " + inTextCitation + ".";
        }
        return paraphrasedText + " " + inTextCitation;
    }

    @Override
    public String toString() {
        return "CitedParaphrase{" +
                "inTextCitation='" + inTextCitation + '\'' +
                ", citationStyle=" + citationStyle +
                '}';
    }
}
