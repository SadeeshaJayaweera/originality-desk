package com.writingassistant.sources;

import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable persistent evidence record capturing external scholarly provenance,
 * verified publisher links, licensing, and standardized citations (APA, IEEE, MLA, BibTeX).
 */
public record ExternalReferenceEvidence(
        String workspaceId,
        String documentId,
        String providerId,
        String externalId,
        String doi,
        String title,
        List<String> authors,
        Integer publicationYear,
        String venueOrPublisher,
        String landingPageUrl,
        String pdfUrl,
        String license,
        boolean isOpenAccess,
        int citationCount,
        String citationApa,
        String citationIeee,
        String citationMla,
        String citationBibtex,
        String persistentResolverUrl,
        String createdAt
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public ExternalReferenceEvidence {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        authors = authors == null ? List.of() : List.copyOf(authors);
        if (citationCount < 0) citationCount = 0;
        if (createdAt == null) createdAt = Instant.now().toString();
        if (persistentResolverUrl == null || persistentResolverUrl.isBlank()) {
            if (doi != null && !doi.isBlank()) {
                persistentResolverUrl = DoiNormalizer.toResolverUrl(doi);
            } else if (landingPageUrl != null && !landingPageUrl.isBlank()) {
                persistentResolverUrl = landingPageUrl;
            } else {
                persistentResolverUrl = "";
            }
        }
    }

    /**
     * Constructs evidence directly from an ExternalScholarlyWork with auto-generated citations.
     */
    public static ExternalReferenceEvidence fromWork(String workspaceId, String documentId, ExternalScholarlyWork work) {
        Objects.requireNonNull(work, "work must not be null");
        String normDoi = work.doi() != null ? DoiNormalizer.normalize(work.doi()).orElse(work.doi()) : null;
        String apa = ScholarlyCitationFormatter.formatApa(work);
        String ieee = ScholarlyCitationFormatter.formatIeee(work);
        String mla = ScholarlyCitationFormatter.formatMla(work);
        String bibtex = ScholarlyCitationFormatter.formatBibtex(work);
        String resolver = ScholarlyCitationFormatter.getPersistentResolver(work);
        String license = work.attribution() != null ? work.attribution().license() : null;
        boolean openAccess = (work.attribution() != null && work.attribution().isOpenAccess()) || work.pdfUrl() != null;

        return new ExternalReferenceEvidence(
                workspaceId,
                documentId,
                work.providerId(),
                work.externalId(),
                normDoi,
                work.title(),
                work.authors(),
                work.publicationYear(),
                work.venueOrJournal(),
                work.landingPageUrl(),
                work.pdfUrl(),
                license,
                openAccess,
                work.citationCount(),
                apa,
                ieee,
                mla,
                bibtex,
                resolver,
                Instant.now().toString()
        );
    }

    /**
     * Converts this evidence record to a structured Map for API responses and persistence.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("workspaceId", workspaceId);
        map.put("documentId", documentId);
        map.put("providerId", providerId);
        map.put("externalId", externalId);
        map.put("doi", doi);
        map.put("title", title);
        map.put("authors", authors);
        map.put("publicationYear", publicationYear);
        map.put("venueOrPublisher", venueOrPublisher);
        map.put("landingPageUrl", landingPageUrl);
        map.put("pdfUrl", pdfUrl);
        map.put("license", license);
        map.put("isOpenAccess", isOpenAccess);
        map.put("citationCount", citationCount);
        map.put("citationApa", citationApa);
        map.put("citationIeee", citationIeee);
        map.put("citationMla", citationMla);
        map.put("citationBibtex", citationBibtex);
        map.put("persistentResolverUrl", persistentResolverUrl);
        map.put("createdAt", createdAt);
        return map;
    }
}
