package com.writingassistant.sources;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoreSourceProviderTest {

    private static HttpServer mockServer;
    private static String serverUrl;
    private static final AtomicBoolean receivedBearerAuth = new AtomicBoolean(false);

    @BeforeAll
    static void startServer() throws Exception {
        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockServer.createContext("/search/works", exchange -> {
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer test-core-key")) {
                receivedBearerAuth.set(true);
            }
            String query = exchange.getRequestURI().toString();
            if (query.contains("unauthorized")) {
                exchange.sendResponseHeaders(401, 0);
                try (OutputStream os = exchange.getResponseBody()) { os.write("Unauthorized".getBytes(StandardCharsets.UTF_8)); }
                return;
            }
            if (query.contains("rate-limit")) {
                exchange.sendResponseHeaders(429, 0);
                try (OutputStream os = exchange.getResponseBody()) { os.write("Rate limit".getBytes(StandardCharsets.UTF_8)); }
                return;
            }

            JsonObject root = new JsonObject();
            root.addProperty("totalHits", 1);
            JsonArray results = new JsonArray();

            JsonObject work = new JsonObject();
            work.addProperty("id", "8931201");
            work.addProperty("title", "A Survey on Automated Plagiarism Detection");
            work.addProperty("yearPublished", 2022);
            work.addProperty("publisher", "SpringerOpen");
            work.addProperty("doi", "10.1186/s13673-022-00341-1");
            work.addProperty("downloadUrl", "https://core.ac.uk/download/pdf/8931201.pdf");
            work.addProperty("abstract", "This survey reviews state of the art similarity detection.");

            JsonArray authors = new JsonArray();
            JsonObject author = new JsonObject();
            author.addProperty("name", "Alice Walker");
            authors.add(author);
            work.add("authors", authors);

            results.add(work);
            root.add("results", results);

            byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });

        mockServer.createContext("/works/doi:", exchange -> {
            String path = exchange.getRequestURI().toString();
            if (path.contains("not-found")) {
                exchange.sendResponseHeaders(404, 0);
                try (OutputStream os = exchange.getResponseBody()) { os.write("Not found".getBytes(StandardCharsets.UTF_8)); }
                return;
            }

            JsonObject work = new JsonObject();
            work.addProperty("id", "8931201");
            work.addProperty("title", "A Survey on Automated Plagiarism Detection");
            work.addProperty("yearPublished", 2022);
            work.addProperty("publisher", "SpringerOpen");
            work.addProperty("doi", "10.1186/s13673-022-00341-1");
            work.addProperty("downloadUrl", "https://core.ac.uk/download/pdf/8931201.pdf");

            JsonArray authors = new JsonArray();
            JsonObject author = new JsonObject();
            author.addProperty("name", "Alice Walker");
            authors.add(author);
            work.add("authors", authors);

            byte[] bytes = work.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });

        mockServer.start();
        serverUrl = "http://localhost:" + mockServer.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    @Test
    void searchesCoreAndIdentifiesFullTextAvailability() throws IOException {
        CoreSourceProvider provider = new CoreSourceProvider(
                serverUrl,
                "test-core-key",
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                Duration.ofSeconds(5)
        );

        assertEquals("core", provider.getProviderId());
        assertEquals("CORE (Connecting Repositories)", provider.getDisplayName());
        assertTrue(provider.isAvailable());

        ExternalSearchResult result = provider.search(ExternalSearchQuery.of("plagiarism detection", 10));
        assertEquals("core", result.providerId());
        assertEquals(1, result.totalEstimatedResults());
        assertEquals(1, result.works().size());

        ExternalScholarlyWork work = result.works().get(0);
        assertEquals("8931201", work.externalId());
        assertEquals("A Survey on Automated Plagiarism Detection", work.title());
        assertEquals(List.of("Alice Walker"), work.authors());
        assertEquals(2022, work.publicationYear());
        assertEquals("SpringerOpen", work.venueOrJournal());
        assertEquals("10.1186/s13673-022-00341-1", work.doi());
        assertTrue(work.hasFullText(), "Should have full text when downloadUrl is present");
        assertEquals("https://core.ac.uk/download/pdf/8931201.pdf", work.pdfUrl());
        assertTrue(receivedBearerAuth.get(), "Authorization Bearer header must be present");
    }

    @Test
    void resolvesByDoiAndHandlesMissingWithFallback() throws IOException {
        CoreSourceProvider provider = new CoreSourceProvider(
                serverUrl,
                "test-core-key",
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                Duration.ofSeconds(5)
        );

        Optional<ExternalScholarlyWork> work = provider.findByDoi("10.1186/s13673-022-00341-1");
        assertTrue(work.isPresent());
        assertEquals("A Survey on Automated Plagiarism Detection", work.get().title());

        // When direct /works/doi: returns 404, it queries /search/works?q=doi:... which returns our mock
        Optional<ExternalScholarlyWork> fallbackWork = provider.findByDoi("10.1186/not-found-direct");
        assertTrue(fallbackWork.isPresent());
    }

    @Test
    void handlesAuthenticationFailure() {
        CoreSourceProvider provider = new CoreSourceProvider(
                serverUrl,
                null,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                Duration.ofSeconds(5)
        );

        ProviderException ex = assertThrows(ProviderException.class, () -> provider.search(ExternalSearchQuery.of("unauthorized", 5)));
        assertEquals(401, ex.getStatusCode());
        assertEquals("core", ex.getProviderId());
    }

    @Test
    void handlesRateLimit() {
        CoreSourceProvider provider = new CoreSourceProvider(
                serverUrl,
                null,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                Duration.ofSeconds(5)
        );

        ProviderException ex = assertThrows(ProviderException.class, () -> provider.search(ExternalSearchQuery.of("rate-limit", 5)));
        assertEquals(429, ex.getStatusCode());
        assertEquals("core", ex.getProviderId());
    }
}
