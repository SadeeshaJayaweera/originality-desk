package com.writingassistant.server;

final class ApiException extends RuntimeException {
    final int status;
    final String code;
    ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    static ApiException bad(String message) { return new ApiException(400, "INVALID_REQUEST", message); }
    static ApiException missing() { return new ApiException(404, "NOT_FOUND", "Resource not found"); }
    static ApiException conflict(String message) { return new ApiException(409, "CONFLICT", message); }
    static ApiException limit(String message) { return new ApiException(429, "LIMIT_EXCEEDED", message); }
}
