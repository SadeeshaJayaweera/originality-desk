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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * External scholarly literature provider integrating the OpenAlex open-access catalog.
 */
public class OpenAlexSourceProvider implements ExternalSourceProvider {

    public static final String PROVIDER_ID = "openalex";
    public static final String DEFAULT_BASE_URL = "https://api.openalex.org";

    private final String baseUrl;
    private final String mailtoEmail;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public OpenAlexSourceProvider() {
        this(
                System.getProperty("originality.openalex.base_url",
                        System.getenv().getOrDefault("ORIGINALITY_OPENALEX_BASE_URL", DEFAULT_BASE_URL)),
                System.getProperty("originality.openalex.mailto",
                        System.getenv().getOrDefault("ORIGINALITY_OPENALEX_MAILTO", "integrity@originalitydesk.org")),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                Duration.ofSeconds(15)
        );
    }

    public OpenAlexSourceProvider(String baseUrl, String mailtoEmail, HttpClient httpClient, Duration requestTimeout) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null").replaceAll("/+$", "");
        this.mailtoEmail = mailtoEmail;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout must not be null");
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getMailtoEmail() {
        return mailtoEmail;
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayName() {
        return "OpenAlex";
    }

    @Override
    public boolean isAvailable() {
        String enabled = System.getenv("ORIGINALITY_OPENALEX_ENABLED");
        return enabled == null || !"false".equalsIgnoreCase(enabled);
    }

    @Override
    public ExternalSearchResult search(ExternalSearchQuery query) throws IOException {
        Objects.requireNonNull(query, "query must not be null");
        long startTime = System.currentTimeMillis();

        StringBuilder urlBuilder = new StringBuilder(baseUrl).append("/works?");
        List<String> params = new ArrayList<>();

        if (query.queryText() != null && !query.queryText().isBlank()) {
            params.add("search=" + urlEncode(query.queryText()));
        }

        List<String> filters = new ArrayList<>();
        if (query.title() != null && !query.title().isBlank()) {
            filters.add("title.search:" + query.title());
        }
        if (query.author() != null && !query.author().isBlank()) {
            filters.add("author.display_name.search:" + query.author());
        }
        if (query.yearFrom() != null && query.yearTo() != null) {
            filters.add("publication_year:" + query.yearFrom() + "-" + query.yearTo());
        } else if (query.yearFrom() != null) {
            filters.add("from_publication_date:" + query.yearFrom() + "-01-01");
        } else if (query.yearTo() != null) {
            filters.add("to_publication_date:" + query.yearTo() + "-12-31");
        }

        if (!filters.isEmpty()) {
            params.add("filter=" + urlEncode(String.join(",", filters)));
        }

        params.add("per_page=" + query.limit());
        int page = (query.offset() / query.limit()) + 1;
        params.add("page=" + page);

        if (mailtoEmail != null && !mailtoEmail.isBlank()) {
            params.add("mailto=" + urlEncode(mailtoEmail));
        }

        urlBuilder.append(String.join("&", params));

        JsonObject json = executeGet(urlBuilder.toString());
        long duration = Math.max(0L, System.currentTimeMillis() - startTime);

        long totalCount = 0;
        if (json.has("meta") && json.getAsJsonObject("meta").has("count")) {
            totalCount = json.getAsJsonObject("meta").get("count").getAsLong();
        }

        List<ExternalScholarlyWork> works = new ArrayList<>();
        if (json.has("results") && json.get("results").isJsonArray()) {
            JsonArray results = json.getAsJsonArray("results");
            for (JsonElement el : results) {
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

        String url = baseUrl + "/works/https://doi.org/" + normalizedDoi.get();
        if (mailtoEmail != null && !mailtoEmail.isBlank()) {
            url += "?mailto=" + urlEncode(mailtoEmail);
        }

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
                .header("User-Agent", "OriginalityDesk/1.0 (mailto:" + (mailtoEmail != null ? mailtoEmail : "integrity@originalitydesk.org") + ")")
                .GET();

        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status == 200) {
                return JsonParser.parseString(response.body()).getAsJsonObject();
            }
            if (status == 404) {
                throw new ProviderException(PROVIDER_ID, 404, "Scholarly work not found: " + url);
            }
            if (status == 429) {
                throw new ProviderException(PROVIDER_ID, 429, "OpenAlex rate limit exceeded; please configure mailto for polite pool");
            }
            throw new ProviderException(PROVIDER_ID, status, "OpenAlex API error (" + status + "): " + response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("OpenAlex request was interrupted", e);
        }
    }

    static ExternalScholarlyWork parseWork(JsonObject obj) {
        String id = obj.has("id") ? obj.get("id").getAsString() : "";
        String title = obj.has("title") && !obj.get("title").isJsonNull()
                ? obj.get("title").getAsString()
                : (obj.has("display_name") && !obj.get("display_name").isJsonNull() ? obj.get("display_name").getAsString() : "Untitled");

        List<String> authors = new ArrayList<>();
        if (obj.has("authorships") && obj.get("authorships").isJsonArray()) {
            for (JsonElement a : obj.getAsJsonArray("authorships")) {
                if (a.isJsonObject() && a.getAsJsonObject().has("author")) {
                    JsonObject authorObj = a.getAsJsonObject().getAsJsonObject("author");
                    if (authorObj.has("display_name") && !authorObj.get("display_name").isJsonNull()) {
                        authors.add(authorObj.get("display_name").getAsString());
                    }
                }
            }
        }

        Integer year = obj.has("publication_year") && !obj.get("publication_year").isJsonNull()
                ? obj.get("publication_year").getAsInt() : null;

        String venue = null;
        if (obj.has("primary_location") && !obj.get("primary_location").isJsonNull()) {
            JsonObject loc = obj.getAsJsonObject("primary_location");
            if (loc.has("source") && !loc.get("source").isJsonNull()) {
                JsonObject src = loc.getAsJsonObject("source");
                if (src.has("display_name") && !src.get("display_name").isJsonNull()) {
                    venue = src.get("display_name").getAsString();
                }
            }
        }

        String rawDoi = obj.has("doi") && !obj.get("doi").isJsonNull() ? obj.get("doi").getAsString() : null;
        String doi = DoiNormalizer.normalize(rawDoi).orElse(rawDoi);

        String landingPageUrl = null;
        String pdfUrl = null;
        boolean isOa = false;
        if (obj.has("open_access") && !obj.get("open_access").isJsonNull()) {
            JsonObject oa = obj.getAsJsonObject("open_access");
            if (oa.has("is_oa") && !oa.get("is_oa").isJsonNull()) {
                isOa = oa.get("is_oa").getAsBoolean();
            }
            if (oa.has("oa_url") && !oa.get("oa_url").isJsonNull()) {
                landingPageUrl = oa.get("oa_url").getAsString();
                if (landingPageUrl.endsWith(".pdf")) {
                    pdfUrl = landingPageUrl;
                }
            }
        }
        if (landingPageUrl == null && doi != null) {
            landingPageUrl = "https://doi.org/" + doi;
        }

        String abstractText = null;
        if (obj.has("abstract_inverted_index") && !obj.get("abstract_inverted_index").isJsonNull()) {
            abstractText = reconstructAbstract(obj.getAsJsonObject("abstract_inverted_index"));
        }

        int citations = obj.has("cited_by_count") && !obj.get("cited_by_count").isJsonNull()
                ? obj.get("cited_by_count").getAsInt() : 0;

        SourceAttribution attribution = SourceAttribution.of(
                PROVIDER_ID,
                venue != null ? venue : "OpenAlex Academic Index",
                id,
                "CC0",
                isOa
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
                .pdfUrl(pdfUrl)
                .abstractText(abstractText)
                .citationCount(citations)
                .hasFullText(pdfUrl != null || isOa)
                .attribution(attribution)
                .build();
    }

    /**
     * Reconstructs prose text from OpenAlex's abstract_inverted_index structure.
     */
    static String reconstructAbstract(JsonObject invertedIndex) {
        if (invertedIndex == null || invertedIndex.isEmpty()) return null;
        Map<Integer, String> wordPositions = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : invertedIndex.entrySet()) {
            String word = entry.getKey();
            if (entry.getValue().isJsonArray()) {
                for (JsonElement posEl : entry.getValue().getAsJsonArray()) {
                    wordPositions.put(posEl.getAsInt(), word);
                }
            }
        }
        if (wordPositions.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (String word : wordPositions.values()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(word);
        }
        return sb.toString();
    }

    private static String urlEncode(String val) {
        return URLEncoder.encode(val, StandardCharsets.UTF_8);
    }
}
