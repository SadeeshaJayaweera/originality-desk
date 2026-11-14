package com.writingassistant.identity.lti;

import java.util.List;

/**
 * Maps LTI 1.3 Advantage / LIS 2.0 role vocabularies to Originality Desk platform roles
 * (INSTITUTION_ADMIN, REVIEWER, MEMBER).
 */
public final class LtiRoleMapper {

    private LtiRoleMapper() {}

    /**
     * Maps an LTI role list to the highest applicable platform privilege tier.
     */
    public static String mapRole(List<String> ltiRoles) {
        if (ltiRoles == null || ltiRoles.isEmpty()) {
            return "MEMBER";
        }

        // 1. Check for Platform / Institutional Administrator
        for (String r : ltiRoles) {
            String clean = cleanRole(r);
            if (clean.endsWith("administrator") || clean.endsWith("sysadmin") || clean.equals("admin")) {
                return "INSTITUTION_ADMIN";
            }
        }

        // 2. Check for Course Instructor / Teaching Assistant / Grader
        for (String r : ltiRoles) {
            String clean = cleanRole(r);
            if (clean.endsWith("instructor") || clean.endsWith("teachingassistant")
                    || clean.endsWith("grader") || clean.endsWith("teacher")
                    || clean.contains("faculty") || clean.contains("lecturer")) {
                return "REVIEWER";
            }
        }

        // 3. Default to Student / Learner / Member
        return "MEMBER";
    }

    private static String cleanRole(String r) {
        if (r == null) return "";
        String s = r.trim().toLowerCase();
        int hashIdx = s.lastIndexOf('#');
        if (hashIdx >= 0 && hashIdx < s.length() - 1) {
            s = s.substring(hashIdx + 1);
        }
        int slashIdx = s.lastIndexOf('/');
        if (slashIdx >= 0 && slashIdx < s.length() - 1) {
            s = s.substring(slashIdx + 1);
        }
        return s.replaceAll("[^a-z0-9]", "");
    }
}
