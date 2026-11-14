package com.writingassistant.identity.oidc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Parsed and validated claims extracted from an OpenID Connect ID Token or UserInfo endpoint.
 */
public record IdTokenClaims(
        String subject,
        String issuer,
        String audience,
        String email,
        String name,
        String username,
        long expirationTime,
        long issuedAtTime,
        Set<String> roles
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public IdTokenClaims {
        Objects.requireNonNull(subject, "subject must not be null");
        roles = roles != null ? Collections.unmodifiableSet(roles) : Set.of();
    }

    /**
     * Parses an encoded JWT (header.payload.signature) and extracts claims.
     */
    public static IdTokenClaims parseJwt(String jwtToken) {
        if (jwtToken == null || jwtToken.isBlank()) {
            throw new IllegalArgumentException("JWT token must not be null or blank");
        }
        String[] parts = jwtToken.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid JWT token format: expected header.payload.signature");
        }

        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        JsonObject json = JsonParser.parseString(payloadJson).getAsJsonObject();

        String sub = json.has("sub") ? json.get("sub").getAsString() : "";
        String iss = json.has("iss") ? json.get("iss").getAsString() : "";

        String aud = "";
        if (json.has("aud")) {
            JsonElement audEl = json.get("aud");
            if (audEl.isJsonArray() && audEl.getAsJsonArray().size() > 0) {
                aud = audEl.getAsJsonArray().get(0).getAsString();
            } else if (audEl.isJsonPrimitive()) {
                aud = audEl.getAsString();
            }
        }

        String email = json.has("email") ? json.get("email").getAsString() : "";
        String name = json.has("name") ? json.get("name").getAsString() : (json.has("preferred_username") ? json.get("preferred_username").getAsString() : email);
        String username = json.has("preferred_username") ? json.get("preferred_username").getAsString() : email;
        long exp = json.has("exp") ? json.get("exp").getAsLong() : 0L;
        long iat = json.has("iat") ? json.get("iat").getAsLong() : 0L;

        Set<String> roles = new HashSet<>();
        // 1. Keycloak realm_access.roles
        if (json.has("realm_access") && json.getAsJsonObject("realm_access").has("roles")) {
            JsonArray arr = json.getAsJsonObject("realm_access").getAsJsonArray("roles");
            for (JsonElement el : arr) roles.add(el.getAsString());
        }
        // 2. Direct roles claim
        if (json.has("roles") && json.get("roles").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("roles")) roles.add(el.getAsString());
        }

        return new IdTokenClaims(sub, iss, aud, email, name, username, exp, iat, roles);
    }

    /**
     * Validates cryptographic and operational invariants of the ID token.
     */
    public void validate(String expectedIssuer, String expectedAudience) {
        if (expectedIssuer != null && !expectedIssuer.isBlank() && !expectedIssuer.equalsIgnoreCase(issuer)) {
            throw new SecurityException("OIDC Issuer mismatch: expected '" + expectedIssuer + "', received '" + issuer + "'");
        }
        if (expectedAudience != null && !expectedAudience.isBlank() && !expectedAudience.equalsIgnoreCase(audience)) {
            throw new SecurityException("OIDC Audience mismatch: expected '" + expectedAudience + "', received '" + audience + "'");
        }

        long nowSec = Instant.now().getEpochSecond();
        // Allow 60s clock skew
        if (expirationTime > 0 && (nowSec - 60) > expirationTime) {
            throw new SecurityException("OIDC ID Token has expired (exp=" + expirationTime + ", now=" + nowSec + ")");
        }
    }
}
