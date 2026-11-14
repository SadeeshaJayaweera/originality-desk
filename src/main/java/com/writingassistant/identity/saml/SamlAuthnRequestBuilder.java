package com.writingassistant.identity.saml;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Builds SAML 2.0 AuthnRequest documents and encodes them for HTTP-Redirect or HTTP-POST bindings.
 */
public class SamlAuthnRequestBuilder {

    public static class AuthnRequestResult {
        private final String requestId;
        private final String xml;
        private final String redirectUrl;
        private final String relayState;

        public AuthnRequestResult(String requestId, String xml, String redirectUrl, String relayState) {
            this.requestId = requestId;
            this.xml = xml;
            this.redirectUrl = redirectUrl;
            this.relayState = relayState;
        }

        public String getRequestId() { return requestId; }
        public String getXml() { return xml; }
        public String getRedirectUrl() { return redirectUrl; }
        public String getRelayState() { return relayState; }
    }

    /**
     * Builds an AuthnRequest and constructs the complete HTTP-Redirect URL for initiating SSO with the IdP.
     */
    public static AuthnRequestResult buildRedirectAuthnRequest(SamlConfig config, String relayState) {
        Objects.requireNonNull(config, "config cannot be null");
        String requestId = "_" + UUID.randomUUID().toString().replace("-", "");
        String issueInstant = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();

        String xml = buildAuthnRequestXml(config, requestId, issueInstant);
        String deflatedBase64 = deflateAndBase64Encode(xml);

        StringBuilder url = new StringBuilder(config.getIdpSsoUrl());
        url.append(config.getIdpSsoUrl().contains("?") ? "&" : "?");
        url.append("SAMLRequest=").append(URLEncoder.encode(deflatedBase64, StandardCharsets.UTF_8));

        if (relayState != null && !relayState.isBlank()) {
            url.append("&RelayState=").append(URLEncoder.encode(relayState, StandardCharsets.UTF_8));
        }

        return new AuthnRequestResult(requestId, xml, url.toString(), relayState);
    }

    /**
     * Constructs the canonical SAML 2.0 AuthnRequest XML string.
     */
    public static String buildAuthnRequestXml(SamlConfig config, String requestId, String issueInstant) {
        return "<samlp:AuthnRequest xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\"\n"
                + "                    xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\"\n"
                + "                    ID=\"" + escapeXml(requestId) + "\"\n"
                + "                    Version=\"2.0\"\n"
                + "                    IssueInstant=\"" + escapeXml(issueInstant) + "\"\n"
                + "                    Destination=\"" + escapeXml(config.getIdpSsoUrl()) + "\"\n"
                + "                    AssertionConsumerServiceURL=\"" + escapeXml(config.getAcsUrl()) + "\"\n"
                + "                    ProtocolBinding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\">\n"
                + "  <saml:Issuer>" + escapeXml(config.getSpEntityId()) + "</saml:Issuer>\n"
                + "  <samlp:NameIDPolicy Format=\"urn:oasis:names:tc:SAML:2.0:nameid-format:persistent\"\n"
                + "                     AllowCreate=\"true\"/>\n"
                + "</samlp:AuthnRequest>";
    }

    /**
     * Deflates the XML string using RFC 1951 raw deflate (nowrap=true) and encodes to Base64
     * as required by SAML 2.0 HTTP-Redirect Binding.
     */
    public static String deflateAndBase64Encode(String input) {
        byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.DEFLATED, true); // true for raw DEFLATE without zlib wrapper
        try (DeflaterOutputStream dos = new DeflaterOutputStream(baos, deflater)) {
            dos.write(inputBytes);
            dos.finish();
        } catch (IOException e) {
            throw new RuntimeException("Failed to deflate SAML AuthnRequest", e);
        }
        return Base64.getEncoder().encodeToString(baos.toByteArray());
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
