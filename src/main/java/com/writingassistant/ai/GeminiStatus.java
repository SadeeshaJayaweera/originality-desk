package com.writingassistant.ai;

/**
 * Status of the Gemini AI connection and configuration.
 */
public class GeminiStatus {

    private final boolean available;
    private final String message;
    private final int httpStatusCode;

    public GeminiStatus(boolean available, String message, int httpStatusCode) {
        this.available = available;
        this.message = message;
        this.httpStatusCode = httpStatusCode;
    }

    public static GeminiStatus unconfigured(String reason) {
        return new GeminiStatus(false, reason, 0);
    }

    public static GeminiStatus connected(String model) {
        return new GeminiStatus(true, "Successfully connected to Gemini API (" + model + ")", 200);
    }

    public static GeminiStatus failed(String error, int statusCode) {
        return new GeminiStatus(false, error, statusCode);
    }

    public boolean isAvailable() {
        return available;
    }

    public String getMessage() {
        return message;
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    @Override
    public String toString() {
        return "GeminiStatus{" +
                "available=" + available +
                ", message='" + message + '\'' +
                ", httpStatusCode=" + httpStatusCode +
                '}';
    }
}
