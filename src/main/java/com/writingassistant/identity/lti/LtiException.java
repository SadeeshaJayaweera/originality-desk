package com.writingassistant.identity.lti;

/**
 * Thrown when an LTI 1.3 Advantage launch fails authentication, verification, or authorization checks.
 */
public class LtiException extends Exception {
    public LtiException(String message) {
        super(message);
    }

    public LtiException(String message, Throwable cause) {
        super(message, cause);
    }
}
