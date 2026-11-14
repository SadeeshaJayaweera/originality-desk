package com.writingassistant.identity.saml;

import com.writingassistant.identity.UserSessionProvisioner;

import java.util.Map;
import java.util.Objects;

/**
 * Service orchestrating SAML 2.0 institutional authentication, metadata publication,
 * and Just-in-Time user provisioning.
 */
public class SamlAuthenticationService {

    private final SamlConfig config;
    private final UserSessionProvisioner sessionProvisioner;

    public SamlAuthenticationService(SamlConfig config, UserSessionProvisioner sessionProvisioner) {
        this.config = Objects.requireNonNull(config, "SamlConfig cannot be null");
        this.sessionProvisioner = Objects.requireNonNull(sessionProvisioner, "UserSessionProvisioner cannot be null");
    }

    /**
     * Generates Service Provider SAML 2.0 metadata XML.
     */
    public String getSpMetadataXml() {
        return SamlMetadataGenerator.generateSpMetadataXml(config);
    }

    /**
     * Initiates SAML SSO by building an AuthnRequest with HTTP-Redirect binding.
     *
     * @param relayState optional state or redirect URI after login
     * @return AuthnRequestResult with redirect URL, ID, and raw XML
     */
    public SamlAuthnRequestBuilder.AuthnRequestResult initiateLogin(String relayState) {
        return SamlAuthnRequestBuilder.buildRedirectAuthnRequest(config, relayState);
    }

    /**
     * Processes an incoming SAML 2.0 Response, verifies assertions and attributes,
     * provisions user account if needed, and issues a platform session.
     *
     * @param samlResponseRaw Base64 or XML SAMLResponse parameter from HTTP POST
     * @param expectedRequestId Optional original AuthnRequest ID for InResponseTo validation
     * @param targetWorkspaceId Optional target workspace (falls back to config.getDefaultWorkspaceId())
     * @return Authenticated user and session details
     * @throws SamlValidationException if validation fails
     */
    public SamlAuthResult processResponse(String samlResponseRaw, String expectedRequestId, String targetWorkspaceId) throws Exception {
        SamlAssertionClaims claims = SamlResponseValidator.validateResponse(samlResponseRaw, expectedRequestId, config);

        String email = claims.getEmail();
        if (email == null || email.isBlank()) {
            throw new SamlValidationException("SAML assertion does not contain an email address or valid NameID");
        }

        String displayName = claims.getDisplayName();
        String role = SamlRoleMapper.mapRole(claims);
        String workspaceId = (targetWorkspaceId != null && !targetWorkspaceId.isBlank()) ? targetWorkspaceId : config.getDefaultWorkspaceId();

        Map<String, Object> sessionData = sessionProvisioner.provisionUserAndSession(email, displayName, role, workspaceId);

        return new SamlAuthResult(
                email,
                displayName,
                role,
                claims.getSubjectNameId(),
                workspaceId,
                sessionData,
                claims
        );
    }

    public SamlConfig getConfig() {
        return config;
    }
}
