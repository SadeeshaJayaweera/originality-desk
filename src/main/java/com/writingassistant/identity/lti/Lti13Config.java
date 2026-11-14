package com.writingassistant.identity.lti;

import java.util.Objects;

/**
 * Configuration parameters for LTI 1.3 Advantage integration with Learning Management Systems
 * (Canvas, Moodle, Blackboard Learn, D2L Brightspace).
 */
public class Lti13Config {
    private final String platformIssuer;
    private final String clientId;
    private final String deploymentId;
    private final String oidcAuthUrl;
    private final String jwksUrl;
    private final String tokenUrl;
    private final String toolRedirectUri;
    private final String platformPublicKeyPem;
    private final String defaultWorkspaceId;

    public Lti13Config(Builder builder) {
        this.platformIssuer = Objects.requireNonNull(builder.platformIssuer, "platformIssuer cannot be null");
        this.clientId = Objects.requireNonNull(builder.clientId, "clientId cannot be null");
        this.deploymentId = builder.deploymentId;
        this.oidcAuthUrl = Objects.requireNonNull(builder.oidcAuthUrl, "oidcAuthUrl cannot be null");
        this.jwksUrl = builder.jwksUrl;
        this.tokenUrl = builder.tokenUrl;
        this.toolRedirectUri = Objects.requireNonNull(builder.toolRedirectUri, "toolRedirectUri cannot be null");
        this.platformPublicKeyPem = builder.platformPublicKeyPem;
        this.defaultWorkspaceId = builder.defaultWorkspaceId != null ? builder.defaultWorkspaceId : "default";
    }

    public String getPlatformIssuer() { return platformIssuer; }
    public String getClientId() { return clientId; }
    public String getDeploymentId() { return deploymentId; }
    public String getOidcAuthUrl() { return oidcAuthUrl; }
    public String getJwksUrl() { return jwksUrl; }
    public String getTokenUrl() { return tokenUrl; }
    public String getToolRedirectUri() { return toolRedirectUri; }
    public String getPlatformPublicKeyPem() { return platformPublicKeyPem; }
    public String getDefaultWorkspaceId() { return defaultWorkspaceId; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String platformIssuer;
        private String clientId;
        private String deploymentId;
        private String oidcAuthUrl;
        private String jwksUrl;
        private String tokenUrl;
        private String toolRedirectUri;
        private String platformPublicKeyPem;
        private String defaultWorkspaceId = "default";

        public Builder platformIssuer(String platformIssuer) { this.platformIssuer = platformIssuer; return this; }
        public Builder clientId(String clientId) { this.clientId = clientId; return this; }
        public Builder deploymentId(String deploymentId) { this.deploymentId = deploymentId; return this; }
        public Builder oidcAuthUrl(String oidcAuthUrl) { this.oidcAuthUrl = oidcAuthUrl; return this; }
        public Builder jwksUrl(String jwksUrl) { this.jwksUrl = jwksUrl; return this; }
        public Builder tokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; return this; }
        public Builder toolRedirectUri(String toolRedirectUri) { this.toolRedirectUri = toolRedirectUri; return this; }
        public Builder platformPublicKeyPem(String platformPublicKeyPem) { this.platformPublicKeyPem = platformPublicKeyPem; return this; }
        public Builder defaultWorkspaceId(String defaultWorkspaceId) { this.defaultWorkspaceId = defaultWorkspaceId; return this; }

        public Lti13Config build() {
            return new Lti13Config(this);
        }
    }
}
