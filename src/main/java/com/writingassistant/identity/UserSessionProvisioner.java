package com.writingassistant.identity;

import java.util.Map;

/**
 * Common functional callback interface for provisioning institutional users (OIDC, SAML, LTI, SCIM)
 * and issuing authenticated sessions in the platform.
 */
@FunctionalInterface
public interface UserSessionProvisioner {
    Map<String, Object> provisionUserAndSession(String email, String name, String role, String defaultWorkspaceId) throws Exception;
}
