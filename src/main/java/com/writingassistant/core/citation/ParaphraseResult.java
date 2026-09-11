package com.writingassistant.core.citation;

/**
 * Result of paraphrasing a source quote with alongside citation formatting.
 * Extends CitedParaphrase for backward compatibility.
 */
public class ParaphraseResult extends CitedParaphrase {

    public ParaphraseResult(String originalQuote, String paraphrasedText, String inTextCitation,
                            String bibliographyEntry, CitationStyle citationStyle) {
        super(originalQuote, paraphrasedText, inTextCitation, bibliographyEntry, citationStyle, null);
    }

    public ParaphraseResult(String originalQuote, String paraphrasedText, String inTextCitation,
                            String bibliographyEntry, CitationStyle citationStyle, SourceQuote source) {
        super(originalQuote, paraphrasedText, inTextCitation, bibliographyEntry, citationStyle, source);
    }
}
