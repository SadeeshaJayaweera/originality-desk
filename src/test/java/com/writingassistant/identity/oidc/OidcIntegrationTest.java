package com.writingassistant.identity.oidc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class OidcIntegrationTest {

    private static final String ISSUER = "https://keycloak.institution.edu/realms/academic";
    private static final String CLIENT_ID = "originality-desk";

    @Test
    @DisplayName("OIDC is disabled by default and rejects callback attempts")
    void oidcDisabledByDefault() {
        OidcConfig config = OidcConfig.disabled();
        assertFalse(config.enabled());

        OidcAuthenticationService service = new OidcAuthenticationService(config, null);
        assertThrows(IllegalStateException.class, () -> service.handleCallback("dummy.jwt.token"));
    }

    @Test
    @DisplayName("OidcAuthenticationService builds RFC-compliant authorization URL with state and nonce")
    void authorizationUrlConstruction() {
        OidcConfig config = OidcConfig.keycloak(
                ISSUER,
                CLIENT_ID,
                "secret123",
                "https://desk.institution.edu/api/v1/auth/oidc/callback"
        );

        OidcAuthenticationService service = new OidcAuthenticationService(config, null);
        String authUrl = service.buildAuthorizationUrl("state-xyz-123", "nonce-abc-456");

        assertNotNull(authUrl);
        assertTrue(authUrl.startsWith(ISSUER + "/protocol/openid-connect/auth?"));
        assertTrue(authUrl.contains("client_id=" + CLIENT_ID));
        assertTrue(authUrl.contains("response_type=code"));
        assertTrue(authUrl.contains("state=state-xyz-123"));
        assertTrue(authUrl.contains("nonce=nonce-abc-456"));
    }

    @Test
    @DisplayName("IdTokenClaims parses JWT and validates issuer, audience, and expiration")
    void idTokenClaimsValidation() {
        long now = Instant.now().getEpochSecond();
        String validJwt = createMockJwt(ISSUER, CLIENT_ID, "prof.turing@institution.edu", "Alan Turing", now + 3600, Set.of("originality-admin"));

        IdTokenClaims claims = IdTokenClaims.parseJwt(validJwt);
        assertEquals("prof.turing@institution.edu", claims.email());
        assertEquals("Alan Turing", claims.name());
        assertEquals(ISSUER, claims.issuer());
        assertEquals(CLIENT_ID, claims.audience());
        assertTrue(claims.roles().contains("originality-admin"));

        // Must validate successfully
        assertDoesNotThrow(() -> claims.validate(ISSUER, CLIENT_ID));

        // Wrong issuer
        assertThrows(SecurityException.class, () -> claims.validate("https://attacker.com", CLIENT_ID));

        // Wrong audience
        assertThrows(SecurityException.class, () -> claims.validate(ISSUER, "wrong-client"));

        // Expired token
        String expiredJwt = createMockJwt(ISSUER, CLIENT_ID, "user@test.edu", "User", now - 3600, Set.of());
        IdTokenClaims expiredClaims = IdTokenClaims.parseJwt(expiredJwt);
        assertThrows(SecurityException.class, () -> expiredClaims.validate(ISSUER, CLIENT_ID));
    }

    @Test
    @DisplayName("OidcRoleMapper correctly maps Keycloak roles to platform tiers")
    void oidcRoleMapping() {
        OidcConfig config = OidcConfig.keycloak(ISSUER, CLIENT_ID, "sec", "http://callback");

        // Admin
        IdTokenClaims adminClaims = new IdTokenClaims("sub-1", ISSUER, CLIENT_ID, "a@b.c", "A", "a", 0, 0, Set.of("originality-admin"));
        assertEquals("INSTITUTION_ADMIN", OidcRoleMapper.mapRole(adminClaims, config));

        // Reviewer
        IdTokenClaims reviewerClaims = new IdTokenClaims("sub-2", ISSUER, CLIENT_ID, "b@b.c", "B", "b", 0, 0, Set.of("originality-reviewer"));
        assertEquals("REVIEWER", OidcRoleMapper.mapRole(reviewerClaims, config));

        // Default member
        IdTokenClaims memberClaims = new IdTokenClaims("sub-3", ISSUER, CLIENT_ID, "c@b.c", "C", "c", 0, 0, Set.of("default-roles-academic"));
        assertEquals("MEMBER", OidcRoleMapper.mapRole(memberClaims, config));
    }

    @Test
    @DisplayName("End-to-end OIDC callback provisions new user, assigns mapped role, and issues session")
    void endToEndUserProvisioningAndSession() throws Exception {
        Map<String, Map<String, Object>> mockUserStore = new HashMap<>();

        UserSessionProvisioner provisioner = (email, name, role, defaultWs) -> {
            Map<String, Object> user = mockUserStore.computeIfAbsent(email, k -> {
                Map<String, Object> u = new HashMap<>();
                u.put("id", "user-" + UUID.randomUUID());
                u.put("email", email);
                u.put("workspace_id", defaultWs);
                return u;
            });
            user.put("name", name);
            user.put("role", role);

            Map<String, Object> res = new HashMap<>();
            res.put("token", "sess-token-" + UUID.randomUUID());
            res.put("user", user);
            return res;
        };

        OidcConfig config = OidcConfig.keycloak(ISSUER, CLIENT_ID, "sec", "http://callback");
        OidcAuthenticationService service = new OidcAuthenticationService(config, provisioner);

        long future = Instant.now().getEpochSecond() + 3600;
        String jwt = createMockJwt(ISSUER, CLIENT_ID, "ada.lovelace@institution.edu", "Ada Lovelace", future, Set.of("originality-reviewer"));

        // 1. First login: user provisioned with REVIEWER role
        Map<String, Object> authResult = service.handleCallback(jwt);
        assertNotNull(authResult.get("token"));

        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) authResult.get("user");
        assertEquals("ada.lovelace@institution.edu", user.get("email"));
        assertEquals("Ada Lovelace", user.get("name"));
        assertEquals("REVIEWER", user.get("role"));

        // 2. Second login: role upgraded in Keycloak to originality-admin
        String upgradedJwt = createMockJwt(ISSUER, CLIENT_ID, "ada.lovelace@institution.edu", "Ada Lovelace Countess of Lovelace", future, Set.of("originality-admin"));
        Map<String, Object> updatedResult = service.handleCallback(upgradedJwt);

        @SuppressWarnings("unchecked")
        Map<String, Object> updatedUser = (Map<String, Object>) updatedResult.get("user");
        assertEquals("INSTITUTION_ADMIN", updatedUser.get("role"));
        assertEquals("Ada Lovelace Countess of Lovelace", updatedUser.get("name"));
    }

    private static String createMockJwt(String issuer, String audience, String email, String name, long exp, Set<String> roles) {
        JsonObject header = new JsonObject();
        header.addProperty("alg", "HS256");
        header.addProperty("typ", "JWT");

        JsonObject payload = new JsonObject();
        payload.addProperty("sub", "keycloak-sub-" + email);
        payload.addProperty("iss", issuer);
        payload.addProperty("aud", audience);
        payload.addProperty("email", email);
        payload.addProperty("name", name);
        payload.addProperty("preferred_username", email);
        payload.addProperty("exp", exp);
        payload.addProperty("iat", exp - 3600);

        JsonObject realmAccess = new JsonObject();
        JsonArray rolesArr = new JsonArray();
        for (String r : roles) rolesArr.add(r);
        realmAccess.add("roles", rolesArr);
        payload.add("realm_access", realmAccess);

        String b64Header = Base64.getUrlEncoder().withoutPadding().encodeToString(header.toString().getBytes(StandardCharsets.UTF_8));
        String b64Payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
        String b64Sig = Base64.getUrlEncoder().withoutPadding().encodeToString("mock-signature-bytes".getBytes(StandardCharsets.UTF_8));

        return b64Header + "." + b64Payload + "." + b64Sig;
    }
}
