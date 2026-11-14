package com.writingassistant.identity.lti;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Parsed claims from an LTI 1.3 Advantage Launch ID Token.
 */
public record LtiMessageClaims(
        String subject,
        String issuer,
        String audience,
        long expirationTime,
        long issuedAtTime,
        String nonce,
        String email,
        String name,
        String givenName,
        String familyName,
        String messageType,
        String version,
        String deploymentId,
        String targetLinkUri,
        String resourceLinkId,
        String resourceLinkTitle,
        String resourceLinkDescription,
        String contextId,
        String contextTitle,
        String contextLabel,
        List<String> roles,
        String agsLineitemUrl,
        String agsLineitemsUrl,
        List<String> agsScopes,
        String nrpsMembershipsUrl,
        Map<String, String> customParameters
) implements Serializable {

    public static final String CLAIM_MESSAGE_TYPE = "https://purl.imsglobal.org/spec/lti/claim/message_type";
    public static final String CLAIM_VERSION = "https://purl.imsglobal.org/spec/lti/claim/version";
    public static final String CLAIM_DEPLOYMENT_ID = "https://purl.imsglobal.org/spec/lti/claim/deployment_id";
    public static final String CLAIM_TARGET_LINK_URI = "https://purl.imsglobal.org/spec/lti/claim/target_link_uri";
    public static final String CLAIM_RESOURCE_LINK = "https://purl.imsglobal.org/spec/lti/claim/resource_link";
    public static final String CLAIM_CONTEXT = "https://purl.imsglobal.org/spec/lti/claim/context";
    public static final String CLAIM_ROLES = "https://purl.imsglobal.org/spec/lti/claim/roles";
    public static final String CLAIM_AGS_ENDPOINT = "https://purl.imsglobal.org/spec/lti-ags/claim/endpoint";
    public static final String CLAIM_NRPS_SERVICE = "https://purl.imsglobal.org/spec/lti-nrps/claim/namesroleservice";
    public static final String CLAIM_CUSTOM = "https://purl.imsglobal.org/spec/lti/claim/custom";

    public LtiMessageClaims {
        roles = roles != null ? Collections.unmodifiableList(roles) : Collections.emptyList();
        agsScopes = agsScopes != null ? Collections.unmodifiableList(agsScopes) : Collections.emptyList();
        customParameters = customParameters != null ? Collections.unmodifiableMap(customParameters) : Collections.emptyMap();
    }

    /**
     * Parses an LTI 1.3 JWT payload and maps both OIDC and 1EdTech / IMS Global claims.
     */
    public static LtiMessageClaims parse(String jwt) {
        if (jwt == null || jwt.isBlank()) {
            throw new IllegalArgumentException("JWT token must not be null or blank");
        }
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid JWT format: expected header.payload.signature");
        }

        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        JsonObject json = JsonParser.parseString(payloadJson).getAsJsonObject();

        String sub = getString(json, "sub", "");
        String iss = getString(json, "iss", "");

        String aud = "";
        if (json.has("aud")) {
            JsonElement audEl = json.get("aud");
            if (audEl.isJsonArray() && audEl.getAsJsonArray().size() > 0) {
                aud = audEl.getAsJsonArray().get(0).getAsString();
            } else if (audEl.isJsonPrimitive()) {
                aud = audEl.getAsString();
            }
        }

        long exp = json.has("exp") ? json.get("exp").getAsLong() : 0L;
        long iat = json.has("iat") ? json.get("iat").getAsLong() : 0L;
        String nonce = getString(json, "nonce", null);
        String email = getString(json, "email", null);
        String name = getString(json, "name", null);
        String givenName = getString(json, "given_name", null);
        String familyName = getString(json, "family_name", null);

        String messageType = getString(json, CLAIM_MESSAGE_TYPE, "LtiResourceLinkRequest");
        String version = getString(json, CLAIM_VERSION, "1.3.0");
        String deploymentId = getString(json, CLAIM_DEPLOYMENT_ID, null);
        String targetLinkUri = getString(json, CLAIM_TARGET_LINK_URI, null);

        String resourceLinkId = null;
        String resourceLinkTitle = null;
        String resourceLinkDesc = null;
        if (json.has(CLAIM_RESOURCE_LINK) && json.get(CLAIM_RESOURCE_LINK).isJsonObject()) {
            JsonObject rl = json.getAsJsonObject(CLAIM_RESOURCE_LINK);
            resourceLinkId = getString(rl, "id", null);
            resourceLinkTitle = getString(rl, "title", null);
            resourceLinkDesc = getString(rl, "description", null);
        }

        String contextId = null;
        String contextTitle = null;
        String contextLabel = null;
        if (json.has(CLAIM_CONTEXT) && json.get(CLAIM_CONTEXT).isJsonObject()) {
            JsonObject ctx = json.getAsJsonObject(CLAIM_CONTEXT);
            contextId = getString(ctx, "id", null);
            contextTitle = getString(ctx, "title", null);
            contextLabel = getString(ctx, "label", null);
        }

        List<String> roles = new ArrayList<>();
        if (json.has(CLAIM_ROLES) && json.get(CLAIM_ROLES).isJsonArray()) {
            for (JsonElement re : json.getAsJsonArray(CLAIM_ROLES)) {
                if (re.isJsonPrimitive()) {
                    roles.add(re.getAsString());
                }
            }
        }

        String agsLineitem = null;
        String agsLineitems = null;
        List<String> agsScopes = new ArrayList<>();
        if (json.has(CLAIM_AGS_ENDPOINT) && json.get(CLAIM_AGS_ENDPOINT).isJsonObject()) {
            JsonObject ags = json.getAsJsonObject(CLAIM_AGS_ENDPOINT);
            agsLineitem = getString(ags, "lineitem", null);
            agsLineitems = getString(ags, "lineitems", null);
            if (ags.has("scope") && ags.get("scope").isJsonArray()) {
                for (JsonElement se : ags.getAsJsonArray("scope")) {
                    if (se.isJsonPrimitive()) agsScopes.add(se.getAsString());
                }
            }
        }

        String nrpsMembershipsUrl = null;
        if (json.has(CLAIM_NRPS_SERVICE) && json.get(CLAIM_NRPS_SERVICE).isJsonObject()) {
            JsonObject nrps = json.getAsJsonObject(CLAIM_NRPS_SERVICE);
            nrpsMembershipsUrl = getString(nrps, "context_memberships_url", null);
        }

        Map<String, String> customParams = new HashMap<>();
        if (json.has(CLAIM_CUSTOM) && json.get(CLAIM_CUSTOM).isJsonObject()) {
            JsonObject custom = json.getAsJsonObject(CLAIM_CUSTOM);
            for (Map.Entry<String, JsonElement> entry : custom.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    customParams.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        }

        return new LtiMessageClaims(
                sub,
                iss,
                aud,
                exp,
                iat,
                nonce,
                email,
                name,
                givenName,
                familyName,
                messageType,
                version,
                deploymentId,
                targetLinkUri,
                resourceLinkId,
                resourceLinkTitle,
                resourceLinkDesc,
                contextId,
                contextTitle,
                contextLabel,
                roles,
                agsLineitem,
                agsLineitems,
                agsScopes,
                nrpsMembershipsUrl,
                customParams
        );
    }

    private static String getString(JsonObject obj, String key, String defaultVal) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return defaultVal;
    }
}
