package com.writingassistant.server;

import java.util.Map;
import java.util.Set;

/** Bounded request events: no raw URLs, caller identifiers, headers, or bodies. */
final class RequestEvents {
    private static final Set<String> METHODS = Set.of("GET", "POST", "DELETE", "PUT", "PATCH", "HEAD", "OPTIONS", "TRACE", "CONNECT");
    private static final Map<String,String> EXACT = Map.ofEntries(
            Map.entry("/", "static"), Map.entry("/index.html", "static"),
            Map.entry("/app.js", "static"), Map.entry("/styles.css", "static"),
            Map.entry("/health/live", "health.live"), Map.entry("/health/ready", "health.ready"),
            Map.entry("/api/v1/auth/register", "auth.register"), Map.entry("/api/v1/auth/login", "auth.login"),
            Map.entry("/api/v1/auth/me", "auth.me"), Map.entry("/api/v1/auth/logout", "auth.logout"),
            Map.entry("/api/v1/dashboard", "dashboard"), Map.entry("/api/v1/documents", "documents"),
            Map.entry("/api/v1/analyses", "analyses"), Map.entry("/api/v1/admin/health", "admin.health"),
            Map.entry("/api/v1/admin/jobs", "admin.jobs"), Map.entry("/api/v1/admin/audit-logs", "admin.audit"));

    private RequestEvents() {}

    static String completed(String method, String rawPath, int status, long durationMs, String requestId) {
        return Service.JSON.toJson(Map.of("event", "request_completed", "method", METHODS.contains(method) ? method : "OTHER",
                "routeCategory", category(rawPath), "status", Math.max(0, status),
                "durationMs", Math.max(0, durationMs), "requestId", requestId));
    }

    private static String category(String rawPath) {
        if (rawPath == null || !rawPath.matches("/[a-zA-Z0-9/_.-]*") || rawPath.contains("..") || rawPath.contains("//")) return "unmatched";
        String exact = EXACT.get(rawPath);
        if (exact != null) return exact;
        if (!rawPath.startsWith("/api/v1/")) return "unmatched";
        String[] parts = rawPath.substring("/api/v1/".length()).split("/", -1);
        if (parts.length < 2 || parts[1].isEmpty()) return "unmatched";
        if (parts[0].equals("documents") && parts.length == 2) return "documents.item";
        if (parts[0].equals("analyses")) {
            if (parts.length == 2) return "analyses.item";
            if (parts.length == 3) return switch (parts[2]) {
                case "status" -> "analyses.status";
                case "cancel" -> "analyses.cancel";
                case "reviews" -> "analyses.reviews";
                default -> "unmatched";
            };
        }
        if (parts[0].equals("reports")) {
            if (parts.length == 2) return "reports.json";
            if (parts.length == 3) return switch (parts[2]) {
                case "json" -> "reports.json";
                case "html" -> "reports.html";
                case "csv" -> "reports.csv";
                case "pdf" -> "reports.pdf";
                default -> "unmatched";
            };
        }
        return "unmatched";
    }
}
