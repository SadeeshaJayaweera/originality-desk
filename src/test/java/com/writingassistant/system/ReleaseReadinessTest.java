package com.writingassistant.system;

import com.writingassistant.identity.UserSessionProvisioner;
import com.writingassistant.identity.lti.Lti13Config;
import com.writingassistant.identity.lti.Lti13Service;
import com.writingassistant.identity.oidc.IdTokenClaims;
import com.writingassistant.identity.oidc.OidcConfig;
import com.writingassistant.identity.oidc.OidcRoleMapper;
import com.writingassistant.identity.saml.SamlAssertionClaims;
import com.writingassistant.identity.saml.SamlRoleMapper;
import com.writingassistant.identity.scim.*;
import com.writingassistant.jobs.BackoffPolicy;
import com.writingassistant.jobs.JobLease;
import com.writingassistant.review.*;
import com.writingassistant.similarity.*;
import com.writingassistant.similarity.chunking.ChunkingOptions;
import com.writingassistant.similarity.chunking.DocumentChunk;
import com.writingassistant.similarity.chunking.SentenceAwareChunkingStrategy;
import com.writingassistant.sources.ExternalScholarlyWork;
import com.writingassistant.sources.ScholarlyCitationFormatter;
import com.writingassistant.storage.LocalStorageService;
import com.writingassistant.storage.StorageMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Release Readiness and Production Validation Suite for Originality Desk v1.0.0.
 * Exercises integration flows across all 40 engineering modernisation pull requests.
 */
class ReleaseReadinessTest {

    @Test
    @DisplayName("E2E Verification 1: Document Processing and Sentence-Aware Chunking")
    void testDocumentProcessingAndChunking() {
        String academicText = "Deep learning models require rigorous validation. Natural language processing relies on dense vector representations. "
                + "Academic integrity platforms must detect both verbatim copying and conceptual paraphrasing accurately.";

        SentenceAwareChunkingStrategy chunker = new SentenceAwareChunkingStrategy();
        List<DocumentChunk> chunks = chunker.chunk("doc_release_1", academicText, ChunkingOptions.defaults());

        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.size() >= 1);
        for (DocumentChunk chunk : chunks) {
            assertTrue(chunk.startOffset() >= 0);
            assertTrue(chunk.endOffset() > chunk.startOffset());
            assertEquals(academicText.substring(chunk.startOffset(), chunk.endOffset()), chunk.text());
        }
    }

    @Test
    @DisplayName("E2E Verification 2: Pluggable Similarity Engine and Hybrid Matching")
    void testSimilarityStrategyExecution() {
        SimilarityStrategyRegistry registry = new SimilarityStrategyRegistry();
        registry.register(new ExactSimilarityStrategy());
        registry.register(new NearDuplicateSimilarityStrategy());

        String prose = "The quick brown fox jumps over the lazy dog in the middle of a warm summer afternoon in the forest.";
        SimilarityTarget target = SimilarityTarget.of("doc_target", "Submitted Document", prose);
        SimilaritySource source = SimilaritySource.of("doc_ref", "Reference Paper", prose, "REFERENCE");

        Optional<SimilarityStrategy> exactOpt = registry.getStrategy(SimilarityStrategyType.EXACT);
        assertTrue(exactOpt.isPresent());
        StrategyMatchResult exactResult = exactOpt.get().analyze(target, List.of(source), SimilarityOptions.standard());
        assertNotNull(exactResult);
        assertEquals(SimilarityStrategyType.EXACT, exactResult.strategyType());
        assertTrue(exactResult.coverageRatio() > 0.8);
        assertFalse(exactResult.spans().isEmpty());
    }

    @Test
    @DisplayName("E2E Verification 3: Scholarly Reference Attribution and Citation Formatting")
    void testScholarlyAttributionAndCitations() {
        ExternalScholarlyWork work = ExternalScholarlyWork.builder()
                .providerId("openalex")
                .externalId("W12345")
                .title("Attention Is All You Need")
                .authors(List.of("Vaswani, A.", "Shazeer, N.", "Parmar, N."))
                .publicationYear(2017)
                .venueOrJournal("Advances in Neural Information Processing Systems")
                .doi("10.48550/arXiv.1706.03762")
                .landingPageUrl("https://openalex.org/W12345")
                .pdfUrl("https://arxiv.org/pdf/1706.03762.pdf")
                .abstractText("The dominant sequence transduction models are based on complex recurrent or convolutional neural networks.")
                .build();

        String apa = ScholarlyCitationFormatter.formatApa(work);
        assertTrue(apa.contains("Vaswani"));
        assertTrue(apa.contains("(2017)"));
        assertTrue(apa.contains("Attention Is All You Need"));
        assertTrue(apa.contains("https://doi.org/10.48550/arxiv.1706.03762"));

        String ieee = ScholarlyCitationFormatter.formatIeee(work);
        assertTrue(ieee.contains("Attention Is All You Need"));
        assertTrue(ieee.contains("2017"));

        String bibtex = ScholarlyCitationFormatter.formatBibtex(work);
        assertTrue(bibtex.startsWith("@article{"));
        assertTrue(bibtex.contains("title = {Attention Is All You Need}"));
    }

    @Test
    @DisplayName("E2E Verification 4: Human Review Workflow State Machine and Governance")
    void testHumanReviewWorkflowGovernance() {
        HumanReviewWorkflowEngine engine = new HumanReviewWorkflowEngine();

        ReviewRecord r1 = new ReviewRecord(
                "rev_1", "ws_princeton", "analysis_1", 0, "reviewer_1",
                "Prof Turing", ReviewRole.SENIOR_REVIEWER, ReviewDecision.FLAGGED,
                "Suspicious verbatim overlap without citation.", Instant.now().toString(), false
        );

        // Validation of valid transition
        assertDoesNotThrow(() ->
                ReviewTransitionValidator.validateTransition(
                        ReviewDecision.FLAGGED, ReviewDecision.APPROVED, ReviewRole.SENIOR_REVIEWER, "Verified primary source consent."
                )
        );

        // Transition without justification is rejected
        assertThrows(IllegalStateException.class, () ->
                ReviewTransitionValidator.validateTransition(
                        ReviewDecision.FLAGGED, ReviewDecision.EXEMPTED, ReviewRole.SENIOR_REVIEWER, "   "
                )
        );

        DocumentReviewSummary summary = engine.evaluate("ws_princeton", "analysis_1", 1, List.of(r1));
        assertNotNull(summary);
        assertEquals(1, summary.flaggedCount());
        assertEquals(1, summary.reviewedMatches());
    }

    @Test
    @DisplayName("E2E Verification 5: Durable Background Job Processing and Leases")
    void testDurableJobLifecycle() {
        BackoffPolicy policy = new BackoffPolicy(1000L, 10_000L, 2.0);
        long delay0 = policy.computeDelay(0);
        assertEquals(0L, delay0);

        long delay1 = policy.computeDelay(1);
        assertTrue(delay1 >= 1000L && delay1 <= 1200L);

        JobLease lease = JobLease.acquire("worker_alpha", 1000L);
        assertEquals("worker_alpha", lease.leaseOwner());
        assertFalse(lease.isExpired(System.currentTimeMillis()));
        assertTrue(lease.isExpired(System.currentTimeMillis() + 1500L));
    }

    @Test
    @DisplayName("E2E Verification 6: Multi-Tenant Storage Abstraction and Path Jailing")
    void testStorageAbstractionAndDirectoryJailing() throws Exception {
        Path tempDir = Files.createTempDirectory("release_storage_test");
        try (LocalStorageService storage = new LocalStorageService(tempDir)) {
            String workspaceA = "ws_tenant_alpha";
            String workspaceB = "ws_tenant_beta";
            String key = "documents/essay1.pdf";
            byte[] content = "CONFIDENTIAL_ESSAY_CONTENT".getBytes(StandardCharsets.UTF_8);

            StorageMetadata meta = storage.put(workspaceA, key, content, "application/pdf");
            assertNotNull(meta.sha256());
            assertEquals(content.length, meta.sizeBytes());

            assertTrue(storage.exists(workspaceA, key));
            // Strict tenant isolation: Tenant Beta cannot see Tenant Alpha's key
            assertFalse(storage.exists(workspaceB, key));

            // Path Traversal Directory Jailing
            assertThrows(SecurityException.class, () ->
                    storage.get(workspaceA, "../../etc/passwd"));
        } finally {
            try (var stream = Files.walk(tempDir)) {
                stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
            }
        }
    }

    @Test
    @DisplayName("E2E Verification 7: Institutional Identity Federation (OIDC, SAML, LTI, SCIM)")
    void testInstitutionalIdentityFederation() throws Exception {
        Map<String, Object> sessionOutput = new HashMap<>();
        UserSessionProvisioner provisioner = (email, name, role, ws) -> {
            sessionOutput.put("email", email);
            sessionOutput.put("name", name);
            sessionOutput.put("role", role);
            sessionOutput.put("workspaceId", ws);
            sessionOutput.put("sessionToken", "token_" + UUID.randomUUID());
            return sessionOutput;
        };

        // 1. OIDC Role Mapping
        IdTokenClaims oidcClaims = new IdTokenClaims(
                "sub_oidc_1", "https://keycloak.institution.edu", "originality-desk",
                "prof@institution.edu", "Prof X", "profx",
                Instant.now().plusSeconds(3600).getEpochSecond(), Instant.now().getEpochSecond(),
                Set.of("faculty", "reviewer")
        );
        OidcConfig oidcConfig = OidcConfig.keycloak("https://keycloak.institution.edu", "originality-desk", "secret", "https://app/callback");
        assertEquals("REVIEWER", OidcRoleMapper.mapRole(oidcClaims, oidcConfig));

        // 2. SAML 2.0 eduPerson Role Mapping
        SamlAssertionClaims samlClaims = new SamlAssertionClaims(
                "saml_sub_1", "urn:oid", "https://idp.institution.edu", "dean@institution.edu",
                "Dean Smith", null, null, List.of("faculty@institution.edu", "institution_admin@institution.edu"), Map.of()
        );
        assertEquals("INSTITUTION_ADMIN", SamlRoleMapper.mapRole(samlClaims));

        // 3. LTI 1.3 Course Mapping
        Lti13Config ltiConfig = Lti13Config.builder()
                .platformIssuer("https://canvas.instructure.com")
                .clientId("cid_123")
                .oidcAuthUrl("https://canvas.instructure.com/auth")
                .toolRedirectUri("https://originalitydesk.institution.edu/lti/launch")
                .defaultWorkspaceId("ws_default")
                .build();
        Lti13Service ltiService = new Lti13Service(ltiConfig, provisioner);
        assertNotNull(ltiService.initiateLogin("https://canvas.instructure.com", "user_1", "target", "hint"));

        // 4. SCIM 2.0 User Provisioning and Deactivation
        ScimProvisioningService scim = new ScimProvisioningService(new InMemoryScimStore());
        ScimUser scimUser = scim.createUser("ws_inst", new ScimUser(null, "jdoe", "Jane Doe", "jdoe@inst.edu", "MEMBER", true));
        assertTrue(scimUser.isActive());

        scim.patchUser("ws_inst", scimUser.getId(), List.of(new ScimPatchOperation("replace", "active", false)));
        ScimUser deactivated = scim.getUser("ws_inst", scimUser.getId());
        assertFalse(deactivated.isActive());
    }
}
