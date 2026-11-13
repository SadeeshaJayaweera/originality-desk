package com.writingassistant.sources;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Standardized canonical representation of an academic publication, manuscript,
 * or research artifact retrieved from external scholarly data sources.
 */
public record ExternalScholarlyWork(
        String providerId,
        String externalId,
        String title,
        List<String> authors,
        Integer publicationYear,
        String venueOrJournal,
        String doi,
        String landingPageUrl,
        String pdfUrl,
        String abstractText,
        int citationCount,
        boolean hasFullText,
        SourceAttribution attribution
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public ExternalScholarlyWork {
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(externalId, "externalId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        authors = authors == null ? List.of() : List.copyOf(authors);
        if (citationCount < 0) citationCount = 0;
        if (attribution == null) {
            attribution = SourceAttribution.of(providerId, venueOrJournal, landingPageUrl, null, pdfUrl != null);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String providerId;
        private String externalId;
        private String title;
        private List<String> authors = List.of();
        private Integer publicationYear;
        private String venueOrJournal;
        private String doi;
        private String landingPageUrl;
        private String pdfUrl;
        private String abstractText;
        private int citationCount;
        private boolean hasFullText;
        private SourceAttribution attribution;

        public Builder providerId(String providerId) { this.providerId = providerId; return this; }
        public Builder externalId(String externalId) { this.externalId = externalId; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder authors(List<String> authors) { this.authors = authors; return this; }
        public Builder publicationYear(Integer year) { this.publicationYear = year; return this; }
        public Builder venueOrJournal(String venue) { this.venueOrJournal = venue; return this; }
        public Builder doi(String doi) { this.doi = doi; return this; }
        public Builder landingPageUrl(String url) { this.landingPageUrl = url; return this; }
        public Builder pdfUrl(String url) { this.pdfUrl = url; return this; }
        public Builder abstractText(String text) { this.abstractText = text; return this; }
        public Builder citationCount(int count) { this.citationCount = count; return this; }
        public Builder hasFullText(boolean hasFullText) { this.hasFullText = hasFullText; return this; }
        public Builder attribution(SourceAttribution attribution) { this.attribution = attribution; return this; }

        public ExternalScholarlyWork build() {
            return new ExternalScholarlyWork(
                    providerId, externalId, title, authors, publicationYear, venueOrJournal,
                    doi, landingPageUrl, pdfUrl, abstractText, citationCount, hasFullText, attribution
            );
        }
    }
}
