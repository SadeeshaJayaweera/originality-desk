package com.writingassistant.identity.lti;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles LTI 1.3 Advantage OIDC Initiation and Launch Token verification.
 */
public class LtiOidcLaunchHandler {

    private final Lti13Config config;
    private final ConcurrentHashMap<String, Long> activeNonces = new ConcurrentHashMap<>();

    public record InitiationResult(String authRedirectUrl, String state, String nonce) {}

    public LtiOidcLaunchHandler(Lti13Config config) {
        this.config = Objects.requireNonNull(config, "Lti13Config cannot be null");
    }

    /**
     * Handles Step 1 of LTI 1.3: Third-Party Initiated OIDC Login.
     *
     * @param iss Platform issuer URI from LMS
     * @param loginHint User identifier opaque to the tool
     * @param targetLinkUri Intended tool launch destination
     * @param ltiMessageHint LMS-specific correlation token
     * @return InitiationResult containing authorization redirect URL, state, and nonce
     * @throws LtiException if issuer validation fails
     */
    public InitiationResult initiateLogin(String iss, String loginHint, String targetLinkUri, String ltiMessageHint) throws LtiException {
        if (iss == null || !config.getPlatformIssuer().equals(iss.trim())) {
            throw new LtiException("Unrecognized or untrusted LTI Platform Issuer: " + iss);
        }
        if (loginHint == null || loginHint.isBlank()) {
            throw new LtiException("Missing required parameter 'login_hint'");
        }

        String state = "lti_state_" + UUID.randomUUID().toString().replace("-", "");
        String nonce = "lti_nonce_" + UUID.randomUUID().toString().replace("-", "");
        activeNonces.put(nonce, Instant.now().plusSeconds(600).getEpochSecond());

        StringBuilder url = new StringBuilder(config.getOidcAuthUrl());
        url.append(config.getOidcAuthUrl().contains("?") ? "&" : "?");
        url.append("response_type=id_token");
        url.append("&scope=").append(URLEncoder.encode("openid", StandardCharsets.UTF_8));
        url.append("&response_mode=form_post");
        url.append("&prompt=none");
        url.append("&client_id=").append(URLEncoder.encode(config.getClientId(), StandardCharsets.UTF_8));
        url.append("&redirect_uri=").append(URLEncoder.encode(config.getToolRedirectUri(), StandardCharsets.UTF_8));
        url.append("&login_hint=").append(URLEncoder.encode(loginHint, StandardCharsets.UTF_8));
        url.append("&state=").append(URLEncoder.encode(state, StandardCharsets.UTF_8));
        url.append("&nonce=").append(URLEncoder.encode(nonce, StandardCharsets.UTF_8));

        if (ltiMessageHint != null && !ltiMessageHint.isBlank()) {
            url.append("&lti_message_hint=").append(URLEncoder.encode(ltiMessageHint, StandardCharsets.UTF_8));
        }

        return new InitiationResult(url.toString(), state, nonce);
    }

    /**
     * Handles Step 3 of LTI 1.3: Validates and parses the LtiResourceLinkRequest id_token.
     *
     * @param idToken Signed JWT provided in form_post from LMS
     * @param expectedNonce Optional nonce for replay validation
     * @return Validated LtiMessageClaims
     * @throws LtiException if cryptographic or protocol validation fails
     */
    public LtiMessageClaims validateLaunchToken(String idToken, String expectedNonce) throws LtiException {
        if (idToken == null || idToken.isBlank()) {
            throw new LtiException("Missing required LTI launch parameter 'id_token'");
        }

        String[] parts = idToken.split("\\.");
        if (parts.length < 2) {
            throw new LtiException("Malformed LTI JWT: expected at least header and payload segments");
        }

        // 1. Verify Cryptographic Signature if platform public key is configured
        if (config.getPlatformPublicKeyPem() != null && !config.getPlatformPublicKeyPem().isBlank()) {
            if (parts.length < 3) {
                throw new LtiException("Missing signature segment in LTI JWT");
            }
            verifyJwtSignature(parts[0], parts[1], parts[2], config.getPlatformPublicKeyPem());
        }

        // 2. Parse claims
        LtiMessageClaims claims;
        try {
            claims = LtiMessageClaims.parse(idToken);
        } catch (Exception e) {
            throw new LtiException("Failed to parse LTI launch token claims: " + e.getMessage(), e);
        }

        // 3. Validate Expiration
        long now = Instant.now().getEpochSecond();
        if (claims.expirationTime() > 0 && claims.expirationTime() < now - 60) {
            throw new LtiException("LTI launch token has expired (exp=" + claims.expirationTime() + ", now=" + now + ")");
        }

        // 4. Validate Issuer
        if (!config.getPlatformIssuer().equals(claims.issuer())) {
            throw new LtiException("Issuer mismatch: expected " + config.getPlatformIssuer() + " but got " + claims.issuer());
        }

        // 5. Validate Audience
        if (!config.getClientId().equals(claims.audience())) {
            throw new LtiException("Audience mismatch: expected " + config.getClientId() + " but got " + claims.audience());
        }

        // 6. Validate Nonce
        if (expectedNonce != null && !expectedNonce.isBlank()) {
            if (!expectedNonce.equals(claims.nonce())) {
                throw new LtiException("Nonce mismatch in launch token: replay or session tampering detected");
            }
            activeNonces.remove(expectedNonce);
        }

        // 7. Validate Deployment ID
        if (config.getDeploymentId() != null && !config.getDeploymentId().isBlank()) {
            if (!config.getDeploymentId().equals(claims.deploymentId())) {
                throw new LtiException("Deployment ID mismatch: expected " + config.getDeploymentId() + " but got " + claims.deploymentId());
            }
        }

        // 8. Validate LTI Message Type and Version
        if (!"LtiResourceLinkRequest".equals(claims.messageType()) && !"LtiDeepLinkingRequest".equals(claims.messageType())) {
            throw new LtiException("Unsupported LTI message type: " + claims.messageType());
        }
        if (!"1.3.0".equals(claims.version())) {
            throw new LtiException("Unsupported LTI version: expected 1.3.0, got " + claims.version());
        }

        return claims;
    }

    private void verifyJwtSignature(String headerB64, String payloadB64, String signatureB64, String publicKeyPem) throws LtiException {
        try {
            byte[] signingInput = (headerB64 + "." + payloadB64).getBytes(StandardCharsets.US_ASCII);
            byte[] signatureBytes = Base64.getUrlDecoder().decode(signatureB64);
            PublicKey publicKey = parsePublicKey(publicKeyPem);

            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(signingInput);
            if (!sig.verify(signatureBytes)) {
                throw new LtiException("Cryptographic verification of LTI JWT signature failed");
            }
        } catch (LtiException e) {
            throw e;
        } catch (Exception e) {
            throw new LtiException("Error validating RSA signature: " + e.getMessage(), e);
        }
    }

    private PublicKey parsePublicKey(String pem) throws Exception {
        String clean = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s+", "");
        byte[] bytes = Base64.getDecoder().decode(clean);

        if (pem.contains("BEGIN CERTIFICATE")) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(bytes));
            return cert.getPublicKey();
        }

        X509EncodedKeySpec spec = new X509EncodedKeySpec(bytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }
}
