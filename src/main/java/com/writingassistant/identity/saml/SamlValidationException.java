package com.writingassistant.identity.saml;

/**
 * Thrown when a SAML 2.0 response or assertion fails structural, temporal,
 * cryptographic, or policy validation.
 */
public class SamlValidationException extends Exception {
    public SamlValidationException(String message) {
        super(message);
    }

    public SamlValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
