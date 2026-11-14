package com.writingassistant.identity.saml;

import java.util.Objects;

/**
 * Generates compliant SAML 2.0 Service Provider (SP) metadata XML for institutional
 * federation registration (e.g., Shibboleth, InCommon, eduGAIN).
 */
public class SamlMetadataGenerator {

    /**
     * Generates the SP EntityDescriptor XML document according to the SAML-Metadata 2.0 specification.
     */
    public static String generateSpMetadataXml(SamlConfig config) {
        Objects.requireNonNull(config, "config cannot be null");

        StringBuilder sb = new StringBuilder(1024);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<md:EntityDescriptor xmlns:md=\"urn:oasis:names:tc:SAML:2.0:metadata\"\n");
        sb.append("                     entityID=\"").append(escapeXml(config.getSpEntityId())).append("\">\n");
        sb.append("  <md:SPSSODescriptor AuthnRequestsSigned=\"false\"\n");
        sb.append("                      WantAssertionsSigned=\"true\"\n");
        sb.append("                      protocolSupportEnumeration=\"urn:oasis:names:tc:SAML:2.0:protocol\">\n");
        
        sb.append("    <md:NameIDFormat>urn:oasis:names:tc:SAML:2.0:nameid-format:persistent</md:NameIDFormat>\n");
        sb.append("    <md:NameIDFormat>urn:oasis:names:tc:SAML:2.0:nameid-format:transient</md:NameIDFormat>\n");
        sb.append("    <md:NameIDFormat>urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress</md:NameIDFormat>\n");
        
        sb.append("    <md:AssertionConsumerService Binding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\"\n");
        sb.append("                                Location=\"").append(escapeXml(config.getAcsUrl())).append("\"\n");
        sb.append("                                index=\"0\"\n");
        sb.append("                                isDefault=\"true\"/>\n");

        sb.append("    <md:AttributeConsumingService index=\"1\" isDefault=\"true\">\n");
        sb.append("      <md:ServiceName xml:lang=\"en\">Originality Desk</md:ServiceName>\n");
        sb.append("      <md:ServiceDescription xml:lang=\"en\">Originality Desk Academic Integrity Platform</md:ServiceDescription>\n");
        sb.append("      <md:RequestedAttribute Name=\"").append(SamlConfig.ATTR_MAIL_OID).append("\"\n");
        sb.append("                             FriendlyName=\"").append(SamlConfig.ATTR_MAIL_FRIENDLY).append("\"\n");
        sb.append("                             NameFormat=\"urn:oasis:names:tc:SAML:2.0:attrname-format:uri\"\n");
        sb.append("                             isRequired=\"true\"/>\n");
        sb.append("      <md:RequestedAttribute Name=\"").append(SamlConfig.ATTR_DISPLAY_NAME_OID).append("\"\n");
        sb.append("                             FriendlyName=\"").append(SamlConfig.ATTR_DISPLAY_NAME_FRIENDLY).append("\"\n");
        sb.append("                             NameFormat=\"urn:oasis:names:tc:SAML:2.0:attrname-format:uri\"\n");
        sb.append("                             isRequired=\"false\"/>\n");
        sb.append("      <md:RequestedAttribute Name=\"").append(SamlConfig.ATTR_AFFILIATION_OID).append("\"\n");
        sb.append("                             FriendlyName=\"").append(SamlConfig.ATTR_AFFILIATION_FRIENDLY).append("\"\n");
        sb.append("                             NameFormat=\"urn:oasis:names:tc:SAML:2.0:attrname-format:uri\"\n");
        sb.append("                             isRequired=\"true\"/>\n");
        sb.append("    </md:AttributeConsumingService>\n");

        sb.append("  </md:SPSSODescriptor>\n");
        sb.append("</md:EntityDescriptor>\n");

        return sb.toString();
    }

    private static String escapeXml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
