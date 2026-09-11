package com.writingassistant.core.citation;

import com.writingassistant.ai.GeminiClient;
import com.writingassistant.ai.GeminiException;

/**
 * Concrete implementation of CitationEngine combining AI paraphrasing and deterministic formatting.
 */
public class DefaultCitationEngine implements CitationEngine {

    private final QuoteParaphraser quoteParaphraser;

    public DefaultCitationEngine() {
        this(new QuoteParaphraser());
    }

    public DefaultCitationEngine(GeminiClient geminiClient) {
        this(new QuoteParaphraser(geminiClient));
    }

    public DefaultCitationEngine(QuoteParaphraser quoteParaphraser) {
        this.quoteParaphraser = (quoteParaphraser != null) ? quoteParaphraser : new QuoteParaphraser();
    }

    @Override
    public CitedParaphrase paraphraseWithCitation(SourceQuote source, CitationStyle style) throws GeminiException {
        if (source == null) {
            return new CitedParaphrase("", "", "", "", style, null);
        }

        CitationStyle targetStyle = (style != null) ? style : CitationStyle.APA;

        // 1. Generate genuinely restructured paraphrase
        String paraphrasedText = quoteParaphraser.paraphrase(source);

        // 2. Format in-text citation
        String inTextCitation = CitationFormatter.formatInText(source, targetStyle);

        // 3. Format full bibliography entry
        String bibliographyEntry = CitationFormatter.formatBibliographyEntry(source, targetStyle);

        return new CitedParaphrase(
                source.getQuote(),
                paraphrasedText,
                inTextCitation,
                bibliographyEntry,
                targetStyle,
                source
        );
    }

    @Override
    public String formatInText(SourceQuote source, CitationStyle style) {
        return CitationFormatter.formatInText(source, style);
    }

    @Override
    public String formatBibliographyEntry(SourceQuote source, CitationStyle style) {
        return CitationFormatter.formatBibliographyEntry(source, style);
    }

    public QuoteParaphraser getQuoteParaphraser() {
        return quoteParaphraser;
    }
}
