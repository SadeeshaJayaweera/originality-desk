package com.writingassistant.sources;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * External scholarly literature provider integrating the CORE API v3 for open-access research
 * and full-text document discovery.
 */
public class CoreSourceProvider implements ExternalSourceProvider {

    public static final String PROVIDER_ID = "core";
    public static final String DEFAULT_BASE_URL = "https://api.core.ac.uk/v3";

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public CoreSourceProvider() {
        this(
                System.getProperty("originality.core.base_url",
                        System.getenv().getOrDefault("ORIGINALITY_CORE_BASE_URL", DEFAULT_BASE_URL)),
                System.getProperty("originality.core.api_key",
                        System.getenv().getOrDefault("ORIGINALITY_CORE_API_KEY", null)),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                Duration.ofSeconds(15)
        );
    }

    public CoreSourceProvider(String baseUrl, String apiKey, HttpClient httpClient, Duration requestTimeout) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null").replaceAll("/+$", "");
        this.apiKey = apiKey;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout must not be null");
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayName() {
        return "CORE (Connecting Repositories)";
    }

    @Override
    public boolean isAvailable() {
        String enabled = System.getenv("ORIGINALITY_CORE_ENABLED");
        return enabled == null || !"false".equalsIgnoreCase(enabled);
    }

    @Override
    public ExternalSearchResult search(ExternalSearchQuery query) throws IOException {
        Objects.requireNonNull(query, "query must not be null");
        long startTime = System.currentTimeMillis();

        StringBuilder queryBuilder = new StringBuilder();
        if (query.queryText() != null && !query.queryText().isBlank()) {
            queryBuilder.append(query.queryText().trim());
        }
        if (query.title() != null && !query.title().isBlank()) {
            if (queryBuilder.length() > 0) queryBuilder.append(" AND ");
            queryBuilder.append("title:\"").append(query.title().replace("\"", "\\\"")).append("\"");
        }
        if (query.author() != null && !query.author().isBlank()) {
            if (queryBuilder.length() > 0) queryBuilder.append(" AND ");
            queryBuilder.append("authors:\"").append(query.author().replace("\"", "\\\"")).append("\"");
        }
        if (query.yearFrom() != null && query.yearTo() != null) {
            if (queryBuilder.length() > 0) queryBuilder.append(" AND ");
            queryBuilder.append("yearPublished:>=").append(query.yearFrom()).append(" AND yearPublished:<=").append(query.yearTo());
        } else if (query.yearFrom() != null) {
            if (queryBuilder.length() > 0) queryBuilder.append(" AND ");
            queryBuilder.append("yearPublished:>=").append(query.yearFrom());
        } else if (query.yearTo() != null) {
            if (queryBuilder.length() > 0) queryBuilder.append(" AND ");
            queryBuilder.append("yearPublished:<=").append(query.yearTo());
        }

        String searchUrl = baseUrl + "/search/works?q=" + urlEncode(queryBuilder.toString())
                + "&limit=" + query.limit() + "&offset=" + query.offset();

        JsonObject json = executeGet(searchUrl);
        long duration = Math.max(0L, System.currentTimeMillis() - startTime);

        long totalCount = 0;
        if (json.has("totalHits") && !json.get("totalHits").isJsonNull()) {
            totalCount = json.get("totalHits").getAsLong();
        }

        List<ExternalScholarlyWork> works = new ArrayList<>();
        if (json.has("results") && json.get("results").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("results")) {
                if (el.isJsonObject()) {
                    works.add(parseWork(el.getAsJsonObject()));
                }
            }
        }

        return new ExternalSearchResult(PROVIDER_ID, works, totalCount, duration);
    }

    @Override
    public Optional<ExternalScholarlyWork> findByDoi(String rawDoi) throws IOException {
        if (rawDoi == null || rawDoi.isBlank()) return Optional.empty();
        Optional<String> normalizedDoi = DoiNormalizer.normalize(rawDoi);
        if (normalizedDoi.isEmpty()) return Optional.empty();

        String url = baseUrl + "/works/doi:" + urlEncode(normalizedDoi.get());
        try {
            JsonObject json = executeGet(url);
            return Optional.of(parseWork(json));
        } catch (ProviderException e) {
            if (e.getStatusCode() == 404) {
                return searchByDoiFallback(normalizedDoi.get());
            }
            throw e;
        }
    }

    private Optional<ExternalScholarlyWork> searchByDoiFallback(String doi) throws IOException {
        String searchUrl = baseUrl + "/search/works?q=" + urlEncode("doi:\"" + doi + "\"") + "&limit=1";
        JsonObject json = executeGet(searchUrl);
        if (json.has("results") && json.get("results").isJsonArray()) {
            JsonArray results = json.getAsJsonArray("results");
            if (results.size() > 0 && results.get(0).isJsonObject()) {
                return Optional.of(parseWork(results.get(0).getAsJsonObject()));
            }
        }
        return Optional.empty();
    }

    private JsonObject executeGet(String url) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("User-Agent", "OriginalityDesk/1.0");

        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status == 200) {
                return JsonParser.parseString(response.body()).getAsJsonObject();
            }
            if (status == 404) {
                throw new ProviderException(PROVIDER_ID, 404, "CORE work not found: " + url);
            }
            if (status == 401 || status == 403) {
                throw new ProviderException(PROVIDER_ID, status, "CORE authentication required or invalid API key");
            }
            if (status == 429) {
                throw new ProviderException(PROVIDER_ID, 429, "CORE rate limit exceeded");
            }
            throw new ProviderException(PROVIDER_ID, status, "CORE API error (" + status + "): " + response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("CORE request was interrupted", e);
        }
    }

    static ExternalScholarlyWork parseWork(JsonObject obj) {
        String id = obj.has("id") && !obj.get("id").isJsonNull() ? obj.get("id").getAsString() : "";
        String title = obj.has("title") && !obj.get("title").isJsonNull() ? obj.get("title").getAsString() : "Untitled";

        List<String> authors = new ArrayList<>();
        if (obj.has("authors") && obj.get("authors").isJsonArray()) {
            for (JsonElement a : obj.getAsJsonArray("authors")) {
                if (a.isJsonObject() && a.getAsJsonObject().has("name") && !a.getAsJsonObject().get("name").isJsonNull()) {
                    authors.add(a.getAsJsonObject().get("name").getAsString());
                } else if (a.isJsonPrimitive()) {
                    authors.add(a.getAsString());
                }
            }
        }

        Integer year = obj.has("yearPublished") && !obj.get("yearPublished").isJsonNull()
                ? obj.get("yearPublished").getAsInt() : null;

        String venue = null;
        if (obj.has("publisher") && !obj.get("publisher").isJsonNull()) {
            venue = obj.get("publisher").getAsString();
        } else if (obj.has("journals") && obj.get("journals").isJsonArray()) {
            JsonArray j = obj.getAsJsonArray("journals");
            if (j.size() > 0 && !j.get(0).isJsonNull()) venue = j.get(0).getAsString();
        }

        String rawDoi = obj.has("doi") && !obj.get("doi").isJsonNull() ? obj.get("doi").getAsString() : null;
        String doi = DoiNormalizer.normalize(rawDoi).orElse(rawDoi);

        String downloadUrl = obj.has("downloadUrl") && !obj.get("downloadUrl").isJsonNull()
                ? obj.get("downloadUrl").getAsString() : null;

        String fullText = obj.has("fullText") && !obj.get("fullText").isJsonNull()
                ? obj.get("fullText").getAsString() : null;

        String landingPageUrl = null;
        if (obj.has("links") && obj.get("links").isJsonArray()) {
            for (JsonElement linkEl : obj.getAsJsonArray("links")) {
                if (linkEl.isJsonObject() && linkEl.getAsJsonObject().has("url") && !linkEl.getAsJsonObject().get("url").isJsonNull()) {
                    landingPageUrl = linkEl.getAsJsonObject().get("url").getAsString();
                    break;
                }
            }
        }
        if (landingPageUrl == null) {
            landingPageUrl = (doi != null && !doi.isBlank()) ? "https://doi.org/" + doi : "https://core.ac.uk/works/" + id;
        }

        String abstractText = obj.has("abstract") && !obj.get("abstract").isJsonNull()
                ? obj.get("abstract").getAsString() : null;

        boolean hasFullText = (downloadUrl != null && !downloadUrl.isBlank()) || (fullText != null && !fullText.isBlank());

        SourceAttribution attribution = SourceAttribution.of(
                PROVIDER_ID,
                venue != null ? venue : "CORE Open Repositories",
                landingPageUrl,
                "Open Access Repository",
                hasFullText
        );

        return ExternalScholarlyWork.builder()
                .providerId(PROVIDER_ID)
                .externalId(id)
                .title(title)
                .authors(authors)
                .publicationYear(year)
                .venueOrJournal(venue)
                .doi(doi)
                .landingPageUrl(landingPageUrl)
                .pdfUrl(downloadUrl)
                .abstractText(abstractText)
                .citationCount(0)
                .hasFullText(hasFullText)
                .attribution(attribution)
                .build();
    }

    private static String urlEncode(String val) {
        return URLEncoder.encode(val, StandardCharsets.UTF_8);
    }
}
