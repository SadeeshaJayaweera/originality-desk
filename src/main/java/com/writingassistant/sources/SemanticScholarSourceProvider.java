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
 * External scholarly literature provider integrating the Semantic Scholar Academic Graph API.
 */
public class SemanticScholarSourceProvider implements ExternalSourceProvider {

    public static final String PROVIDER_ID = "semantic_scholar";
    public static final String DEFAULT_BASE_URL = "https://api.semanticscholar.org/graph/v1";
    public static final String STANDARD_FIELDS = "paperId,title,abstract,authors,year,venue,citationCount,openAccessPdf,externalIds,url";

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public SemanticScholarSourceProvider() {
        this(
                System.getProperty("originality.semanticscholar.base_url",
                        System.getenv().getOrDefault("ORIGINALITY_SEMANTICSCHOLAR_BASE_URL", DEFAULT_BASE_URL)),
                System.getProperty("originality.semanticscholar.api_key",
                        System.getenv().getOrDefault("ORIGINALITY_SEMANTICSCHOLAR_API_KEY", null)),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                Duration.ofSeconds(15)
        );
    }

    public SemanticScholarSourceProvider(String baseUrl, String apiKey, HttpClient httpClient, Duration requestTimeout) {
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
        return "Semantic Scholar";
    }

    @Override
    public boolean isAvailable() {
        String enabled = System.getenv("ORIGINALITY_SEMANTICSCHOLAR_ENABLED");
        return enabled == null || !"false".equalsIgnoreCase(enabled);
    }

    @Override
    public ExternalSearchResult search(ExternalSearchQuery query) throws IOException {
        Objects.requireNonNull(query, "query must not be null");
        long startTime = System.currentTimeMillis();

        String rawQuery = query.queryText();
        if (rawQuery == null || rawQuery.isBlank()) {
            if (query.title() != null && !query.title().isBlank()) {
                rawQuery = query.title();
            } else if (query.author() != null && !query.author().isBlank()) {
                rawQuery = query.author();
            }
        }

        StringBuilder urlBuilder = new StringBuilder(baseUrl)
                .append("/paper/search?query=")
                .append(urlEncode(rawQuery))
                .append("&offset=").append(query.offset())
                .append("&limit=").append(query.limit())
                .append("&fields=").append(urlEncode(STANDARD_FIELDS));

        if (query.yearFrom() != null && query.yearTo() != null) {
            urlBuilder.append("&year=").append(query.yearFrom()).append("-").append(query.yearTo());
        } else if (query.yearFrom() != null) {
            urlBuilder.append("&year=").append(query.yearFrom()).append("-");
        } else if (query.yearTo() != null) {
            urlBuilder.append("&year=-").append(query.yearTo());
        }

        JsonObject json = executeGet(urlBuilder.toString());
        long duration = Math.max(0L, System.currentTimeMillis() - startTime);

        long totalCount = 0;
        if (json.has("total") && !json.get("total").isJsonNull()) {
            totalCount = json.get("total").getAsLong();
        }

        List<ExternalScholarlyWork> works = new ArrayList<>();
        if (json.has("data") && json.get("data").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("data")) {
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

        String url = baseUrl + "/paper/DOI:" + urlEncode(normalizedDoi.get()) + "?fields=" + urlEncode(STANDARD_FIELDS);
        try {
            JsonObject json = executeGet(url);
            return Optional.of(parseWork(json));
        } catch (ProviderException e) {
            if (e.getStatusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    private JsonObject executeGet(String url) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("User-Agent", "OriginalityDesk/1.0");

        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("x-api-key", apiKey);
        }

        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status == 200) {
                return JsonParser.parseString(response.body()).getAsJsonObject();
            }
            if (status == 404) {
                throw new ProviderException(PROVIDER_ID, 404, "Paper not found: " + url);
            }
            if (status == 429) {
                throw new ProviderException(PROVIDER_ID, 429, "Semantic Scholar rate limit exceeded");
            }
            throw new ProviderException(PROVIDER_ID, status, "Semantic Scholar API error (" + status + "): " + response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Semantic Scholar request was interrupted", e);
        }
    }

    static ExternalScholarlyWork parseWork(JsonObject obj) {
        String paperId = obj.has("paperId") && !obj.get("paperId").isJsonNull() ? obj.get("paperId").getAsString() : "";
        String title = obj.has("title") && !obj.get("title").isJsonNull() ? obj.get("title").getAsString() : "Untitled";

        List<String> authors = new ArrayList<>();
        if (obj.has("authors") && obj.get("authors").isJsonArray()) {
            for (JsonElement a : obj.getAsJsonArray("authors")) {
                if (a.isJsonObject() && a.getAsJsonObject().has("name") && !a.getAsJsonObject().get("name").isJsonNull()) {
                    authors.add(a.getAsJsonObject().get("name").getAsString());
                }
            }
        }

        Integer year = obj.has("year") && !obj.get("year").isJsonNull() ? obj.get("year").getAsInt() : null;
        String venue = obj.has("venue") && !obj.get("venue").isJsonNull() ? obj.get("venue").getAsString() : null;

        String doi = null;
        if (obj.has("externalIds") && obj.get("externalIds").isJsonObject()) {
            JsonObject ext = obj.getAsJsonObject("externalIds");
            if (ext.has("DOI") && !ext.get("DOI").isJsonNull()) {
                doi = DoiNormalizer.normalize(ext.get("DOI").getAsString()).orElse(ext.get("DOI").getAsString());
            }
        }

        String landingPageUrl = obj.has("url") && !obj.get("url").isJsonNull()
                ? obj.get("url").getAsString() : (paperId.isBlank() ? null : "https://www.semanticscholar.org/paper/" + paperId);

        String pdfUrl = null;
        if (obj.has("openAccessPdf") && obj.get("openAccessPdf").isJsonObject()) {
            JsonObject oa = obj.getAsJsonObject("openAccessPdf");
            if (oa.has("url") && !oa.get("url").isJsonNull()) {
                pdfUrl = oa.get("url").getAsString();
            }
        }

        String abstractText = obj.has("abstract") && !obj.get("abstract").isJsonNull()
                ? obj.get("abstract").getAsString() : null;

        int citations = obj.has("citationCount") && !obj.get("citationCount").isJsonNull()
                ? obj.get("citationCount").getAsInt() : 0;

        SourceAttribution attribution = SourceAttribution.of(
                PROVIDER_ID,
                venue != null ? venue : "Semantic Scholar",
                landingPageUrl != null ? landingPageUrl : "https://www.semanticscholar.org/paper/" + paperId,
                "Semantic Scholar Open Research Corpus",
                pdfUrl != null
        );

        return ExternalScholarlyWork.builder()
                .providerId(PROVIDER_ID)
                .externalId(paperId)
                .title(title)
                .authors(authors)
                .publicationYear(year)
                .venueOrJournal(venue)
                .doi(doi)
                .landingPageUrl(landingPageUrl)
                .pdfUrl(pdfUrl)
                .abstractText(abstractText)
                .citationCount(citations)
                .hasFullText(pdfUrl != null)
                .attribution(attribution)
                .build();
    }

    private static String urlEncode(String val) {
        return URLEncoder.encode(val, StandardCharsets.UTF_8);
    }
}
