package com.writingassistant.identity.scim;

/**
 * Exception carrying SCIM 2.0 RFC 7644 HTTP error status and scimType.
 */
public class ScimException extends Exception {
    private final int status;
    private final String scimType;

    public ScimException(int status, String message) {
        this(status, null, message);
    }

    public ScimException(int status, String scimType, String message) {
        super(message);
        this.status = status;
        this.scimType = scimType;
    }

    public int getStatus() { return status; }
    public String getScimType() { return scimType; }

    public ScimError toScimError() {
        return new ScimError(status, scimType, getMessage());
    }
}
