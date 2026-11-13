package com.writingassistant.sources;

import java.io.Serializable;

/**
 * Standard query parameters for searching academic literature across external source providers.
 */
public record ExternalSearchQuery(
        String queryText,
        String title,
        String author,
        Integer yearFrom,
        Integer yearTo,
        int limit,
        int offset
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public ExternalSearchQuery {
        if ((queryText == null || queryText.isBlank())
                && (title == null || title.isBlank())
                && (author == null || author.isBlank())) {
            throw new IllegalArgumentException("At least one search field (queryText, title, or author) must be provided");
        }
        if (limit <= 0 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset cannot be negative");
        }
    }

    public static ExternalSearchQuery of(String queryText) {
        return new ExternalSearchQuery(queryText, null, null, null, null, 10, 0);
    }

    public static ExternalSearchQuery of(String queryText, int limit) {
        return new ExternalSearchQuery(queryText, null, null, null, null, limit, 0);
    }
}
