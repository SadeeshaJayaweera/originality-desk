package com.writingassistant.identity.oidc;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Service managing the OpenID Connect Single Sign-On flow, state verification,
 * token exchange, user provisioning, and session integration.
 */
public class OidcAuthenticationService {

    private final OidcConfig config;
    private final UserSessionProvisioner provisioner;
    private final HttpClient httpClient;

    public OidcAuthenticationService(OidcConfig config, UserSessionProvisioner provisioner) {
        this(config, provisioner, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    public OidcAuthenticationService(OidcConfig config, UserSessionProvisioner provisioner, HttpClient httpClient) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.provisioner = provisioner;
        this.httpClient = httpClient;
    }

    public OidcConfig getConfig() {
        return config;
    }

    /**
     * Builds the authorization URL to redirect users to the IdP login screen.
     */
    public String buildAuthorizationUrl(String state, String nonce) {
        if (!config.enabled()) {
            throw new IllegalStateException("OIDC authentication is not enabled");
        }

        String authEndpoint = config.issuerUri() + "/protocol/openid-connect/auth";
        return authEndpoint + "?"
                + "client_id=" + URLEncoder.encode(config.clientId(), StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(config.redirectUri(), StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=" + URLEncoder.encode(config.scopes(), StandardCharsets.UTF_8)
                + "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8)
                + "&nonce=" + URLEncoder.encode(nonce, StandardCharsets.UTF_8);
    }

    /**
     * Processes an OIDC authorization callback, validating the ID token, provisioning
     * the user account in the workspace, and creating an authenticated session.
     */
    public Map<String, Object> handleCallback(String idTokenRaw) throws Exception {
        if (!config.enabled()) {
            throw new IllegalStateException("OIDC single sign-on is disabled");
        }

        IdTokenClaims claims = IdTokenClaims.parseJwt(idTokenRaw);
        claims.validate(config.issuerUri(), config.clientId());

        if (claims.email().isBlank()) {
            throw new IllegalArgumentException("OIDC identity provider did not supply an email address");
        }

        String mappedRole = OidcRoleMapper.mapRole(claims, config);

        if (provisioner != null) {
            return provisioner.provisionUserAndSession(claims.email(), claims.name(), mappedRole, config.defaultWorkspaceId());
        }

        // Return profile claims if no provisioner callback supplied
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("email", claims.email());
        result.put("name", claims.name());
        result.put("role", mappedRole);
        return result;
    }
}
