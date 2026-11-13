package com.writingassistant.ai;

import com.writingassistant.config.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class GeminiClientTest {
    @TempDir Path tempDir;
    private ConfigManager config;
    private StubHttpClient http;
    private GeminiClient client;

    @BeforeEach
    void setUp() {
        config = new ConfigManager(tempDir);
        config.getConfig().setGeminiApiKey("test-secret-key");
        http = new StubHttpClient();
        client = new GeminiClient(config, http, Duration.ofSeconds(1));
    }

    @Test
    void credentialsUseHeaderAndNeverAppearInRequestUrl() throws Exception {
        assertEquals("OK", client.generateText("prompt").getContent());
        assertEquals("test-secret-key", http.request.headers().firstValue("x-goog-api-key").orElseThrow());
        assertNull(http.request.uri().getQuery());
        assertFalse(http.request.uri().toString().contains("test-secret-key"));
        assertTrue(client.testConnection().isAvailable());
    }

    @Test
    void explicitOfflineModeNeverSendsARequestEvenWithAStoredKey() {
        config.getConfig().setOfflineMode(true);
        assertFalse(client.isConfigured());
        assertFalse(client.testConnection().isAvailable());
        assertThrows(AiFeatureUnavailableException.class, () -> client.generateText("private prompt"));
        assertEquals(0, http.calls);
    }

    @Test
    void upstreamBodiesAndParserFailuresAreNeverExposedOrLogged() {
        String sensitive = "SENSITIVE-upstream-test-secret-key-private-prompt";
        Logger logger = Logger.getLogger(GeminiClient.class.getName());
        List<LogRecord> records = new java.util.ArrayList<>();
        Handler capture = new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        logger.addHandler(capture);
        try {
            for (int status : new int[] {401, 429, 500, 200}) {
                http.status = status;
                for (String body : List.of("<html>" + sensitive + "</html>",
                        "{\"error\":{\"message\":\"" + sensitive + "\"}}",
                        "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":{\"secret\":\"" + sensitive + "\"}}]}}]}")) {
                    http.body = body;
                    GeminiException error = assertThrows(GeminiException.class, () -> client.generateText("prompt"));
                    assertFalse(error.getMessage().contains(sensitive));
                    assertNull(error.getCause(), "Parser/network causes can contain response or header secrets");
                    assertEquals(status == 200 ? 0 : status, error.getStatusCode());
                    GeminiStatus result = client.testConnection();
                    assertFalse(result.isAvailable());
                    assertFalse(result.getMessage().contains(sensitive));
                }
            }
            for (LogRecord record : records) {
                assertFalse(record.getMessage().contains(sensitive));
                assertNull(record.getThrown());
            }
        } finally {
            logger.removeHandler(capture);
        }
    }

    @Test
    void networkAndInvalidModelErrorsAreSanitized() {
        http.failure = new IOException("test-secret-key in upstream diagnostic");
        GeminiException error = assertThrows(GeminiException.class, () -> client.generateText("prompt"));
        assertFalse(error.getMessage().contains("test-secret-key"));
        assertNull(error.getCause());
        int previousCalls = http.calls;
        config.getConfig().setGeminiModel("model?key=test-secret-key");
        assertThrows(GeminiException.class, () -> client.generateText("prompt"));
        assertEquals(previousCalls, http.calls);
    }

    private static class StubHttpClient extends HttpClient {
        private final HttpClient defaults = HttpClient.newHttpClient();
        HttpRequest request;
        int calls;
        int status = 200;
        String body = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"OK\"}]}}]}";
        IOException failure;

        @Override @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
            this.request = request;
            calls++;
            if (failure != null) throw failure;
            return (HttpResponse<T>) new StubResponse(request, status, body);
        }
        @Override public Optional<CookieHandler> cookieHandler() { return defaults.cookieHandler(); }
        @Override public Optional<Duration> connectTimeout() { return defaults.connectTimeout(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return defaults.proxy(); }
        @Override public SSLContext sslContext() { return defaults.sslContext(); }
        @Override public SSLParameters sslParameters() { return defaults.sslParameters(); }
        @Override public Optional<Authenticator> authenticator() { return defaults.authenticator(); }
        @Override public Version version() { return defaults.version(); }
        @Override public Optional<Executor> executor() { return defaults.executor(); }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            throw new UnsupportedOperationException();
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler,
                HttpResponse.PushPromiseHandler<T> pushHandler) {
            throw new UnsupportedOperationException();
        }
    }

    private record StubResponse(HttpRequest request, int statusCode, String body) implements HttpResponse<String> {
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (name, value) -> true); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
