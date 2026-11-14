package com.writingassistant.identity.saml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Validates incoming SAML 2.0 Response documents, enforces security conditions,
 * checks cryptographic signatures, and extracts assertions and claims.
 */
public class SamlResponseValidator {

    private static final String STATUS_SUCCESS = "urn:oasis:names:tc:SAML:2.0:status:Success";

    /**
     * Parses and validates a SAML 2.0 Response document (Base64-encoded or raw XML string).
     *
     * @param samlResponseRaw the raw SAMLResponse parameter (Base64 encoded or raw XML)
     * @param expectedRequestId optional expected request ID for InResponseTo validation (null to skip)
     * @param config the SAML SP configuration
     * @return validated assertion claims
     * @throws SamlValidationException if validation fails
     */
    public static SamlAssertionClaims validateResponse(String samlResponseRaw, String expectedRequestId, SamlConfig config) throws SamlValidationException {
        Objects.requireNonNull(samlResponseRaw, "SAML response cannot be null");
        Objects.requireNonNull(config, "SamlConfig cannot be null");

        String xml = decodeIfBase64(samlResponseRaw.trim());
        Document doc = parseSecureXml(xml);

        Element root = doc.getDocumentElement();
        String rootName = getLocalName(root);
        if (!"Response".equalsIgnoreCase(rootName)) {
            throw new SamlValidationException("Invalid root element: expected Response, got " + rootName);
        }

        // 1. Validate StatusCode
        validateStatusCode(root);

        // 2. Validate InResponseTo
        if (expectedRequestId != null && !expectedRequestId.isBlank()) {
            String inResponseTo = root.getAttribute("InResponseTo");
            if (inResponseTo != null && !inResponseTo.isBlank() && !expectedRequestId.equals(inResponseTo)) {
                throw new SamlValidationException("InResponseTo mismatch: expected " + expectedRequestId + " but got " + inResponseTo);
            }
        }

        // 3. Locate Assertion
        Element assertion = findChildElement(root, "Assertion");
        if (assertion == null) {
            throw new SamlValidationException("No SAML Assertion found in Response");
        }

        // 4. Validate Issuer
        Element issuerElem = findChildElement(assertion, "Issuer");
        if (issuerElem == null) {
            issuerElem = findChildElement(root, "Issuer");
        }
        if (issuerElem == null) {
            throw new SamlValidationException("Missing Issuer in SAML Response/Assertion");
        }
        String issuer = issuerElem.getTextContent().trim();
        if (!config.getIdpEntityId().equals(issuer)) {
            throw new SamlValidationException("Issuer mismatch: expected " + config.getIdpEntityId() + " but got " + issuer);
        }

        // 5. Validate Conditions & Lifetime
        validateConditions(assertion, config);

        // 6. Validate Subject
        Element subjectElem = findChildElement(assertion, "Subject");
        if (subjectElem == null) {
            throw new SamlValidationException("Missing Subject in SAML Assertion");
        }
        Element nameIdElem = findChildElement(subjectElem, "NameID");
        String subjectNameId = nameIdElem != null ? nameIdElem.getTextContent().trim() : null;
        String nameIdFormat = nameIdElem != null ? nameIdElem.getAttribute("Format") : null;

        if (subjectNameId == null || subjectNameId.isBlank()) {
            throw new SamlValidationException("Missing NameID in SAML Subject");
        }

        // 7. Verify Signature if IdP Certificate is configured
        if (config.getIdpCertificatePem() != null && !config.getIdpCertificatePem().isBlank()) {
            verifySignature(doc, config.getIdpCertificatePem());
        }

        // 8. Extract Attributes
        Map<String, List<String>> attributes = extractAttributes(assertion);

        String email = findAttributeValue(attributes, config.getAttributeMappings().getOrDefault("email", "mail"),
                SamlConfig.ATTR_MAIL_OID, SamlConfig.ATTR_MAIL_FRIENDLY, "email", "mail");
        if (email == null && "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress".equals(nameIdFormat)) {
            email = subjectNameId;
        }

        String displayName = findAttributeValue(attributes, config.getAttributeMappings().getOrDefault("name", "displayName"),
                SamlConfig.ATTR_DISPLAY_NAME_OID, SamlConfig.ATTR_DISPLAY_NAME_FRIENDLY, "displayName");

        String givenName = findAttributeValue(attributes, SamlConfig.ATTR_GIVEN_NAME_OID, SamlConfig.ATTR_GIVEN_NAME_FRIENDLY);
        String surname = findAttributeValue(attributes, SamlConfig.ATTR_SURNAME_OID, SamlConfig.ATTR_SURNAME_FRIENDLY);

        List<String> affiliations = findAllAttributeValues(attributes, config.getAttributeMappings().getOrDefault("affiliation", "eduPersonScopedAffiliation"),
                SamlConfig.ATTR_AFFILIATION_OID, SamlConfig.ATTR_AFFILIATION_FRIENDLY,
                SamlConfig.ATTR_PRIMARY_AFFILIATION_OID, SamlConfig.ATTR_PRIMARY_AFFILIATION_FRIENDLY);

        return new SamlAssertionClaims(
                subjectNameId,
                nameIdFormat,
                issuer,
                email,
                displayName,
                givenName,
                surname,
                affiliations,
                attributes
        );
    }

    private static void validateStatusCode(Element root) throws SamlValidationException {
        Element status = findChildElement(root, "Status");
        if (status == null) {
            throw new SamlValidationException("Missing Status element in SAML Response");
        }
        Element statusCode = findChildElement(status, "StatusCode");
        if (statusCode == null) {
            throw new SamlValidationException("Missing StatusCode element in SAML Response");
        }
        String codeValue = statusCode.getAttribute("Value");
        if (!STATUS_SUCCESS.equals(codeValue)) {
            Element statusMsg = findChildElement(status, "StatusMessage");
            String msg = statusMsg != null ? statusMsg.getTextContent() : "Status code: " + codeValue;
            throw new SamlValidationException("SAML Authentication rejected by IdP: " + msg);
        }
    }

    private static void validateConditions(Element assertion, SamlConfig config) throws SamlValidationException {
        Element conditions = findChildElement(assertion, "Conditions");
        if (conditions == null) {
            return;
        }

        Instant now = Instant.now();
        int skew = config.getClockSkewSeconds();

        String notBeforeStr = conditions.getAttribute("NotBefore");
        if (notBeforeStr != null && !notBeforeStr.isBlank()) {
            try {
                Instant notBefore = Instant.parse(notBeforeStr);
                if (now.isBefore(notBefore.minusSeconds(skew))) {
                    throw new SamlValidationException("SAML assertion is not yet valid: NotBefore=" + notBeforeStr);
                }
            } catch (DateTimeParseException e) {
                throw new SamlValidationException("Malformed NotBefore condition timestamp: " + notBeforeStr, e);
            }
        }

        String notOnOrAfterStr = conditions.getAttribute("NotOnOrAfter");
        if (notOnOrAfterStr != null && !notOnOrAfterStr.isBlank()) {
            try {
                Instant notOnOrAfter = Instant.parse(notOnOrAfterStr);
                if (now.isAfter(notOnOrAfter.plusSeconds(skew))) {
                    throw new SamlValidationException("SAML assertion has expired: NotOnOrAfter=" + notOnOrAfterStr);
                }
            } catch (DateTimeParseException e) {
                throw new SamlValidationException("Malformed NotOnOrAfter condition timestamp: " + notOnOrAfterStr, e);
            }
        }

        // Validate AudienceRestriction
        Element audienceRestriction = findChildElement(conditions, "AudienceRestriction");
        if (audienceRestriction != null) {
            Element audience = findChildElement(audienceRestriction, "Audience");
            if (audience != null) {
                String aud = audience.getTextContent().trim();
                if (!config.getSpEntityId().equals(aud)) {
                    throw new SamlValidationException("Audience mismatch: expected " + config.getSpEntityId() + " but got " + aud);
                }
            }
        }
    }

    private static Map<String, List<String>> extractAttributes(Element assertion) {
        Map<String, List<String>> attributes = new HashMap<>();
        Element attributeStatement = findChildElement(assertion, "AttributeStatement");
        if (attributeStatement == null) {
            return attributes;
        }

        NodeList children = attributeStatement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element attrElem && "Attribute".equalsIgnoreCase(getLocalName(attrElem))) {
                String name = attrElem.getAttribute("Name");
                String friendlyName = attrElem.getAttribute("FriendlyName");

                List<String> values = new ArrayList<>();
                NodeList valNodes = attrElem.getChildNodes();
                for (int j = 0; j < valNodes.getLength(); j++) {
                    Node vNode = valNodes.item(j);
                    if (vNode instanceof Element vElem && "AttributeValue".equalsIgnoreCase(getLocalName(vElem))) {
                        values.add(vElem.getTextContent().trim());
                    }
                }

                if (name != null && !name.isBlank()) {
                    attributes.computeIfAbsent(name, k -> new ArrayList<>()).addAll(values);
                }
                if (friendlyName != null && !friendlyName.isBlank()) {
                    attributes.computeIfAbsent(friendlyName, k -> new ArrayList<>()).addAll(values);
                }
            }
        }
        return attributes;
    }

    private static String findAttributeValue(Map<String, List<String>> attributes, String... candidateKeys) {
        for (String key : candidateKeys) {
            if (key == null) continue;
            List<String> vals = attributes.get(key);
            if (vals != null && !vals.isEmpty()) {
                for (String v : vals) {
                    if (v != null && !v.isBlank()) return v;
                }
            }
        }
        return null;
    }

    private static List<String> findAllAttributeValues(Map<String, List<String>> attributes, String... candidateKeys) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String key : candidateKeys) {
            if (key == null) continue;
            List<String> vals = attributes.get(key);
            if (vals != null) {
                for (String v : vals) {
                    if (v != null && !v.isBlank() && seen.add(v)) {
                        result.add(v);
                    }
                }
            }
        }
        return result;
    }

    private static void verifySignature(Document doc, String certPem) throws SamlValidationException {
        try {
            PublicKey publicKey = parseCertificatePublicKey(certPem);
            NodeList nl = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
            if (nl.getLength() == 0) {
                throw new SamlValidationException("Signature verification required, but no XML Signature found in SAML document");
            }

            XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
            DOMValidateContext valContext = new DOMValidateContext(publicKey, nl.item(0));
            // Unsecure references check / ID attribute registration
            valContext.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.TRUE);

            XMLSignature signature = fac.unmarshalXMLSignature(valContext);
            boolean valid = signature.validate(valContext);
            if (!valid) {
                throw new SamlValidationException("Cryptographic verification of SAML XML Signature failed");
            }
        } catch (SamlValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new SamlValidationException("Failed to verify SAML XML signature: " + e.getMessage(), e);
        }
    }

    private static PublicKey parseCertificatePublicKey(String pem) throws Exception {
        String clean = pem.replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s+", "");
        byte[] bytes = Base64.getDecoder().decode(clean);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(bytes));
        return cert.getPublicKey();
    }

    /**
     * Hardened XML parser disabling external DTDs, external entities, and XInclude to prevent XXE.
     */
    public static Document parseSecureXml(String xml) throws SamlValidationException {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);

            DocumentBuilder builder = dbf.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new SamlValidationException("XML parsing error or XXE defense triggered: " + e.getMessage(), e);
        }
    }

    private static String decodeIfBase64(String input) {
        if (input.startsWith("<")) {
            return input;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(input);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return input;
        }
    }

    private static Element findChildElement(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element el && localName.equalsIgnoreCase(getLocalName(el))) {
                return el;
            }
        }
        return null;
    }

    private static String getLocalName(Element element) {
        String local = element.getLocalName();
        if (local != null) return local;
        String tagName = element.getTagName();
        int idx = tagName.indexOf(':');
        return idx >= 0 ? tagName.substring(idx + 1) : tagName;
    }
}
