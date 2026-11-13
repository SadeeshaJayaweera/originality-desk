package com.writingassistant.sources;

import java.io.IOException;

/**
 * Exception raised during external provider API interactions (rate limits, timeouts, upstream failures).
 */
public class ProviderException extends IOException {
    private static final long serialVersionUID = 1L;

    private final String providerId;
    private final int statusCode;

    public ProviderException(String providerId, String message) {
        super(message);
        this.providerId = providerId;
        this.statusCode = 0;
    }

    public ProviderException(String providerId, int statusCode, String message) {
        super(message);
        this.providerId = providerId;
        this.statusCode = statusCode;
    }

    public ProviderException(String providerId, String message, Throwable cause) {
        super(message, cause);
        this.providerId = providerId;
        this.statusCode = 0;
    }

    public String getProviderId() {
        return providerId;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
