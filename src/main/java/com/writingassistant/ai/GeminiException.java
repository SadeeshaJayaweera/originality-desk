package com.writingassistant.ai;

/**
 * Base exception for Gemini AI client interactions.
 */
public class GeminiException extends Exception {

    private final int statusCode;

    public GeminiException(String message) {
        super(message);
        this.statusCode = 0;
    }

    public GeminiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public GeminiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
