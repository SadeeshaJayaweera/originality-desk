package com.writingassistant.identity.saml;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration parameters for SAML 2.0 Service Provider (SP) institutional authentication.
 */
public class SamlConfig {
    private final String spEntityId;
    private final String acsUrl;
    private final String idpEntityId;
    private final String idpSsoUrl;
    private final String idpCertificatePem;
    private final int clockSkewSeconds;
    private final String defaultWorkspaceId;
    private final Map<String, String> attributeMappings;

    // Standard Higher Education SAML / Shibboleth Attribute OIDs and Friendly Names
    public static final String ATTR_MAIL_OID = "urn:oid:0.9.2342.19200300.100.1.3";
    public static final String ATTR_MAIL_FRIENDLY = "mail";
    public static final String ATTR_DISPLAY_NAME_OID = "urn:oid:2.16.840.1.113730.3.1.241";
    public static final String ATTR_DISPLAY_NAME_FRIENDLY = "displayName";
    public static final String ATTR_GIVEN_NAME_OID = "urn:oid:2.5.4.42";
    public static final String ATTR_GIVEN_NAME_FRIENDLY = "givenName";
    public static final String ATTR_SURNAME_OID = "urn:oid:2.5.4.4";
    public static final String ATTR_SURNAME_FRIENDLY = "sn";
    public static final String ATTR_AFFILIATION_OID = "urn:oid:1.3.6.1.4.1.5923.1.1.1.9";
    public static final String ATTR_AFFILIATION_FRIENDLY = "eduPersonScopedAffiliation";
    public static final String ATTR_PRIMARY_AFFILIATION_OID = "urn:oid:1.3.6.1.4.1.5923.1.1.1.5";
    public static final String ATTR_PRIMARY_AFFILIATION_FRIENDLY = "eduPersonPrimaryAffiliation";
    public static final String ATTR_EPPN_OID = "urn:oid:1.3.6.1.4.1.5923.1.1.1.6";
    public static final String ATTR_EPPN_FRIENDLY = "eduPersonPrincipalName";

    public SamlConfig(Builder builder) {
        this.spEntityId = Objects.requireNonNull(builder.spEntityId, "spEntityId cannot be null");
        this.acsUrl = Objects.requireNonNull(builder.acsUrl, "acsUrl cannot be null");
        this.idpEntityId = Objects.requireNonNull(builder.idpEntityId, "idpEntityId cannot be null");
        this.idpSsoUrl = Objects.requireNonNull(builder.idpSsoUrl, "idpSsoUrl cannot be null");
        this.idpCertificatePem = builder.idpCertificatePem;
        this.clockSkewSeconds = builder.clockSkewSeconds > 0 ? builder.clockSkewSeconds : 180;
        this.defaultWorkspaceId = builder.defaultWorkspaceId != null ? builder.defaultWorkspaceId : "default";
        this.attributeMappings = Collections.unmodifiableMap(new HashMap<>(builder.attributeMappings));
    }

    public String getSpEntityId() { return spEntityId; }
    public String getAcsUrl() { return acsUrl; }
    public String getIdpEntityId() { return idpEntityId; }
    public String getIdpSsoUrl() { return idpSsoUrl; }
    public String getIdpCertificatePem() { return idpCertificatePem; }
    public int getClockSkewSeconds() { return clockSkewSeconds; }
    public String getDefaultWorkspaceId() { return defaultWorkspaceId; }
    public Map<String, String> getAttributeMappings() { return attributeMappings; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String spEntityId;
        private String acsUrl;
        private String idpEntityId;
        private String idpSsoUrl;
        private String idpCertificatePem;
        private int clockSkewSeconds = 180;
        private String defaultWorkspaceId = "default";
        private final Map<String, String> attributeMappings = new HashMap<>();

        public Builder() {
            // Default mappings covering standard Shibboleth / InCommon schemas
            attributeMappings.put("email", ATTR_MAIL_FRIENDLY);
            attributeMappings.put("name", ATTR_DISPLAY_NAME_FRIENDLY);
            attributeMappings.put("affiliation", ATTR_AFFILIATION_FRIENDLY);
        }

        public Builder spEntityId(String spEntityId) { this.spEntityId = spEntityId; return this; }
        public Builder acsUrl(String acsUrl) { this.acsUrl = acsUrl; return this; }
        public Builder idpEntityId(String idpEntityId) { this.idpEntityId = idpEntityId; return this; }
        public Builder idpSsoUrl(String idpSsoUrl) { this.idpSsoUrl = idpSsoUrl; return this; }
        public Builder idpCertificatePem(String idpCertificatePem) { this.idpCertificatePem = idpCertificatePem; return this; }
        public Builder clockSkewSeconds(int clockSkewSeconds) { this.clockSkewSeconds = clockSkewSeconds; return this; }
        public Builder defaultWorkspaceId(String defaultWorkspaceId) { this.defaultWorkspaceId = defaultWorkspaceId; return this; }
        public Builder mapAttribute(String platformField, String samlAttributeName) {
            this.attributeMappings.put(platformField, samlAttributeName);
            return this;
        }

        public SamlConfig build() {
            return new SamlConfig(this);
        }
    }
}
