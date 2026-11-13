package com.writingassistant.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ApiErrorHandlingTest {
    @TempDir Path tempDir;
    private OriginalityDeskServer server;

    @BeforeEach
    void setUp() throws Exception {
        ServerConfig config = new ServerConfig(tempDir.resolve("api_err_test"), "127.0.0.1", 0, "http://localhost:0", false, Duration.ofSeconds(30));
        server = new OriginalityDeskServer(config);
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void testStandardized404NotFoundEnvelope() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(server.publicOrigin() + "/nonexistent").toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.connect();

        assertEquals(404, conn.getResponseCode());
        String reqIdHeader = conn.getHeaderField("X-Request-Id");
        assertNotNull(reqIdHeader, "X-Request-Id response header must be present");

        try (InputStream in = conn.getErrorStream()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            assertTrue(json.has("error"), "Response must have top-level error envelope");
            JsonObject err = json.getAsJsonObject("error");
            assertEquals("NOT_FOUND", err.get("code").getAsString());
            assertEquals("Resource not found", err.get("message").getAsString());
            assertEquals(reqIdHeader, err.get("requestId").getAsString(), "requestId in body must match X-Request-Id header");
            assertFalse(body.contains("Exception"), "Error response must not leak Java exception names");
        }
    }

    @Test
    void testStandardized401UnauthenticatedEnvelope() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(server.publicOrigin() + "/api/v1/auth/me").toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.connect();

        assertEquals(401, conn.getResponseCode());
        String reqIdHeader = conn.getHeaderField("X-Request-Id");
        assertNotNull(reqIdHeader);

        try (InputStream in = conn.getErrorStream()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            JsonObject err = json.getAsJsonObject("error");
            assertEquals("UNAUTHENTICATED", err.get("code").getAsString());
            assertEquals(reqIdHeader, err.get("requestId").getAsString());
            assertFalse(body.contains("Exception"));
        }
    }

    @Test
    void testStandardized403CsrfEnvelope() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(server.publicOrigin() + "/api/v1/auth/register").toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        // Do not provide matching Origin header -> CSRF rejection
        conn.getOutputStream().write("{}".getBytes(StandardCharsets.UTF_8));
        conn.connect();

        assertEquals(403, conn.getResponseCode());
        try (InputStream in = conn.getErrorStream()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            JsonObject err = json.getAsJsonObject("error");
            assertEquals("CSRF_REJECTED", err.get("code").getAsString());
        }
    }
}
