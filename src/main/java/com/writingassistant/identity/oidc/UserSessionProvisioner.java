package com.writingassistant.identity.oidc;

import java.util.Map;

/**
 * Functional callback interface for provisioning OIDC users and issuing sessions in the platform.
 */
@FunctionalInterface
public interface UserSessionProvisioner {
    Map<String, Object> provisionUserAndSession(String email, String name, String role, String defaultWorkspaceId) throws Exception;
}
