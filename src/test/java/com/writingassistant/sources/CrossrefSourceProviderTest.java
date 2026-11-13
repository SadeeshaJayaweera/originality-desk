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

class CrossrefSourceProviderTest {

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

            // Create mock Crossref message
            JsonObject root = new JsonObject();
            root.addProperty("status", "ok");

            JsonObject message = new JsonObject();
            message.addProperty("total-results", 1);

            JsonObject item = new JsonObject();
            item.addProperty("DOI", "10.1038/s41586-020-2649-2");

            JsonArray titles = new JsonArray();
            titles.add("<jats:title>Deep learning for biology &amp; medicine</jats:title>");
            item.add("title", titles);

            JsonArray authors = new JsonArray();
            JsonObject author = new JsonObject();
            author.addProperty("given", "John");
            author.addProperty("family", "Smith");
            authors.add(author);
            item.add("author", authors);

            JsonObject issued = new JsonObject();
            JsonArray dateParts = new JsonArray();
            JsonArray parts = new JsonArray();
            parts.add(2021); parts.add(5); parts.add(12);
            dateParts.add(parts);
            issued.add("date-parts", dateParts);
            item.add("issued", issued);

            JsonArray venues = new JsonArray();
            venues.add("Nature");
            item.add("container-title", venues);

            item.addProperty("URL", "https://doi.org/10.1038/s41586-020-2649-2");
            item.addProperty("abstract", "<jats:p>This study explores deep neural networks in medicine.</jats:p>");
            item.addProperty("is-referenced-by-count", 340);

            JsonArray links = new JsonArray();
            JsonObject pdfLink = new JsonObject();
            pdfLink.addProperty("content-type", "application/pdf");
            pdfLink.addProperty("URL", "https://nature.com/articles/s41586-020-2649-2.pdf");
            links.add(pdfLink);
            item.add("link", links);

            if (path.startsWith("/works/10.")) {
                message = item;
                root.add("message", message);
            } else {
                JsonArray items = new JsonArray();
                items.add(item);
                message.add("items", items);
                root.add("message", message);
            }

            byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
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
    void stripsXmlAndHtmlEntities() {
        assertEquals("Deep learning for biology & medicine",
                CrossrefSourceProvider.stripXml("<jats:title>Deep learning for biology &amp; medicine</jats:title>"));
        assertEquals("Comparison: a < b and c > d",
                CrossrefSourceProvider.stripXml("<p>Comparison: a &lt; b and c &gt; d</p>"));
        assertNull(CrossrefSourceProvider.stripXml(null));
    }

    @Test
    void searchesCrossrefAndParsesWork() throws IOException {
        CrossrefSourceProvider provider = new CrossrefSourceProvider(
                serverUrl,
                "test@originalitydesk.org",
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                Duration.ofSeconds(5)
        );

        assertEquals("crossref", provider.getProviderId());
        assertEquals("Crossref", provider.getDisplayName());
        assertTrue(provider.isAvailable());

        ExternalSearchResult result = provider.search(ExternalSearchQuery.of("deep learning", 5));
        assertEquals("crossref", result.providerId());
        assertEquals(1, result.totalEstimatedResults());
        assertEquals(1, result.works().size());

        ExternalScholarlyWork work = result.works().get(0);
        assertEquals("Deep learning for biology & medicine", work.title());
        assertEquals("10.1038/s41586-020-2649-2", work.doi());
        assertEquals(List.of("John Smith"), work.authors());
        assertEquals(2021, work.publicationYear());
        assertEquals("Nature", work.venueOrJournal());
        assertEquals(340, work.citationCount());
        assertEquals("This study explores deep neural networks in medicine.", work.abstractText());
        assertTrue(work.hasFullText());
        assertEquals("https://nature.com/articles/s41586-020-2649-2.pdf", work.pdfUrl());
    }

    @Test
    void resolvesByDoiAndHandlesMissing() throws IOException {
        CrossrefSourceProvider provider = new CrossrefSourceProvider(
                serverUrl,
                "test@originalitydesk.org",
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                Duration.ofSeconds(5)
        );

        Optional<ExternalScholarlyWork> work = provider.findByDoi("10.1038/s41586-020-2649-2");
        assertTrue(work.isPresent());
        assertEquals("Deep learning for biology & medicine", work.get().title());

        Optional<ExternalScholarlyWork> notFound = provider.findByDoi("10.1000/not-found");
        assertTrue(notFound.isEmpty());
    }

    @Test
    void handlesRateLimit() {
        CrossrefSourceProvider provider = new CrossrefSourceProvider(
                serverUrl,
                "test@originalitydesk.org",
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                Duration.ofSeconds(5)
        );

        ProviderException ex = assertThrows(ProviderException.class, () -> provider.findByDoi("10.1000/rate-limit"));
        assertEquals(429, ex.getStatusCode());
        assertEquals("crossref", ex.getProviderId());
    }
}
