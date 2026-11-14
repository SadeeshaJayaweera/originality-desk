package com.writingassistant.identity.lti;

import java.util.Map;

/**
 * Result of an authenticated LTI 1.3 Advantage launch and provisioned platform session.
 */
public class LtiLaunchResult {
    private final String email;
    private final String name;
    private final String role;
    private final String subject;
    private final String workspaceId;
    private final String resourceLinkId;
    private final String resourceLinkTitle;
    private final String contextId;
    private final String contextTitle;
    private final Map<String, Object> sessionData;
    private final LtiMessageClaims claims;

    public LtiLaunchResult(String email,
                           String name,
                           String role,
                           String subject,
                           String workspaceId,
                           String resourceLinkId,
                           String resourceLinkTitle,
                           String contextId,
                           String contextTitle,
                           Map<String, Object> sessionData,
                           LtiMessageClaims claims) {
        this.email = email;
        this.name = name;
        this.role = role;
        this.subject = subject;
        this.workspaceId = workspaceId;
        this.resourceLinkId = resourceLinkId;
        this.resourceLinkTitle = resourceLinkTitle;
        this.contextId = contextId;
        this.contextTitle = contextTitle;
        this.sessionData = sessionData;
        this.claims = claims;
    }

    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getRole() { return role; }
    public String getSubject() { return subject; }
    public String getWorkspaceId() { return workspaceId; }
    public String getResourceLinkId() { return resourceLinkId; }
    public String getResourceLinkTitle() { return resourceLinkTitle; }
    public String getContextId() { return contextId; }
    public String getContextTitle() { return contextTitle; }
    public Map<String, Object> getSessionData() { return sessionData; }
    public LtiMessageClaims getClaims() { return claims; }
}
