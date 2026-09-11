package com.writingassistant.core.citation;

/**
 * Citation metadata representation for a source.
 * Extends SourceQuote for comprehensive metadata support.
 */
public class SourceCitation extends SourceQuote {

    public SourceCitation() {
        super();
    }

    public SourceCitation(String author, String year, String title, String publication) {
        super(null, author, year, title, publication);
    }

    public SourceCitation(String quote, String author, String year, String title, String publication) {
        super(quote, author, year, title, publication);
    }

    public SourceCitation(String quote, String author, String year, String title, String publication,
                          String pageNumber, String urlOrDoi, SourceType sourceType) {
        super(quote, author, year, title, publication, pageNumber, urlOrDoi, sourceType);
    }

    public static SourceCitation fromSourceQuote(SourceQuote quote) {
        if (quote == null) {
            return null;
        }
        if (quote instanceof SourceCitation) {
            return (SourceCitation) quote;
        }
        SourceCitation citation = new SourceCitation(
                quote.getQuote(), quote.getAuthor(), quote.getYear(), quote.getTitle(), quote.getPublication(),
                quote.getPageNumber(), quote.getUrlOrDoi(), quote.getSourceType());
        citation.setId(quote.getId());
        citation.setVolume(quote.getVolume());
        citation.setIssue(quote.getIssue());
        return citation;
    }
}
