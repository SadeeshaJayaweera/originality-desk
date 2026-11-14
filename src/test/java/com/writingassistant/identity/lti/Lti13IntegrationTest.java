package com.writingassistant.identity.lti;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.writingassistant.identity.UserSessionProvisioner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class Lti13IntegrationTest {

    private Lti13Config config;
    private final String issuer = "https://canvas.instructure.com";
    private final String clientId = "10000000000001";
    private final String deploymentId = "dep_canvas_12345";
    private final String oidcAuthUrl = "https://canvas.instructure.com/api/lti/authorize_redirect";
    private final String toolRedirectUri = "https://originalitydesk.example.edu/lti/launch";

    private KeyPair rsaKeyPair;
    private String publicKeyPem;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        rsaKeyPair = kpg.generateKeyPair();
        publicKeyPem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(rsaKeyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";

        config = Lti13Config.builder()
                .platformIssuer(issuer)
                .clientId(clientId)
                .deploymentId(deploymentId)
                .oidcAuthUrl(oidcAuthUrl)
                .toolRedirectUri(toolRedirectUri)
                .platformPublicKeyPem(publicKeyPem)
                .defaultWorkspaceId("ws_default")
                .build();
    }

    @Test
    @DisplayName("Initiate LTI 1.3 OIDC login redirect with state, nonce, and message hint")
    void testInitiateOidcLogin() throws Exception {
        LtiOidcLaunchHandler handler = new LtiOidcLaunchHandler(config);

        String loginHint = "user_canvas_9876";
        String targetLinkUri = "https://originalitydesk.example.edu/assignments/view";
        String ltiMessageHint = "hint_correlation_abc";

        LtiOidcLaunchHandler.InitiationResult result = handler.initiateLogin(issuer, loginHint, targetLinkUri, ltiMessageHint);

        assertNotNull(result);
        assertNotNull(result.state());
        assertNotNull(result.nonce());
        assertTrue(result.authRedirectUrl().startsWith(oidcAuthUrl));
        assertTrue(result.authRedirectUrl().contains("client_id=" + clientId));
        assertTrue(result.authRedirectUrl().contains("login_hint=" + loginHint));
        assertTrue(result.authRedirectUrl().contains("state=" + result.state()));
        assertTrue(result.authRedirectUrl().contains("nonce=" + result.nonce()));
        assertTrue(result.authRedirectUrl().contains("lti_message_hint=" + ltiMessageHint));
    }

    @Test
    @DisplayName("Reject OIDC initiation for unrecognized or untrusted platform issuer")
    void testUntrustedIssuerInitiation() {
        LtiOidcLaunchHandler handler = new LtiOidcLaunchHandler(config);
        assertThrows(LtiException.class, () ->
                handler.initiateLogin("https://rogue-lms.attacker.org", "hint", "uri", "msg"));
    }

    @Test
    @DisplayName("Validate and parse valid LTI 1.3 launch token with course context and AGS endpoints")
    void testSuccessfulLaunchTokenValidation() throws Exception {
        LtiOidcLaunchHandler handler = new LtiOidcLaunchHandler(config);
        String nonce = "nonce_valid_123";

        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", issuer);
        claims.put("aud", clientId);
        claims.put("sub", "user_canvas_42");
        claims.put("exp", Instant.now().plusSeconds(3600).getEpochSecond());
        claims.put("iat", Instant.now().getEpochSecond());
        claims.put("nonce", nonce);
        claims.put("email", "ada.lovelace@institution.edu");
        claims.put("name", "Ada Lovelace");
        claims.put(LtiMessageClaims.CLAIM_MESSAGE_TYPE, "LtiResourceLinkRequest");
        claims.put(LtiMessageClaims.CLAIM_VERSION, "1.3.0");
        claims.put(LtiMessageClaims.CLAIM_DEPLOYMENT_ID, deploymentId);

        Map<String, Object> resLink = Map.of("id", "res_link_99", "title", "Final Essay Submission");
        claims.put(LtiMessageClaims.CLAIM_RESOURCE_LINK, resLink);

        Map<String, Object> context = Map.of("id", "course_cs101", "title", "Introduction to Computer Science", "label", "CS101");
        claims.put(LtiMessageClaims.CLAIM_CONTEXT, context);

        claims.put(LtiMessageClaims.CLAIM_ROLES, List.of(
                "http://purl.imsglobal.org/vocab/lis/v2/membership#Instructor"
        ));

        Map<String, Object> ags = Map.of(
                "lineitem", "https://canvas.instructure.com/api/lti/courses/1/line_items/10",
                "scope", List.of("https://purl.imsglobal.org/spec/lti-ags/scope/lineitem")
        );
        claims.put(LtiMessageClaims.CLAIM_AGS_ENDPOINT, ags);

        String jwt = createSignedJwt(claims, rsaKeyPair.getPrivate());

        LtiMessageClaims parsed = handler.validateLaunchToken(jwt, nonce);

        assertNotNull(parsed);
        assertEquals("user_canvas_42", parsed.subject());
        assertEquals(issuer, parsed.issuer());
        assertEquals(clientId, parsed.audience());
        assertEquals("ada.lovelace@institution.edu", parsed.email());
        assertEquals("Ada Lovelace", parsed.name());
        assertEquals("res_link_99", parsed.resourceLinkId());
        assertEquals("course_cs101", parsed.contextId());
        assertEquals("CS101", parsed.contextLabel());
        assertTrue(parsed.roles().contains("http://purl.imsglobal.org/vocab/lis/v2/membership#Instructor"));
        assertEquals("https://canvas.instructure.com/api/lti/courses/1/line_items/10", parsed.agsLineitemUrl());
    }

    @Test
    @DisplayName("Map LIS 2.0 role vocabularies to internal platform roles")
    void testLtiRoleMapping() {
        assertEquals("REVIEWER", LtiRoleMapper.mapRole(List.of("http://purl.imsglobal.org/vocab/lis/v2/membership#Instructor")));
        assertEquals("REVIEWER", LtiRoleMapper.mapRole(List.of("http://purl.imsglobal.org/vocab/lis/v2/membership#TeachingAssistant")));
        assertEquals("REVIEWER", LtiRoleMapper.mapRole(List.of("Instructor")));
        assertEquals("INSTITUTION_ADMIN", LtiRoleMapper.mapRole(List.of("http://purl.imsglobal.org/vocab/lis/v2/system/person#Administrator")));
        assertEquals("INSTITUTION_ADMIN", LtiRoleMapper.mapRole(List.of("Administrator")));
        assertEquals("MEMBER", LtiRoleMapper.mapRole(List.of("http://purl.imsglobal.org/vocab/lis/v2/membership#Learner")));
        assertEquals("MEMBER", LtiRoleMapper.mapRole(List.of("Student")));
        assertEquals("MEMBER", LtiRoleMapper.mapRole(Collections.emptyList()));
    }

    @Test
    @DisplayName("Reject expired LTI launch token")
    void testExpiredTokenRejection() {
        LtiOidcLaunchHandler handler = new LtiOidcLaunchHandler(config);
        String nonce = "nonce_expired";

        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", issuer);
        claims.put("aud", clientId);
        claims.put("sub", "user_1");
        claims.put("exp", Instant.now().minusSeconds(120).getEpochSecond());
        claims.put("iat", Instant.now().minusSeconds(3600).getEpochSecond());
        claims.put("nonce", nonce);
        claims.put(LtiMessageClaims.CLAIM_MESSAGE_TYPE, "LtiResourceLinkRequest");
        claims.put(LtiMessageClaims.CLAIM_VERSION, "1.3.0");

        String jwt = createSignedJwt(claims, rsaKeyPair.getPrivate());

        LtiException ex = assertThrows(LtiException.class, () ->
                handler.validateLaunchToken(jwt, nonce));
        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    @DisplayName("Reject launch token when nonce does not match expected session nonce")
    void testNonceMismatchRejection() {
        LtiOidcLaunchHandler handler = new LtiOidcLaunchHandler(config);

        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", issuer);
        claims.put("aud", clientId);
        claims.put("sub", "user_1");
        claims.put("exp", Instant.now().plusSeconds(3600).getEpochSecond());
        claims.put("nonce", "tampered_nonce_999");
        claims.put(LtiMessageClaims.CLAIM_MESSAGE_TYPE, "LtiResourceLinkRequest");
        claims.put(LtiMessageClaims.CLAIM_VERSION, "1.3.0");

        String jwt = createSignedJwt(claims, rsaKeyPair.getPrivate());

        LtiException ex = assertThrows(LtiException.class, () ->
                handler.validateLaunchToken(jwt, "expected_nonce_111"));
        assertTrue(ex.getMessage().contains("Nonce mismatch"));
    }

    @Test
    @DisplayName("Reject launch token when cryptographic RSA signature is invalid")
    void testInvalidSignatureRejection() throws Exception {
        LtiOidcLaunchHandler handler = new LtiOidcLaunchHandler(config);
        String nonce = "nonce_sig_test";

        KeyPairGenerator otherKpg = KeyPairGenerator.getInstance("RSA");
        otherKpg.initialize(2048);
        KeyPair otherKeyPair = otherKpg.generateKeyPair();

        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", issuer);
        claims.put("aud", clientId);
        claims.put("sub", "user_1");
        claims.put("exp", Instant.now().plusSeconds(3600).getEpochSecond());
        claims.put("nonce", nonce);
        claims.put(LtiMessageClaims.CLAIM_MESSAGE_TYPE, "LtiResourceLinkRequest");
        claims.put(LtiMessageClaims.CLAIM_VERSION, "1.3.0");

        // Signed by untrusted key
        String jwt = createSignedJwt(claims, otherKeyPair.getPrivate());

        assertThrows(LtiException.class, () ->
                handler.validateLaunchToken(jwt, nonce));
    }

    @Test
    @DisplayName("End-to-end LTI course launch, automatic workspace mapping, and user provisioning")
    void testEndToEndLtiCourseContextProvisioning() throws Exception {
        Map<String, Object> mockSession = new HashMap<>();
        mockSession.put("token", "sess_lti_test_token");

        UserSessionProvisioner provisioner = (email, name, role, defaultWs) -> {
            mockSession.put("email", email);
            mockSession.put("name", name);
            mockSession.put("role", role);
            mockSession.put("workspaceId", defaultWs);
            return mockSession;
        };

        Lti13Service service = new Lti13Service(config, provisioner);
        String nonce = "nonce_e2e_canvas";

        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", issuer);
        claims.put("aud", clientId);
        claims.put("sub", "canvas_prof_hopper");
        claims.put("exp", Instant.now().plusSeconds(3600).getEpochSecond());
        claims.put("iat", Instant.now().getEpochSecond());
        claims.put("nonce", nonce);
        claims.put("email", "grace.hopper@navy.edu");
        claims.put("name", "Grace Hopper");
        claims.put(LtiMessageClaims.CLAIM_MESSAGE_TYPE, "LtiResourceLinkRequest");
        claims.put(LtiMessageClaims.CLAIM_VERSION, "1.3.0");
        claims.put(LtiMessageClaims.CLAIM_DEPLOYMENT_ID, deploymentId);
        claims.put(LtiMessageClaims.CLAIM_RESOURCE_LINK, Map.of("id", "res_assignment_1", "title", "Compiler Project"));
        claims.put(LtiMessageClaims.CLAIM_CONTEXT, Map.of("id", "course_compilers_401", "title", "Advanced Compilers"));
        claims.put(LtiMessageClaims.CLAIM_ROLES, List.of("http://purl.imsglobal.org/vocab/lis/v2/membership#Instructor"));

        String jwt = createSignedJwt(claims, rsaKeyPair.getPrivate());

        LtiLaunchResult launchResult = service.handleLaunch(jwt, nonce, null);

        assertNotNull(launchResult);
        assertEquals("grace.hopper@navy.edu", launchResult.getEmail());
        assertEquals("Grace Hopper", launchResult.getName());
        assertEquals("REVIEWER", launchResult.getRole());
        assertEquals("lms_course_compilers_401", launchResult.getWorkspaceId());
        assertEquals("Compiler Project", launchResult.getResourceLinkTitle());
        assertEquals("sess_lti_test_token", launchResult.getSessionData().get("token"));
    }

    private String createSignedJwt(Map<String, Object> claims, PrivateKey privateKey) {
        try {
            Gson gson = new Gson();
            JsonObject header = new JsonObject();
            header.addProperty("typ", "JWT");
            header.addProperty("alg", "RS256");

            String headerB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(gson.toJson(header).getBytes(StandardCharsets.UTF_8));
            String payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(gson.toJson(claims).getBytes(StandardCharsets.UTF_8));

            String signingInput = headerB64 + "." + payloadB64;
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(privateKey);
            sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            byte[] signatureBytes = sig.sign();
            String sigB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);

            return signingInput + "." + sigB64;
        } catch (Exception e) {
            throw new RuntimeException("Failed to construct signed test JWT", e);
        }
    }
}
