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

class SemanticScholarSourceProviderTest {

    private static HttpServer mockServer;
    private static String serverUrl;
    private static final AtomicBoolean receivedApiKey = new AtomicBoolean(false);

    @BeforeAll
    static void startServer() throws Exception {
        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockServer.createContext("/paper", exchange -> {
            String path = exchange.getRequestURI().toString();
            String authHeader = exchange.getRequestHeaders().getFirst("x-api-key");
            if ("test-api-key".equals(authHeader)) {
                receivedApiKey.set(true);
            }

            if (path.contains("rate-limit")) {
                exchange.sendResponseHeaders(429, 0);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write("Too many requests".getBytes(StandardCharsets.UTF_8));
                }
                return;
            }
            if (path.contains("not-found")) {
                exchange.sendResponseHeaders(404, 0);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write("Paper not found".getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            // Mock paper item
            JsonObject paper = new JsonObject();
            paper.addProperty("paperId", "649def34f8be52c8b66281af98ae772c99cfd5d2");
            paper.addProperty("title", "Language Models are Few-Shot Learners");
            paper.addProperty("abstract", "We demonstrate that scaling up language models greatly improves task-agnostic few-shot performance.");
            paper.addProperty("year", 2020);
            paper.addProperty("venue", "NeurIPS");
            paper.addProperty("citationCount", 25000);
            paper.addProperty("url", "https://www.semanticscholar.org/paper/649def34f8be52c8b66281af98ae772c99cfd5d2");

            JsonObject extIds = new JsonObject();
            extIds.addProperty("DOI", "10.48550/arxiv.2005.14165");
            paper.add("externalIds", extIds);

            JsonArray authors = new JsonArray();
            JsonObject author1 = new JsonObject();
            author1.addProperty("name", "Tom B. Brown");
            authors.add(author1);
            paper.add("authors", authors);

            JsonObject oaPdf = new JsonObject();
            oaPdf.addProperty("url", "https://arxiv.org/pdf/2005.14165.pdf");
            paper.add("openAccessPdf", oaPdf);

            if (path.startsWith("/paper/DOI:")) {
                byte[] bytes = paper.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
            } else {
                JsonObject root = new JsonObject();
                root.addProperty("total", 1);
                JsonArray data = new JsonArray();
                data.add(paper);
                root.add("data", data);

                byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
            }
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
    void searchesSemanticScholarAndParsesResult() throws IOException {
        SemanticScholarSourceProvider provider = new SemanticScholarSourceProvider(
                serverUrl,
                "test-api-key",
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                Duration.ofSeconds(5)
        );

        assertEquals("semantic_scholar", provider.getProviderId());
        assertEquals("Semantic Scholar", provider.getDisplayName());
        assertTrue(provider.isAvailable());

        ExternalSearchResult result = provider.search(ExternalSearchQuery.of("few-shot learners", 10));
        assertEquals("semantic_scholar", result.providerId());
        assertEquals(1, result.totalEstimatedResults());
        assertEquals(1, result.works().size());

        ExternalScholarlyWork work = result.works().get(0);
        assertEquals("649def34f8be52c8b66281af98ae772c99cfd5d2", work.externalId());
        assertEquals("Language Models are Few-Shot Learners", work.title());
        assertEquals(List.of("Tom B. Brown"), work.authors());
        assertEquals(2020, work.publicationYear());
        assertEquals("NeurIPS", work.venueOrJournal());
        assertEquals("10.48550/arxiv.2005.14165", work.doi());
        assertEquals(25000, work.citationCount());
        assertTrue(work.hasFullText());
        assertEquals("https://arxiv.org/pdf/2005.14165.pdf", work.pdfUrl());
        assertTrue(receivedApiKey.get(), "Expected x-api-key header to be transmitted");
    }

    @Test
    void resolvesByDoiAndHandlesMissing() throws IOException {
        SemanticScholarSourceProvider provider = new SemanticScholarSourceProvider(
                serverUrl,
                null,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                Duration.ofSeconds(5)
        );

        Optional<ExternalScholarlyWork> work = provider.findByDoi("10.48550/arxiv.2005.14165");
        assertTrue(work.isPresent());
        assertEquals("Language Models are Few-Shot Learners", work.get().title());

        Optional<ExternalScholarlyWork> notFound = provider.findByDoi("10.1000/not-found");
        assertTrue(notFound.isEmpty());
    }

    @Test
    void handlesRateLimitWithProviderException() {
        SemanticScholarSourceProvider provider = new SemanticScholarSourceProvider(
                serverUrl,
                null,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                Duration.ofSeconds(5)
        );

        ProviderException ex = assertThrows(ProviderException.class, () -> provider.findByDoi("10.1000/rate-limit"));
        assertEquals(429, ex.getStatusCode());
        assertEquals("semantic_scholar", ex.getProviderId());
    }
}
