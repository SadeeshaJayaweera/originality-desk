package com.writingassistant.identity.saml;

import com.writingassistant.identity.UserSessionProvisioner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SamlIntegrationTest {

    private SamlConfig config;
    private final String spEntityId = "https://originalitydesk.example.edu/saml/sp";
    private final String acsUrl = "https://originalitydesk.example.edu/saml/acs";
    private final String idpEntityId = "https://idp.institution.edu/idp/shibboleth";
    private final String idpSsoUrl = "https://idp.institution.edu/idp/profile/SAML2/Redirect/SSO";

    @BeforeEach
    void setUp() {
        config = SamlConfig.builder()
                .spEntityId(spEntityId)
                .acsUrl(acsUrl)
                .idpEntityId(idpEntityId)
                .idpSsoUrl(idpSsoUrl)
                .clockSkewSeconds(120)
                .defaultWorkspaceId("ws_harvard")
                .build();
    }

    @Test
    @DisplayName("Generate compliant SAML 2.0 SP metadata XML with required bindings and attribute requests")
    void testSpMetadataGeneration() {
        String metadataXml = SamlMetadataGenerator.generateSpMetadataXml(config);
        assertNotNull(metadataXml);
        assertTrue(metadataXml.contains("entityID=\"" + spEntityId + "\""));
        assertTrue(metadataXml.contains("AssertionConsumerService Binding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\""));
        assertTrue(metadataXml.contains("Location=\"" + acsUrl + "\""));
        assertTrue(metadataXml.contains("NameIDFormat>urn:oasis:names:tc:SAML:2.0:nameid-format:persistent</md:NameIDFormat"));
        assertTrue(metadataXml.contains("RequestedAttribute Name=\"" + SamlConfig.ATTR_MAIL_OID + "\""));
        assertTrue(metadataXml.contains("RequestedAttribute Name=\"" + SamlConfig.ATTR_AFFILIATION_OID + "\""));
    }

    @Test
    @DisplayName("Build AuthnRequest with RFC 1951 Deflate and Base64 URL redirect binding")
    void testAuthnRequestCreation() {
        String relayState = "/document/review/123";
        SamlAuthnRequestBuilder.AuthnRequestResult result = SamlAuthnRequestBuilder.buildRedirectAuthnRequest(config, relayState);

        assertNotNull(result);
        assertNotNull(result.getRequestId());
        assertTrue(result.getRequestId().startsWith("_"));
        assertEquals(relayState, result.getRelayState());

        String xml = result.getXml();
        assertTrue(xml.contains("<samlp:AuthnRequest"));
        assertTrue(xml.contains("ID=\"" + result.getRequestId() + "\""));
        assertTrue(xml.contains("Destination=\"" + idpSsoUrl + "\""));
        assertTrue(xml.contains("AssertionConsumerServiceURL=\"" + acsUrl + "\""));
        assertTrue(xml.contains("<saml:Issuer>" + spEntityId + "</saml:Issuer>"));

        String redirectUrl = result.getRedirectUrl();
        assertTrue(redirectUrl.startsWith(idpSsoUrl));
        assertTrue(redirectUrl.contains("SAMLRequest="));
        assertTrue(redirectUrl.contains("RelayState="));
    }

    @Test
    @DisplayName("Validate successful SAML response and extract attributes and claims")
    void testSuccessfulSamlResponseValidation() throws Exception {
        String requestId = "_req_123456789";
        Instant now = Instant.now();
        String notBefore = now.minus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS).toString();
        String notOnOrAfter = now.plus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS).toString();

        String responseXml = buildSampleSamlResponse(
                requestId,
                "prof.turing@institution.edu",
                "Alan Turing",
                "faculty@institution.edu",
                notBefore,
                notOnOrAfter,
                spEntityId,
                idpEntityId,
                "urn:oasis:names:tc:SAML:2.0:status:Success"
        );

        String base64Response = Base64.getEncoder().encodeToString(responseXml.getBytes());

        SamlAssertionClaims claims = SamlResponseValidator.validateResponse(base64Response, requestId, config);

        assertNotNull(claims);
        assertEquals("prof.turing@institution.edu", claims.getSubjectNameId());
        assertEquals("prof.turing@institution.edu", claims.getEmail());
        assertEquals("Alan Turing", claims.getDisplayName());
        assertEquals(idpEntityId, claims.getIssuer());
        assertTrue(claims.getAffiliations().contains("faculty@institution.edu"));
    }

    @Test
    @DisplayName("Map higher education affiliations to platform roles")
    void testRoleMapping() {
        // Faculty -> REVIEWER
        SamlAssertionClaims facultyClaims = new SamlAssertionClaims(
                "user1", "urn:oid", idpEntityId, "f@inst.edu", "Faculty Member", null, null,
                java.util.List.of("faculty@institution.edu", "member@institution.edu"), Map.of()
        );
        assertEquals("REVIEWER", SamlRoleMapper.mapRole(facultyClaims));

        // Admin -> INSTITUTION_ADMIN
        SamlAssertionClaims adminClaims = new SamlAssertionClaims(
                "user2", "urn:oid", idpEntityId, "a@inst.edu", "System Admin", null, null,
                java.util.List.of("admin@institution.edu"), Map.of()
        );
        assertEquals("INSTITUTION_ADMIN", SamlRoleMapper.mapRole(adminClaims));

        // Student -> MEMBER
        SamlAssertionClaims studentClaims = new SamlAssertionClaims(
                "user3", "urn:oid", idpEntityId, "s@inst.edu", "Student User", null, null,
                java.util.List.of("student@institution.edu"), Map.of()
        );
        assertEquals("MEMBER", SamlRoleMapper.mapRole(studentClaims));
    }

    @Test
    @DisplayName("Reject expired assertion when NotOnOrAfter condition is in the past")
    void testExpiredAssertionRejection() {
        String requestId = "_req_expired";
        Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
        String notBefore = past.minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString();
        String notOnOrAfter = past.truncatedTo(ChronoUnit.SECONDS).toString();

        String responseXml = buildSampleSamlResponse(
                requestId,
                "user@inst.edu",
                "Expired User",
                "student@institution.edu",
                notBefore,
                notOnOrAfter,
                spEntityId,
                idpEntityId,
                "urn:oasis:names:tc:SAML:2.0:status:Success"
        );

        SamlValidationException ex = assertThrows(SamlValidationException.class, () ->
                SamlResponseValidator.validateResponse(responseXml, requestId, config));
        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    @DisplayName("Reject response when InResponseTo does not match original request ID")
    void testInResponseToMismatch() {
        Instant now = Instant.now();
        String notBefore = now.minus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS).toString();
        String notOnOrAfter = now.plus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS).toString();

        String responseXml = buildSampleSamlResponse(
                "_received_id_999",
                "user@inst.edu",
                "Test User",
                "student@institution.edu",
                notBefore,
                notOnOrAfter,
                spEntityId,
                idpEntityId,
                "urn:oasis:names:tc:SAML:2.0:status:Success"
        );

        SamlValidationException ex = assertThrows(SamlValidationException.class, () ->
                SamlResponseValidator.validateResponse(responseXml, "_expected_id_111", config));
        assertTrue(ex.getMessage().contains("InResponseTo mismatch"));
    }

    @Test
    @DisplayName("Reject response when Audience does not match Service Provider Entity ID")
    void testAudienceMismatch() {
        Instant now = Instant.now();
        String notBefore = now.minus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS).toString();
        String notOnOrAfter = now.plus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS).toString();

        String responseXml = buildSampleSamlResponse(
                "_req_aud",
                "user@inst.edu",
                "Test User",
                "student@institution.edu",
                notBefore,
                notOnOrAfter,
                "https://foreign-service.example.org/saml",
                idpEntityId,
                "urn:oasis:names:tc:SAML:2.0:status:Success"
        );

        SamlValidationException ex = assertThrows(SamlValidationException.class, () ->
                SamlResponseValidator.validateResponse(responseXml, "_req_aud", config));
        assertTrue(ex.getMessage().contains("Audience mismatch"));
    }

    @Test
    @DisplayName("Block XML External Entity (XXE) attacks in SAML parsing")
    void testXxeAttackPrevention() {
        String xxePayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE foo [\n"
                + "  <!ELEMENT foo ANY >\n"
                + "  <!ENTITY xxe SYSTEM \"file:///etc/passwd\" >]>\n"
                + "<samlp:Response xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\">\n"
                + "  <foo>&xxe;</foo>\n"
                + "</samlp:Response>";

        assertThrows(SamlValidationException.class, () ->
                SamlResponseValidator.parseSecureXml(xxePayload));
    }

    @Test
    @DisplayName("End-to-end SAML authentication and user session provisioning")
    void testEndToEndAuthenticationAndSessionProvisioning() throws Exception {
        Map<String, Object> mockSession = new HashMap<>();
        mockSession.put("sessionId", "sess_saml_xyz");
        mockSession.put("token", "token_saml_abc");

        UserSessionProvisioner provisioner = (email, name, role, defaultWs) -> {
            mockSession.put("email", email);
            mockSession.put("name", name);
            mockSession.put("role", role);
            mockSession.put("workspaceId", defaultWs);
            return mockSession;
        };

        SamlAuthenticationService service = new SamlAuthenticationService(config, provisioner);

        Instant now = Instant.now();
        String notBefore = now.minus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS).toString();
        String notOnOrAfter = now.plus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS).toString();
        String reqId = "_e2e_request_id";

        String responseXml = buildSampleSamlResponse(
                reqId,
                "dr.curie@institution.edu",
                "Marie Curie",
                "faculty@institution.edu",
                notBefore,
                notOnOrAfter,
                spEntityId,
                idpEntityId,
                "urn:oasis:names:tc:SAML:2.0:status:Success"
        );

        SamlAuthResult result = service.processResponse(responseXml, reqId, "ws_physics");

        assertNotNull(result);
        assertEquals("dr.curie@institution.edu", result.getEmail());
        assertEquals("Marie Curie", result.getDisplayName());
        assertEquals("REVIEWER", result.getRole());
        assertEquals("ws_physics", result.getWorkspaceId());
        assertEquals("sess_saml_xyz", result.getSessionData().get("sessionId"));
    }

    private String buildSampleSamlResponse(String inResponseTo,
                                           String email,
                                           String displayName,
                                           String affiliation,
                                           String notBefore,
                                           String notOnOrAfter,
                                           String audience,
                                           String issuer,
                                           String statusCode) {
        return "<samlp:Response xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\"\n"
                + "                xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\"\n"
                + "                ID=\"_resp_12345\"\n"
                + "                InResponseTo=\"" + inResponseTo + "\"\n"
                + "                Version=\"2.0\"\n"
                + "                IssueInstant=\"" + Instant.now().truncatedTo(ChronoUnit.SECONDS) + "\">\n"
                + "  <saml:Issuer>" + issuer + "</saml:Issuer>\n"
                + "  <samlp:Status>\n"
                + "    <samlp:StatusCode Value=\"" + statusCode + "\"/>\n"
                + "  </samlp:Status>\n"
                + "  <saml:Assertion ID=\"_asst_12345\" Version=\"2.0\" IssueInstant=\"" + Instant.now().truncatedTo(ChronoUnit.SECONDS) + "\">\n"
                + "    <saml:Issuer>" + issuer + "</saml:Issuer>\n"
                + "    <saml:Subject>\n"
                + "      <saml:NameID Format=\"urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress\">" + email + "</saml:NameID>\n"
                + "    </saml:Subject>\n"
                + "    <saml:Conditions NotBefore=\"" + notBefore + "\" NotOnOrAfter=\"" + notOnOrAfter + "\">\n"
                + "      <saml:AudienceRestriction>\n"
                + "        <saml:Audience>" + audience + "</saml:Audience>\n"
                + "      </saml:AudienceRestriction>\n"
                + "    </saml:Conditions>\n"
                + "    <saml:AttributeStatement>\n"
                + "      <saml:Attribute Name=\"" + SamlConfig.ATTR_MAIL_OID + "\" FriendlyName=\"" + SamlConfig.ATTR_MAIL_FRIENDLY + "\">\n"
                + "        <saml:AttributeValue>" + email + "</saml:AttributeValue>\n"
                + "      </saml:Attribute>\n"
                + "      <saml:Attribute Name=\"" + SamlConfig.ATTR_DISPLAY_NAME_OID + "\" FriendlyName=\"" + SamlConfig.ATTR_DISPLAY_NAME_FRIENDLY + "\">\n"
                + "        <saml:AttributeValue>" + displayName + "</saml:AttributeValue>\n"
                + "      </saml:Attribute>\n"
                + "      <saml:Attribute Name=\"" + SamlConfig.ATTR_AFFILIATION_OID + "\" FriendlyName=\"" + SamlConfig.ATTR_AFFILIATION_FRIENDLY + "\">\n"
                + "        <saml:AttributeValue>" + affiliation + "</saml:AttributeValue>\n"
                + "      </saml:Attribute>\n"
                + "    </saml:AttributeStatement>\n"
                + "  </saml:Assertion>\n"
                + "</samlp:Response>";
    }
}
