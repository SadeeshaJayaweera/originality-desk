package com.writingassistant.core.citation;

import com.writingassistant.ai.GeminiException;

/**
 * Interface for citation-safe quote paraphrasing and bibliography formatting (Phase 2).
 */
public interface CitationEngine {

    /**
     * Paraphrases a source quote and formats alongside citations in the specified style.
     *
     * @param source SourceQuote containing quote text and metadata
     * @param style  Target citation standard (APA, MLA, Chicago)
     * @return CitedParaphrase combining rewritten text, in-text citation, and reference entry
     * @throws GeminiException if AI provider call fails or is unconfigured
     */
    CitedParaphrase paraphraseWithCitation(SourceQuote source, CitationStyle style) throws GeminiException;

    /**
     * Legacy signature supporting string quote + SourceCitation.
     */
    default ParaphraseResult paraphraseWithCitation(String sourceQuote, SourceCitation source, CitationStyle style)
            throws GeminiException {
        SourceQuote quoteObj = (source != null) ? source : new SourceCitation();
        quoteObj.setQuote(sourceQuote);
        CitedParaphrase cited = paraphraseWithCitation(quoteObj, style);
        return new ParaphraseResult(
                cited.getOriginalQuote(),
                cited.getParaphrasedText(),
                cited.getInTextCitation(),
                cited.getBibliographyEntry(),
                cited.getCitationStyle(),
                cited.getSource()
        );
    }

    /**
     * Formats an in-text parenthetical citation for a source.
     */
    default String formatInText(SourceCitation source, CitationStyle style) {
        return formatInText((SourceQuote) source, style);
    }

    /**
     * Formats an in-text parenthetical citation for a SourceQuote.
     */
    String formatInText(SourceQuote source, CitationStyle style);

    /**
     * Formats a complete bibliography entry for a source.
     */
    default String formatBibliographyEntry(SourceCitation source, CitationStyle style) {
        return formatBibliographyEntry((SourceQuote) source, style);
    }

    /**
     * Formats a complete bibliography entry for a SourceQuote.
     */
    String formatBibliographyEntry(SourceQuote source, CitationStyle style);
}
