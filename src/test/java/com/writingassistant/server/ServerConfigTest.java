package com.writingassistant.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ServerConfigTest {

    @Test
    void testDevelopmentDefaults(@TempDir Path tempDir) {
        ServerConfig config = ServerConfig.fromEnvironment(Map.of());
        assertEquals(ServerConfig.Environment.DEVELOPMENT, config.environment());
        assertEquals("127.0.0.1", config.host());
        assertEquals(8080, config.port());
        assertEquals("http://localhost:8080", config.publicOrigin());
        assertFalse(config.secureCookies());
        assertEquals(0, config.retentionDays());
        assertEquals(Duration.ofSeconds(90), config.jobTimeout());
    }

    @Test
    void testProductionRequiresHttpsAndSecureCookies(@TempDir Path tempDir) {
        // HTTP in production should fail
        assertThrows(IllegalArgumentException.class, () ->
                new ServerConfig(tempDir, "0.0.0.0", 8443, "http://originality.edu", true,
                        Duration.ofSeconds(60), 30, ServerConfig.Environment.PRODUCTION));

        // Missing secure cookies in production should fail
        assertThrows(IllegalArgumentException.class, () ->
                new ServerConfig(tempDir, "0.0.0.0", 8443, "https://originality.edu", false,
                        Duration.ofSeconds(60), 30, ServerConfig.Environment.PRODUCTION));

        // Port 0 in production should fail
        assertThrows(IllegalArgumentException.class, () ->
                new ServerConfig(tempDir, "0.0.0.0", 0, "https://originality.edu", true,
                        Duration.ofSeconds(60), 30, ServerConfig.Environment.PRODUCTION));

        // Valid production configuration
        ServerConfig prodConfig = new ServerConfig(tempDir, "0.0.0.0", 8443, "https://originality.edu", true,
                Duration.ofSeconds(60), 30, ServerConfig.Environment.PRODUCTION);
        assertEquals(ServerConfig.Environment.PRODUCTION, prodConfig.environment());
        assertTrue(prodConfig.secureCookies());
    }

    @Test
    void testSafeToStringRedactsPathsAndSecrets(@TempDir Path tempDir) {
        ServerConfig config = new ServerConfig(tempDir, "127.0.0.1", 8080, "http://localhost:8080", false, Duration.ofSeconds(90));
        String repr = config.toString();
        assertFalse(repr.contains(tempDir.toString()), "String representation must not expose absolute filesystem paths");
        assertTrue(repr.contains("env=DEVELOPMENT"));
        assertTrue(repr.contains("port=8080"));
    }

    @Test
    void testJobTimeoutRangeValidation(@TempDir Path tempDir) {
        // Less than 5s fails
        assertThrows(IllegalArgumentException.class, () ->
                ServerConfig.fromEnvironment(Map.of("DESK_JOB_TIMEOUT_SECONDS", "4")));

        // Greater than 600s fails
        assertThrows(IllegalArgumentException.class, () ->
                ServerConfig.fromEnvironment(Map.of("DESK_JOB_TIMEOUT_SECONDS", "601")));

        // Non-numeric fails
        assertThrows(IllegalArgumentException.class, () ->
                ServerConfig.fromEnvironment(Map.of("DESK_JOB_TIMEOUT_SECONDS", "abc")));

        // Valid custom timeout
        ServerConfig config = ServerConfig.fromEnvironment(Map.of("DESK_JOB_TIMEOUT_SECONDS", "120"));
        assertEquals(Duration.ofSeconds(120), config.jobTimeout());
    }
}

