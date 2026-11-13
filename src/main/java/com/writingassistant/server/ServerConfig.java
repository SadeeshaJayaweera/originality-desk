package com.writingassistant.server;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/** Configuration for the separate, single-host service; never uses the desktop database. */
public record ServerConfig(Path dataDir, String host, int port, String publicOrigin,
                           boolean secureCookies, Duration jobTimeout, int retentionDays,
                           Environment environment) {

    public enum Environment {
        DEVELOPMENT,
        TEST,
        PRODUCTION;

        public static Environment fromString(String env) {
            if (env == null || env.isBlank()) return DEVELOPMENT;
            String val = env.trim().toUpperCase(Locale.ROOT);
            return switch (val) {
                case "PROD", "PRODUCTION" -> PRODUCTION;
                case "TEST", "TESTING" -> TEST;
                default -> DEVELOPMENT;
            };
        }
    }

    public ServerConfig(Path dataDir, String host, int port, String publicOrigin,
                        boolean secureCookies, Duration jobTimeout, int retentionDays) {
        this(dataDir, host, port, publicOrigin, secureCookies, jobTimeout, retentionDays, Environment.DEVELOPMENT);
    }

    public ServerConfig(Path dataDir, String host, int port, String publicOrigin,
                        boolean secureCookies, Duration jobTimeout) {
        this(dataDir, host, port, publicOrigin, secureCookies, jobTimeout, 0, Environment.DEVELOPMENT);
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
        if (environment == null)
            environment = Environment.DEVELOPMENT;

        // Strict production validation
        if (environment == Environment.PRODUCTION) {
            if (!"https".equals(origin.getScheme()))
                throw new IllegalArgumentException("Production environment requires an HTTPS public origin");
            if (!secureCookies)
                throw new IllegalArgumentException("Production environment requires DESK_SECURE_COOKIES=true");
            if (port == 0)
                throw new IllegalArgumentException("Production environment requires a non-zero bind port");
        }
    }

    public static ServerConfig fromEnvironment() { return fromEnvironment(System.getenv()); }

    static ServerConfig fromEnvironment(Map<String, String> env) {
        String secure = env.getOrDefault("DESK_SECURE_COOKIES", "false");
        if (!secure.equals("true") && !secure.equals("false"))
            throw new IllegalArgumentException("DESK_SECURE_COOKIES must be true or false");
        String retention = env.getOrDefault("DESK_RETENTION_DAYS", "0");
        if (!retention.matches("[0-9]{1,4}"))
            throw new IllegalArgumentException("DESK_RETENTION_DAYS must be an integer between 0 and 3650");

        String timeoutSec = env.getOrDefault("DESK_JOB_TIMEOUT_SECONDS", "90");
        if (!timeoutSec.matches("[0-9]{1,4}"))
            throw new IllegalArgumentException("DESK_JOB_TIMEOUT_SECONDS must be an integer between 5 and 600");
        int timeoutSeconds = Integer.parseInt(timeoutSec);
        if (timeoutSeconds < 5 || timeoutSeconds > 600)
            throw new IllegalArgumentException("DESK_JOB_TIMEOUT_SECONDS must be between 5 and 600");

        Environment environment = Environment.fromString(env.getOrDefault("DESK_ENV", "development"));

        return new ServerConfig(
                Path.of(env.getOrDefault("DESK_DATA_DIR", "./desk-server-data")),
                env.getOrDefault("DESK_HOST", "127.0.0.1"),
                Integer.parseInt(env.getOrDefault("DESK_PORT", "8080")),
                env.getOrDefault("DESK_PUBLIC_ORIGIN", "http://localhost:8080"),
                Boolean.parseBoolean(secure),
                Duration.ofSeconds(timeoutSeconds),
                Integer.parseInt(retention),
                environment
        );
    }

    @Override
    public String toString() {
        return "ServerConfig[env=" + environment + ", host=" + host + ", port=" + port
                + ", origin=" + publicOrigin + ", secureCookies=" + secureCookies
                + ", jobTimeout=" + jobTimeout.toSeconds() + "s, retentionDays=" + retentionDays + "]";
    }
}
