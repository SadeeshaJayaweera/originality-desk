package com.writingassistant.identity.lti;

import com.writingassistant.identity.UserSessionProvisioner;

import java.util.Map;
import java.util.Objects;

/**
 * Service coordinating LTI 1.3 Advantage authentication, LMS course context mapping,
 * and user session provisioning.
 */
public class Lti13Service {

    private final Lti13Config config;
    private final LtiOidcLaunchHandler launchHandler;
    private final UserSessionProvisioner sessionProvisioner;

    public Lti13Service(Lti13Config config, UserSessionProvisioner sessionProvisioner) {
        this.config = Objects.requireNonNull(config, "Lti13Config cannot be null");
        this.sessionProvisioner = Objects.requireNonNull(sessionProvisioner, "UserSessionProvisioner cannot be null");
        this.launchHandler = new LtiOidcLaunchHandler(config);
    }

    /**
     * Handles third-party initiated OIDC login from LMS.
     */
    public LtiOidcLaunchHandler.InitiationResult initiateLogin(String iss, String loginHint, String targetLinkUri, String ltiMessageHint) throws LtiException {
        return launchHandler.initiateLogin(iss, loginHint, targetLinkUri, ltiMessageHint);
    }

    /**
     * Validates LTI 1.3 launch token, maps course context to workspace, assigns role, and provisions session.
     */
    public LtiLaunchResult handleLaunch(String idToken, String expectedNonce, String fallbackWorkspaceId) throws Exception {
        LtiMessageClaims claims = launchHandler.validateLaunchToken(idToken, expectedNonce);

        String email = claims.email();
        if (email == null || email.isBlank()) {
            email = claims.subject() + "@" + sanitizeDomain(config.getPlatformIssuer());
        }

        String name = claims.name();
        if (name == null || name.isBlank()) {
            if (claims.givenName() != null || claims.familyName() != null) {
                name = ((claims.givenName() != null ? claims.givenName() : "") + " "
                        + (claims.familyName() != null ? claims.familyName() : "")).trim();
            } else {
                name = email;
            }
        }

        String role = LtiRoleMapper.mapRole(claims.roles());

        // Map LMS Course Context to Workspace
        String workspaceId;
        if (claims.contextId() != null && !claims.contextId().isBlank()) {
            workspaceId = "lms_" + claims.contextId().replaceAll("[^a-zA-Z0-9_\\-]", "_");
        } else if (fallbackWorkspaceId != null && !fallbackWorkspaceId.isBlank()) {
            workspaceId = fallbackWorkspaceId;
        } else {
            workspaceId = config.getDefaultWorkspaceId();
        }

        Map<String, Object> sessionData = sessionProvisioner.provisionUserAndSession(email, name, role, workspaceId);

        return new LtiLaunchResult(
                email,
                name,
                role,
                claims.subject(),
                workspaceId,
                claims.resourceLinkId(),
                claims.resourceLinkTitle(),
                claims.contextId(),
                claims.contextTitle(),
                sessionData,
                claims
        );
    }

    private String sanitizeDomain(String url) {
        if (url == null) return "lms.local";
        return url.replace("https://", "").replace("http://", "").replaceAll("[/:].*", "");
    }

    public Lti13Config getConfig() {
        return config;
    }
}
