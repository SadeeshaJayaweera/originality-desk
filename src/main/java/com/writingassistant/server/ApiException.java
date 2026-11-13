package com.writingassistant.server;

/**
 * Standard typed API exception mapped directly to HTTP status codes and error responses.
 */
public final class ApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public final int status;
    public final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException bad(String message) { return new ApiException(400, "INVALID_REQUEST", message); }
    public static ApiException unauthorized() { return new ApiException(401, "UNAUTHENTICATED", "Authentication required"); }
    public static ApiException forbidden(String message) { return new ApiException(403, "FORBIDDEN", message); }
    public static ApiException missing() { return new ApiException(404, "NOT_FOUND", "Resource not found"); }
    public static ApiException conflict(String message) { return new ApiException(409, "CONFLICT", message); }
    public static ApiException payloadTooLarge(String message) { return new ApiException(413, "PAYLOAD_TOO_LARGE", message); }
    public static ApiException unsupported(String message) { return new ApiException(415, "UNSUPPORTED_MEDIA_TYPE", message); }
    public static ApiException limit(String message) { return new ApiException(429, "LIMIT_EXCEEDED", message); }
    public static ApiException internal(String message) { return new ApiException(500, "INTERNAL_ERROR", message); }
}
