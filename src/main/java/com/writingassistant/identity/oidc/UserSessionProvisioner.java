package com.writingassistant.identity.oidc;

/**
 * Functional callback interface for provisioning OIDC users and issuing sessions in the platform.
 * Sub-interface of {@link com.writingassistant.identity.UserSessionProvisioner} for backwards compatibility.
 */
@FunctionalInterface
public interface UserSessionProvisioner extends com.writingassistant.identity.UserSessionProvisioner {
}
