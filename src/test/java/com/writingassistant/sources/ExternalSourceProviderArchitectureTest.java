package com.writingassistant.sources;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExternalSourceProviderArchitectureTest {

    @Test
    void externalScholarlyWorkBuildsAccuratelyWithAttribution() {
        ExternalScholarlyWork work = ExternalScholarlyWork.builder()
                .providerId("openalex")
                .externalId("W2741809807")
                .title("Attention Is All You Need")
                .authors(List.of("Vaswani, Ashish", "Shazeer, Noam"))
                .publicationYear(2017)
                .venueOrJournal("NeurIPS")
                .doi("10.48550/arXiv.1706.03762")
                .landingPageUrl("https://arxiv.org/abs/1706.03762")
                .pdfUrl("https://arxiv.org/pdf/1706.03762.pdf")
                .citationCount(95000)
                .hasFullText(true)
                .build();

        assertEquals("openalex", work.providerId());
        assertEquals("W2741809807", work.externalId());
        assertEquals("Attention Is All You Need", work.title());
        assertEquals(2, work.authors().size());
        assertEquals(2017, work.publicationYear());
        assertEquals("NeurIPS", work.venueOrJournal());
        assertTrue(work.hasFullText());
        assertNotNull(work.attribution());
        assertEquals("openalex", work.attribution().providerId());
        assertTrue(work.attribution().isOpenAccess());
    }

    @Test
    void externalSearchQueryEnforcesValidation() {
        assertThrows(IllegalArgumentException.class, () -> new ExternalSearchQuery("", "", "", null, null, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> new ExternalSearchQuery(null, null, null, null, null, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> new ExternalSearchQuery("ai", null, null, null, null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ExternalSearchQuery("ai", null, null, null, null, 101, 0));
        assertThrows(IllegalArgumentException.class, () -> new ExternalSearchQuery("ai", null, null, null, null, 10, -1));

        ExternalSearchQuery query = ExternalSearchQuery.of("deep learning", 25);
        assertEquals("deep learning", query.queryText());
        assertEquals(25, query.limit());
        assertEquals(0, query.offset());
    }

    @Test
    void doiNormalizerHandlesDiverseDoiFormats() {
        assertEquals("10.1038/s41586-020-2649-2",
                DoiNormalizer.normalize("https://doi.org/10.1038/s41586-020-2649-2").orElseThrow());
        assertEquals("10.1145/3372278.3390670",
                DoiNormalizer.normalize("http://dx.doi.org/10.1145/3372278.3390670").orElseThrow());
        assertEquals("10.1016/j.cell.2021.05.001",
                DoiNormalizer.normalize("doi: 10.1016/j.cell.2021.05.001.").orElseThrow());
        assertEquals("10.1000/182",
                DoiNormalizer.normalize("See reference 10.1000/182; for details.").orElseThrow());

        assertEquals("https://doi.org/10.1000/182", DoiNormalizer.toResolverUrl("10.1000/182"));

        assertTrue(DoiNormalizer.normalize(null).isEmpty());
        assertTrue(DoiNormalizer.normalize("not a doi").isEmpty());
    }

    @Test
    void providerExceptionCarriesMetadata() {
        ProviderException ex = new ProviderException("crossref", 429, "Rate limit exceeded");
        assertEquals("crossref", ex.getProviderId());
        assertEquals(429, ex.getStatusCode());
        assertEquals("Rate limit exceeded", ex.getMessage());
    }

    @Test
    void externalSourceRegistryManagesProviders() throws IOException {
        ExternalSourceRegistry registry = new ExternalSourceRegistry();
        assertEquals(0, registry.getAllProviders().size());

        ExternalSourceProvider provider1 = new ExternalSourceProvider() {
            @Override public String getProviderId() { return "openalex"; }
            @Override public String getDisplayName() { return "OpenAlex"; }
            @Override public boolean isAvailable() { return true; }
            @Override public ExternalSearchResult search(ExternalSearchQuery q) { return ExternalSearchResult.empty("openalex"); }
            @Override public Optional<ExternalScholarlyWork> findByDoi(String d) { return Optional.empty(); }
        };

        ExternalSourceProvider provider2 = new ExternalSourceProvider() {
            @Override public String getProviderId() { return "crossref"; }
            @Override public String getDisplayName() { return "Crossref"; }
            @Override public boolean isAvailable() { return false; } // Inactive/offline
            @Override public ExternalSearchResult search(ExternalSearchQuery q) { return ExternalSearchResult.empty("crossref"); }
            @Override public Optional<ExternalScholarlyWork> findByDoi(String d) { return Optional.empty(); }
        };

        registry.register(provider1);
        registry.register(provider2);

        assertEquals(2, registry.getAllProviders().size());
        assertEquals(1, registry.getAvailableProviders().size());
        assertTrue(registry.getProvider("OPENALEX").isPresent());
        assertTrue(registry.getProvider("Crossref").isPresent());
        assertFalse(registry.getProvider("unknown").isPresent());

        registry.unregister("crossref");
        assertEquals(1, registry.getAllProviders().size());
    }
}
