package com.writingassistant.identity.oidc;

import java.util.Set;

/**
 * Maps identity provider claims and Keycloak realm roles to internal platform permission tiers.
 */
public final class OidcRoleMapper {

    private OidcRoleMapper() {}

    /**
     * Determines platform user role from OIDC claims and configured role names.
     *
     * @param claims Extracted ID token claims.
     * @param config OIDC configuration containing institutional role mapping names.
     * @return Internal role string: 'INSTITUTION_ADMIN', 'REVIEWER', or 'MEMBER'.
     */
    public static String mapRole(IdTokenClaims claims, OidcConfig config) {
        if (claims == null || claims.roles().isEmpty()) {
            return "MEMBER";
        }

        Set<String> roles = claims.roles();
        String adminTarget = config.adminRoleName().toLowerCase();
        String reviewerTarget = config.reviewerRoleName().toLowerCase();

        for (String r : roles) {
            String lower = r.toLowerCase();
            if (lower.equals(adminTarget) || lower.equals("admin") || lower.equals("realm-admin") || lower.equals("institution_admin")) {
                return "INSTITUTION_ADMIN";
            }
        }

        for (String r : roles) {
            String lower = r.toLowerCase();
            if (lower.equals(reviewerTarget) || lower.equals("reviewer") || lower.equals("instructor") || lower.equals("grader")) {
                return "REVIEWER";
            }
        }

        return "MEMBER";
    }
}
