package com.writingassistant.identity.saml;

import java.util.List;

/**
 * Maps Higher Education SAML 2.0 eduPersonScopedAffiliation and entitlements
 * to internal Originality Desk platform roles (INSTITUTION_ADMIN, REVIEWER, MEMBER).
 */
public final class SamlRoleMapper {

    private SamlRoleMapper() {}

    /**
     * Determines platform user role from SAML assertion claims.
     *
     * @param claims Extracted and validated assertion claims
     * @return Internal role string: 'INSTITUTION_ADMIN', 'REVIEWER', or 'MEMBER'
     */
    public static String mapRole(SamlAssertionClaims claims) {
        if (claims == null) {
            return "MEMBER";
        }

        List<String> affiliations = claims.getAffiliations();
        if (affiliations == null || affiliations.isEmpty()) {
            return "MEMBER";
        }

        // 1. Check for Administrative Affiliation / Entitlement
        for (String raw : affiliations) {
            String primary = extractPrimaryAffiliation(raw);
            if (primary.equals("admin") || primary.equals("administrator") || primary.equals("institution_admin")
                    || primary.contains("admin") || primary.contains("superuser")) {
                return "INSTITUTION_ADMIN";
            }
        }

        // 2. Check for Academic Reviewer / Instructor / Faculty Affiliation
        for (String raw : affiliations) {
            String primary = extractPrimaryAffiliation(raw);
            if (primary.equals("faculty") || primary.equals("instructor") || primary.equals("teacher")
                    || primary.equals("professor") || primary.equals("lecturer") || primary.equals("reviewer")
                    || primary.equals("staff") || primary.equals("grader")) {
                return "REVIEWER";
            }
        }

        // 3. Default to standard Member / Student
        return "MEMBER";
    }

    private static String extractPrimaryAffiliation(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase();
        int atIdx = s.indexOf('@');
        if (atIdx > 0) {
            s = s.substring(0, atIdx).trim();
        }
        return s;
    }
}
