package com.writingassistant.identity.oidc;

import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

/**
 * Configuration for OpenID Connect (OIDC) Single Sign-On, supporting Keycloak
 * and standard institutional identity providers.
 */
public record OidcConfig(
        boolean enabled,
        String issuerUri,
        String clientId,
        String clientSecret,
        String redirectUri,
        String scopes,
        String adminRoleName,
        String reviewerRoleName,
        String defaultWorkspaceId
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String DEFAULT_SCOPES = "openid profile email roles";
    public static final String DEFAULT_ADMIN_ROLE = "originality-admin";
    public static final String DEFAULT_REVIEWER_ROLE = "originality-reviewer";

    public OidcConfig {
        scopes = scopes != null && !scopes.isBlank() ? scopes : DEFAULT_SCOPES;
        adminRoleName = adminRoleName != null && !adminRoleName.isBlank() ? adminRoleName : DEFAULT_ADMIN_ROLE;
        reviewerRoleName = reviewerRoleName != null && !reviewerRoleName.isBlank() ? reviewerRoleName : DEFAULT_REVIEWER_ROLE;
        defaultWorkspaceId = defaultWorkspaceId != null ? defaultWorkspaceId : "default";
    }

    public static OidcConfig disabled() {
        return new OidcConfig(false, "", "", "", "", DEFAULT_SCOPES, DEFAULT_ADMIN_ROLE, DEFAULT_REVIEWER_ROLE, "default");
    }

    public static OidcConfig keycloak(String issuerUri, String clientId, String clientSecret, String redirectUri) {
        return new OidcConfig(true, issuerUri, clientId, clientSecret, redirectUri, DEFAULT_SCOPES, DEFAULT_ADMIN_ROLE, DEFAULT_REVIEWER_ROLE, "default");
    }
}
