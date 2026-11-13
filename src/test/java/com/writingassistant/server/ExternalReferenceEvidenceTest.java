package com.writingassistant.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.writingassistant.sources.ExternalReferenceEvidence;
import com.writingassistant.sources.ExternalScholarlyWork;
import com.writingassistant.sources.ExternalSearchQuery;
import com.writingassistant.sources.ExternalSearchResult;
import com.writingassistant.sources.ExternalSourceProvider;
import com.writingassistant.sources.ExternalSourceRegistry;
import com.writingassistant.sources.SourceAttribution;
import com.writingassistant.sources.UnifiedExternalReferenceSearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ExternalReferenceEvidenceTest {

    @TempDir
    Path tempDir;

    private ServerDatabase db;
    private Service service;
    private Service.Actor actorA;
    private Service.Actor actorB;

    @BeforeEach
    void setUp() throws Exception {
        db = new ServerDatabase(tempDir.resolve("server_data"));

        ExternalSourceRegistry registry = new ExternalSourceRegistry();
        registry.register(new MockSourceProvider("mock-provider", "Mock Scholarly Index"));
        UnifiedExternalReferenceSearch search = new UnifiedExternalReferenceSearch(registry);

        service = new Service(db, search);

        // Workspace A
        JsonObject regA = new JsonObject();
        regA.addProperty("name", "Alice Admin");
        regA.addProperty("email", "alice@institution-a.edu");
        regA.addProperty("password", "SecurePassword123!");
        regA.addProperty("workspaceName", "Workspace A");
        Service.Authentication authA = service.register(regA);
        actorA = service.authenticate(authA.token());

        // Workspace B
        JsonObject regB = new JsonObject();
        regB.addProperty("name", "Bob Admin");
        regB.addProperty("email", "bob@institution-b.edu");
        regB.addProperty("password", "SecurePassword456!");
        regB.addProperty("workspaceName", "Workspace B");
        Service.Authentication authB = service.register(regB);
        actorB = service.authenticate(authB.token());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (db != null) db.close();
    }

    @Test
    void fromWorkGeneratesCompleteAttributionAndCitations() {
        ExternalScholarlyWork work = ExternalScholarlyWork.builder()
                .providerId("openalex")
                .externalId("W2964343456")
                .title("Language Models are Few-Shot Learners")
                .authors(List.of("Tom B. Brown", "Benjamin Mann", "Nick Ryder"))
                .publicationYear(2020)
                .venueOrJournal("NeurIPS")
                .doi("10.48550/arXiv.2005.14165")
                .landingPageUrl("https://arxiv.org/abs/2005.14165")
                .pdfUrl("https://arxiv.org/pdf/2005.14165.pdf")
                .citationCount(15000)
                .hasFullText(true)
                .attribution(SourceAttribution.of("openalex", "arXiv", "https://arxiv.org/abs/2005.14165", "CC-BY-4.0", true))
                .build();

        ExternalReferenceEvidence evidence = ExternalReferenceEvidence.fromWork("ws-123", "doc-456", work);

        assertEquals("ws-123", evidence.workspaceId());
        assertEquals("doc-456", evidence.documentId());
        assertEquals("openalex", evidence.providerId());
        assertEquals("10.48550/arxiv.2005.14165", evidence.doi());
        assertEquals("Language Models are Few-Shot Learners", evidence.title());
        assertEquals(3, evidence.authors().size());
        assertEquals(2020, evidence.publicationYear());
        assertEquals("NeurIPS", evidence.venueOrPublisher());
        assertEquals("https://doi.org/10.48550/arxiv.2005.14165", evidence.persistentResolverUrl());
        assertTrue(evidence.isOpenAccess());
        assertEquals("CC-BY-4.0", evidence.license());
        assertEquals(15000, evidence.citationCount());

        assertTrue(evidence.citationApa().contains("Brown, T. B."));
        assertTrue(evidence.citationIeee().contains("\"Language Models are Few-Shot Learners,\""));
        assertTrue(evidence.citationMla().contains("Brown, T. B., et al."));
        assertTrue(evidence.citationBibtex().contains("@article{"));

        Map<String, Object> map = evidence.toMap();
        assertEquals("ws-123", map.get("workspaceId"));
        assertEquals("doc-456", map.get("documentId"));
        assertEquals("openalex", map.get("providerId"));
        assertEquals(evidence.citationApa(), map.get("citationApa"));
    }

    @Test
    void serviceImportsExternalReferenceIntoWorkspaceLibrary() throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("title", "Foundations of Statistical Natural Language Processing");
        payload.addProperty("providerId", "crossref");
        payload.addProperty("externalId", "CR-1011");
        payload.addProperty("doi", "10.5555/312345");
        payload.addProperty("abstractText", "Statistical approaches to natural language processing have revolutionized computational linguistics.");
        payload.addProperty("publicationYear", 1999);
        payload.addProperty("venueOrJournal", "MIT Press");
        payload.addProperty("landingPageUrl", "https://mitpress.mit.edu/books/foundations-statistical-natural-language-processing");
        payload.addProperty("citationCount", 12500);
        payload.addProperty("isOpenAccess", false);

        JsonArray authors = new JsonArray();
        authors.add("Christopher D. Manning");
        authors.add("Hinrich Schutze");
        payload.add("authors", authors);

        Map<String, Object> imported = service.importExternalReference(actorA, payload);
        assertNotNull(imported.get("id"));
        String documentId = (String) imported.get("id");
        assertEquals("Foundations of Statistical Natural Language Processing", imported.get("title"));
        assertEquals("REFERENCE", imported.get("kind"));
        assertEquals("READY", imported.get("status"));

        @SuppressWarnings("unchecked")
        Map<String, Object> evidence = (Map<String, Object>) imported.get("evidence");
        assertNotNull(evidence);
        assertEquals("crossref", evidence.get("providerId"));
        assertEquals("10.5555/312345", evidence.get("doi"));
        assertEquals("https://doi.org/10.5555/312345", evidence.get("persistentResolverUrl"));

        // Verify it appears in documents list
        Map<String, Object> list = service.documents(actorA, "REFERENCE", 1, 10);
        assertEquals(1L, ((Number) list.get("total")).longValue());

        // Verify persistent evidence retrieval
        Map<String, Object> fetchedEvidence = service.getExternalReferenceEvidence(actorA, documentId);
        assertEquals(documentId, fetchedEvidence.get("document_id"));
        assertEquals(actorA.workspaceId(), fetchedEvidence.get("workspace_id"));
        assertEquals("crossref", fetchedEvidence.get("provider_id"));
        assertEquals("10.5555/312345", fetchedEvidence.get("doi"));
        assertTrue(((String) fetchedEvidence.get("citation_apa")).contains("Manning, C. D."));
    }

    @Test
    void serviceExternalReferenceEnforcesStrictTenantIsolation() throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("title", "Tenant Isolated Paper on Cryptography");
        payload.addProperty("providerId", "core");
        payload.addProperty("externalId", "CORE-444");
        payload.addProperty("doi", "10.1007/test-crypto");
        payload.addProperty("abstractText", "Confidential cryptographic schemes for multi-tenant data storage.");

        Map<String, Object> imported = service.importExternalReference(actorA, payload);
        String documentId = (String) imported.get("id");

        // Workspace A can access it
        assertNotNull(service.getExternalReferenceEvidence(actorA, documentId));

        // Workspace B MUST receive 404 NOT FOUND when attempting to access Workspace A's reference
        ApiException ex = assertThrows(ApiException.class, () -> service.getExternalReferenceEvidence(actorB, documentId));
        assertEquals(404, ex.status);

        // Workspace B document listing must be empty
        Map<String, Object> listB = service.documents(actorB, "REFERENCE", 1, 10);
        assertEquals(0L, ((Number) listB.get("total")).longValue());
    }

    @Test
    void serviceDeletesExternalReferenceAndCleansUpAttribution() throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("title", "Temporary Research Paper");
        payload.addProperty("providerId", "openalex");
        payload.addProperty("doi", "10.1234/temp");
        payload.addProperty("abstractText", "Temporary text to test reference lifecycle deletion.");

        Map<String, Object> imported = service.importExternalReference(actorA, payload);
        String documentId = (String) imported.get("id");

        // Verify created
        assertNotNull(service.getExternalReferenceEvidence(actorA, documentId));

        // Delete document
        service.deleteDocument(actorA, documentId);

        // Verify document is deleted
        ApiException docEx = assertThrows(ApiException.class, () -> service.document(actorA, documentId));
        assertEquals(404, docEx.status);

        // Verify external reference attribution row is deleted
        ApiException evEx = assertThrows(ApiException.class, () -> service.getExternalReferenceEvidence(actorA, documentId));
        assertEquals(404, evEx.status);
    }

    @Test
    void serviceSearchAndDoiResolutionWorkCorrectly() throws Exception {
        Map<String, Object> searchResult = service.searchExternalReferences("machine learning", 5);
        assertEquals("machine learning", searchResult.get("query"));
        assertTrue(((Number) searchResult.get("totalResults")).longValue() > 0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> works = (List<Map<String, Object>>) searchResult.get("works");
        assertFalse(works.isEmpty());
        Map<String, Object> firstWork = works.get(0);
        assertNotNull(firstWork.get("title"));
        assertNotNull(firstWork.get("citationApa"));
        assertNotNull(firstWork.get("persistentResolverUrl"));

        // Direct DOI resolution
        Map<String, Object> resolved = service.resolveExternalReferenceByDoi("10.1000/182");
        assertNotNull(resolved);
        assertEquals("10.1000/182", resolved.get("doi"));
        assertTrue(((String) resolved.get("persistentResolverUrl")).contains("https://doi.org/10.1000/182"));

        // Invalid DOI format returns 400
        ApiException badDoiEx = assertThrows(ApiException.class, () -> service.resolveExternalReferenceByDoi("invalid-format"));
        assertEquals(400, badDoiEx.status);

        // Missing DOI returns 404
        ApiException missingEx = assertThrows(ApiException.class, () -> service.resolveExternalReferenceByDoi("10.9999/does-not-exist"));
        assertEquals(404, missingEx.status);
    }

    private static class MockSourceProvider implements ExternalSourceProvider {
        private final String providerId;
        private final String displayName;

        MockSourceProvider(String providerId, String displayName) {
            this.providerId = providerId;
            this.displayName = displayName;
        }

        @Override public String getProviderId() { return providerId; }
        @Override public String getDisplayName() { return displayName; }
        @Override public boolean isAvailable() { return true; }

        @Override
        public ExternalSearchResult search(ExternalSearchQuery query) throws IOException {
            ExternalScholarlyWork work1 = ExternalScholarlyWork.builder()
                    .providerId(providerId)
                    .externalId("MOCK-001")
                    .title("Deep Machine Learning Fundamentals")
                    .authors(List.of("Alice Smith", "Bob Jones"))
                    .publicationYear(2021)
                    .venueOrJournal("Mock Journal of AI")
                    .doi("10.1000/182")
                    .landingPageUrl("https://example.org/mock-001")
                    .abstractText("Comprehensive overview of machine learning architectures.")
                    .citationCount(500)
                    .hasFullText(true)
                    .attribution(SourceAttribution.of(providerId, displayName, "https://example.org/mock-001", "Open Access", true))
                    .build();

            return new ExternalSearchResult(providerId, List.of(work1), 1, 10);
        }

        @Override
        public Optional<ExternalScholarlyWork> findByDoi(String doi) throws IOException {
            if ("10.1000/182".equalsIgnoreCase(doi)) {
                return Optional.of(ExternalScholarlyWork.builder()
                        .providerId(providerId)
                        .externalId("MOCK-001")
                        .title("Deep Machine Learning Fundamentals")
                        .authors(List.of("Alice Smith", "Bob Jones"))
                        .publicationYear(2021)
                        .venueOrJournal("Mock Journal of AI")
                        .doi("10.1000/182")
                        .landingPageUrl("https://example.org/mock-001")
                        .abstractText("Comprehensive overview of machine learning architectures.")
                        .citationCount(500)
                        .hasFullText(true)
                        .attribution(SourceAttribution.of(providerId, displayName, "https://example.org/mock-001", "Open Access", true))
                        .build());
            }
            return Optional.empty();
        }
    }
}
