package com.writingassistant.sources;

import java.io.IOException;
import java.util.Optional;

/**
 * Pluggable strategy interface for querying external scholarly databases and literature indexes.
 */
public interface ExternalSourceProvider {

    /**
     * Unique system identifier (e.g. "openalex", "crossref", "semantic_scholar", "core").
     */
    String getProviderId();

    /**
     * User-facing display title (e.g. "OpenAlex", "Crossref").
     */
    String getDisplayName();

    /**
     * Returns true if provider is active and operational.
     */
    boolean isAvailable();

    /**
     * Queries provider for works matching criteria.
     *
     * @param query search query parameters
     * @return structured search results
     * @throws IOException on connection failure, rate limiting, or upstream error
     */
    ExternalSearchResult search(ExternalSearchQuery query) throws IOException;

    /**
     * Resolves an academic work directly by its Digital Object Identifier (DOI).
     *
     * @param doi raw or URL-formatted DOI
     * @return optional containing matching work, or empty if not found
     * @throws IOException on communication failure
     */
    Optional<ExternalScholarlyWork> findByDoi(String doi) throws IOException;
}
