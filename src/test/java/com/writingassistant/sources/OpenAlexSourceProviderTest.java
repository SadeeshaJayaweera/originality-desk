package com.writingassistant.sources;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenAlexSourceProviderTest {

    private static HttpServer mockServer;
    private static String serverUrl;

    @BeforeAll
    static void startServer() throws Exception {
        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockServer.createContext("/works", exchange -> {
            String path = exchange.getRequestURI().toString();
            if (path.contains("rate-limit")) {
                exchange.sendResponseHeaders(429, 0);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write("Rate limited".getBytes(StandardCharsets.UTF_8));
                }
                return;
            }
            if (path.contains("not-found")) {
                exchange.sendResponseHeaders(404, 0);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write("Not found".getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            // Return mock OpenAlex response
            JsonObject root = new JsonObject();
            JsonObject meta = new JsonObject();
            meta.addProperty("count", 1);
            root.add("meta", meta);

            JsonArray results = new JsonArray();
            JsonObject work = new JsonObject();
            work.addProperty("id", "https://openalex.org/W12345");
            work.addProperty("title", "Foundations of Large Language Models");
            work.addProperty("publication_year", 2024);
            work.addProperty("doi", "https://doi.org/10.1000/182");
            work.addProperty("cited_by_count", 42);

            JsonArray authorships = new JsonArray();
            JsonObject a1 = new JsonObject();
            JsonObject author1 = new JsonObject();
            author1.addProperty("display_name", "Jane Doe");
            a1.add("author", author1);
            authorships.add(a1);
            work.add("authorships", authorships);

            JsonObject loc = new JsonObject();
            JsonObject src = new JsonObject();
            src.addProperty("display_name", "Journal of Artificial Intelligence Research");
            loc.add("source", src);
            work.add("primary_location", loc);

            JsonObject oa = new JsonObject();
            oa.addProperty("is_oa", true);
            oa.addProperty("oa_url", "https://jair.org/paper.pdf");
            work.add("open_access", oa);

            // Reconstructed abstract: "Transformers are powerful"
            JsonObject abs = new JsonObject();
            JsonArray p0 = new JsonArray(); p0.add(0); abs.add("Transformers", p0);
            JsonArray p1 = new JsonArray(); p1.add(1); abs.add("are", p1);
            JsonArray p2 = new JsonArray(); p2.add(2); abs.add("powerful", p2);
            work.add("abstract_inverted_index", abs);

            if (path.contains("https://doi.org/") || path.contains("doi:")) {
                // Single work response for DOI lookup
                byte[] bytes = work.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
            } else {
                results.add(work);
                root.add("results", results);
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
    void reconstructsAbstractFromInvertedIndex() {
        JsonObject index = new JsonObject();
        JsonArray p0 = new JsonArray(); p0.add(0); p0.add(4); index.add("the", p0);
        JsonArray p1 = new JsonArray(); p1.add(1); index.add("cat", p1);
        JsonArray p2 = new JsonArray(); p2.add(2); index.add("sat", p2);
        JsonArray p3 = new JsonArray(); p3.add(3); index.add("on", p3);
        JsonArray p5 = new JsonArray(); p5.add(5); index.add("mat", p5);

        String reconstructed = OpenAlexSourceProvider.reconstructAbstract(index);
        assertEquals("the cat sat on the mat", reconstructed);

        assertNull(OpenAlexSourceProvider.reconstructAbstract(null));
        assertNull(OpenAlexSourceProvider.reconstructAbstract(new JsonObject()));
    }

    @Test
    void searchWorksAgainstMockOpenAlex() throws IOException {
        OpenAlexSourceProvider provider = new OpenAlexSourceProvider(
                serverUrl,
                "test@originalitydesk.org",
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                Duration.ofSeconds(5)
        );

        assertEquals("openalex", provider.getProviderId());
        assertEquals("OpenAlex", provider.getDisplayName());
        assertTrue(provider.isAvailable());

        ExternalSearchResult result = provider.search(ExternalSearchQuery.of("transformers", 10));
        assertEquals("openalex", result.providerId());
        assertEquals(1, result.totalEstimatedResults());
        assertEquals(1, result.works().size());

        ExternalScholarlyWork work = result.works().get(0);
        assertEquals("Foundations of Large Language Models", work.title());
        assertEquals("10.1000/182", work.doi());
        assertEquals(List.of("Jane Doe"), work.authors());
        assertEquals("Journal of Artificial Intelligence Research", work.venueOrJournal());
        assertEquals(42, work.citationCount());
        assertEquals("Transformers are powerful", work.abstractText());
        assertTrue(work.hasFullText());
        assertEquals("https://jair.org/paper.pdf", work.pdfUrl());
    }

    @Test
    void resolvesWorkByDoiAndHandlesNotFound() throws IOException {
        OpenAlexSourceProvider provider = new OpenAlexSourceProvider(
                serverUrl,
                "test@originalitydesk.org",
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                Duration.ofSeconds(5)
        );

        Optional<ExternalScholarlyWork> work = provider.findByDoi("10.1000/182");
        assertTrue(work.isPresent());
        assertEquals("Foundations of Large Language Models", work.get().title());

        Optional<ExternalScholarlyWork> missing = provider.findByDoi("10.1000/not-found");
        assertTrue(missing.isEmpty());
    }

    @Test
    void handlesRateLimitWithProviderException() {
        OpenAlexSourceProvider provider = new OpenAlexSourceProvider(
                serverUrl,
                "test@originalitydesk.org",
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                Duration.ofSeconds(5)
        );

        ProviderException ex = assertThrows(ProviderException.class, () -> provider.findByDoi("10.1000/rate-limit"));
        assertEquals(429, ex.getStatusCode());
        assertEquals("openalex", ex.getProviderId());
    }
}
