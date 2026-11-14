package com.writingassistant.identity.saml;

import java.util.Map;

/**
 * Result of a successful SAML 2.0 authentication and session provisioning.
 */
public class SamlAuthResult {
    private final String email;
    private final String displayName;
    private final String role;
    private final String subjectNameId;
    private final String workspaceId;
    private final Map<String, Object> sessionData;
    private final SamlAssertionClaims claims;

    public SamlAuthResult(String email,
                          String displayName,
                          String role,
                          String subjectNameId,
                          String workspaceId,
                          Map<String, Object> sessionData,
                          SamlAssertionClaims claims) {
        this.email = email;
        this.displayName = displayName;
        this.role = role;
        this.subjectNameId = subjectNameId;
        this.workspaceId = workspaceId;
        this.sessionData = sessionData;
        this.claims = claims;
    }

    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getRole() { return role; }
    public String getSubjectNameId() { return subjectNameId; }
    public String getWorkspaceId() { return workspaceId; }
    public Map<String, Object> getSessionData() { return sessionData; }
    public SamlAssertionClaims getClaims() { return claims; }
}
