package com.writingassistant.sources;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UnifiedExternalReferenceSearchTest {

    @Test
    void federatesSearchAcrossMultipleProvidersAndDeduplicatesByDoi() {
        ExternalSourceRegistry registry = new ExternalSourceRegistry();

        // Provider A: OpenAlex
        registry.register(new ExternalSourceProvider() {
            @Override public String getProviderId() { return "openalex"; }
            @Override public String getDisplayName() { return "OpenAlex"; }
            @Override public boolean isAvailable() { return true; }
            @Override public ExternalSearchResult search(ExternalSearchQuery q) {
                ExternalScholarlyWork workA = ExternalScholarlyWork.builder()
                        .providerId("openalex")
                        .externalId("W1")
                        .title("Deep Residual Learning for Image Recognition")
                        .authors(List.of("Kaiming He", "Xiangyu Zhang"))
                        .publicationYear(2016)
                        .doi("10.1109/CVPR.2016.90")
                        .citationCount(180000)
                        .hasFullText(false)
                        .build();
                return new ExternalSearchResult("openalex", List.of(workA), 100L, 10L);
            }
            @Override public Optional<ExternalScholarlyWork> findByDoi(String d) { return Optional.empty(); }
        });

        // Provider B: Semantic Scholar (same paper with DOI, but has full text PDF and more authors)
        registry.register(new ExternalSourceProvider() {
            @Override public String getProviderId() { return "semantic_scholar"; }
            @Override public String getDisplayName() { return "Semantic Scholar"; }
            @Override public boolean isAvailable() { return true; }
            @Override public ExternalSearchResult search(ExternalSearchQuery q) {
                ExternalScholarlyWork workB = ExternalScholarlyWork.builder()
                        .providerId("semantic_scholar")
                        .externalId("S1")
                        .title("Deep Residual Learning for Image Recognition")
                        .authors(List.of("Kaiming He", "Xiangyu Zhang", "Shaoqing Ren", "Jian Sun"))
                        .publicationYear(2016)
                        .doi("10.1109/cvpr.2016.90") // lowercase
                        .pdfUrl("https://arxiv.org/pdf/1512.03385.pdf")
                        .citationCount(190000)
                        .hasFullText(true)
                        .build();
                return new ExternalSearchResult("semantic_scholar", List.of(workB), 100L, 12L);
            }
            @Override public Optional<ExternalScholarlyWork> findByDoi(String d) { return Optional.empty(); }
        });

        UnifiedExternalReferenceSearch searchService = new UnifiedExternalReferenceSearch(registry);
        UnifiedSearchResult result = searchService.search(ExternalSearchQuery.of("residual learning", 10));

        assertEquals(0, result.failedProviders().size());
        assertEquals(2, result.resultsPerProvider().size());
        assertEquals(1, result.works().size(), "Duplicate paper with same DOI must be merged into 1 work");

        ExternalScholarlyWork merged = result.works().get(0);
        assertEquals("10.1109/CVPR.2016.90", merged.doi());
        assertEquals(4, merged.authors().size(), "Authors should be merged from both providers");
        assertEquals(190000, merged.citationCount(), "Max citation count should be chosen");
        assertTrue(merged.hasFullText(), "Full text flag should be true if either provider has it");
        assertEquals("https://arxiv.org/pdf/1512.03385.pdf", merged.pdfUrl());
    }

    @Test
    void ranksFullTextAndHigherCitationsFirst() {
        ExternalSourceRegistry registry = new ExternalSourceRegistry();

        registry.register(new ExternalSourceProvider() {
            @Override public String getProviderId() { return "provider1"; }
            @Override public String getDisplayName() { return "Provider 1"; }
            @Override public boolean isAvailable() { return true; }
            @Override public ExternalSearchResult search(ExternalSearchQuery q) {
                ExternalScholarlyWork w1 = ExternalScholarlyWork.builder()
                        .providerId("provider1").externalId("1").title("Abstract Only Low Citations")
                        .citationCount(5).hasFullText(false).build();
                ExternalScholarlyWork w2 = ExternalScholarlyWork.builder()
                        .providerId("provider1").externalId("2").title("Full Text Paper High Citations")
                        .citationCount(500).hasFullText(true).pdfUrl("https://oa.org/2.pdf").build();
                ExternalScholarlyWork w3 = ExternalScholarlyWork.builder()
                        .providerId("provider1").externalId("3").title("Full Text Paper Low Citations")
                        .citationCount(10).hasFullText(true).pdfUrl("https://oa.org/3.pdf").build();

                return new ExternalSearchResult("provider1", List.of(w1, w2, w3), 3L, 5L);
            }
            @Override public Optional<ExternalScholarlyWork> findByDoi(String d) { return Optional.empty(); }
        });

        UnifiedExternalReferenceSearch searchService = new UnifiedExternalReferenceSearch(registry);
        UnifiedSearchResult result = searchService.search(ExternalSearchQuery.of("test", 10));

        assertEquals(3, result.works().size());
        // w2: full text + 500 citations
        assertEquals("Full Text Paper High Citations", result.works().get(0).title());
        // w3: full text + 10 citations
        assertEquals("Full Text Paper Low Citations", result.works().get(1).title());
        // w1: no full text + 5 citations
        assertEquals("Abstract Only Low Citations", result.works().get(2).title());
    }

    @Test
    void toleratesProviderFailureGracefully() {
        ExternalSourceRegistry registry = new ExternalSourceRegistry();

        // Healthy provider
        registry.register(new ExternalSourceProvider() {
            @Override public String getProviderId() { return "good"; }
            @Override public String getDisplayName() { return "Good Provider"; }
            @Override public boolean isAvailable() { return true; }
            @Override public ExternalSearchResult search(ExternalSearchQuery q) {
                ExternalScholarlyWork w = ExternalScholarlyWork.builder()
                        .providerId("good").externalId("g1").title("Scholarly Paper").build();
                return new ExternalSearchResult("good", List.of(w), 1L, 5L);
            }
            @Override public Optional<ExternalScholarlyWork> findByDoi(String d) { return Optional.empty(); }
        });

        // Failing provider
        registry.register(new ExternalSourceProvider() {
            @Override public String getProviderId() { return "failing"; }
            @Override public String getDisplayName() { return "Failing Provider"; }
            @Override public boolean isAvailable() { return true; }
            @Override public ExternalSearchResult search(ExternalSearchQuery q) throws IOException {
                throw new ProviderException("failing", 503, "Service unavailable");
            }
            @Override public Optional<ExternalScholarlyWork> findByDoi(String d) { return Optional.empty(); }
        });

        UnifiedExternalReferenceSearch searchService = new UnifiedExternalReferenceSearch(registry);
        UnifiedSearchResult result = searchService.search(ExternalSearchQuery.of("query", 10));

        assertEquals(1, result.works().size());
        assertEquals("Scholarly Paper", result.works().get(0).title());
        assertTrue(result.failedProviders().contains("failing"), "Failed provider should be listed in result metadata");
    }

    @Test
    void resolvesByDoiAcrossFallbackProviders() {
        ExternalSourceRegistry registry = new ExternalSourceRegistry();

        registry.register(new ExternalSourceProvider() {
            @Override public String getProviderId() { return "p1"; }
            @Override public String getDisplayName() { return "P1"; }
            @Override public boolean isAvailable() { return true; }
            @Override public ExternalSearchResult search(ExternalSearchQuery q) { return ExternalSearchResult.empty("p1"); }
            @Override public Optional<ExternalScholarlyWork> findByDoi(String d) { return Optional.empty(); } // Missing in P1
        });

        registry.register(new ExternalSourceProvider() {
            @Override public String getProviderId() { return "p2"; }
            @Override public String getDisplayName() { return "P2"; }
            @Override public boolean isAvailable() { return true; }
            @Override public ExternalSearchResult search(ExternalSearchQuery q) { return ExternalSearchResult.empty("p2"); }
            @Override public Optional<ExternalScholarlyWork> findByDoi(String d) {
                return Optional.of(ExternalScholarlyWork.builder()
                        .providerId("p2").externalId("p2-id").title("Found in P2").doi(d).build());
            }
        });

        UnifiedExternalReferenceSearch searchService = new UnifiedExternalReferenceSearch(registry);
        Optional<ExternalScholarlyWork> found = searchService.resolveByDoi("10.1000/182");
        assertTrue(found.isPresent());
        assertEquals("Found in P2", found.get().title());
    }
}
