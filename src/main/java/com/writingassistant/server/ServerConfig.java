package com.writingassistant.server;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/** Configuration for the separate, single-host service; never uses the desktop database. */
public record ServerConfig(Path dataDir, String host, int port, String publicOrigin,
                           boolean secureCookies, Duration jobTimeout, int retentionDays) {
    public ServerConfig(Path dataDir, String host, int port, String publicOrigin,
                        boolean secureCookies, Duration jobTimeout) {
        this(dataDir, host, port, publicOrigin, secureCookies, jobTimeout, 0);
    }

    public ServerConfig {
        dataDir = dataDir.toAbsolutePath().normalize();
        if (host == null || host.isBlank() || port < 0 || port > 65535)
            throw new IllegalArgumentException("Invalid bind address");
        URI origin = URI.create(publicOrigin);
        if (origin.getHost() == null || origin.getUserInfo() != null || origin.getQuery() != null
                || origin.getFragment() != null || (origin.getPath() != null && !origin.getPath().isEmpty())
                || !("http".equals(origin.getScheme()) || "https".equals(origin.getScheme())))
            throw new IllegalArgumentException("DESK_PUBLIC_ORIGIN must be an HTTP(S) origin without a path");
        if ("https".equals(origin.getScheme()) && !secureCookies)
            throw new IllegalArgumentException("HTTPS requires DESK_SECURE_COOKIES=true");
        if (jobTimeout == null || jobTimeout.isNegative() || jobTimeout.isZero()
                || jobTimeout.compareTo(Duration.ofMinutes(10)) > 0)
            throw new IllegalArgumentException("Invalid job timeout");
        if (retentionDays < 0 || retentionDays > 3650)
            throw new IllegalArgumentException("DESK_RETENTION_DAYS must be between 0 and 3650");
    }

    public static ServerConfig fromEnvironment() { return fromEnvironment(System.getenv()); }

    static ServerConfig fromEnvironment(Map<String, String> env) {
        String secure = env.getOrDefault("DESK_SECURE_COOKIES", "false");
        if (!secure.equals("true") && !secure.equals("false"))
            throw new IllegalArgumentException("DESK_SECURE_COOKIES must be true or false");
        String retention = env.getOrDefault("DESK_RETENTION_DAYS", "0");
                if (!retention.matches("[0-9]{1,4}"))
                    throw new IllegalArgumentException("DESK_RETENTION_DAYS must be an integer between 0 and 3650");
                return new ServerConfig(Path.of(env.getOrDefault("DESK_DATA_DIR", "./desk-server-data")),
                env.getOrDefault("DESK_HOST", "127.0.0.1"),
                Integer.parseInt(env.getOrDefault("DESK_PORT", "8080")),
                env.getOrDefault("DESK_PUBLIC_ORIGIN", "http://localhost:8080"),
                Boolean.parseBoolean(secure), Duration.ofSeconds(90), Integer.parseInt(retention));
    }
}
