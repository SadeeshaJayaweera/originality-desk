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
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * External scholarly literature provider integrating the Crossref REST API.
 */
public class CrossrefSourceProvider implements ExternalSourceProvider {

    public static final String PROVIDER_ID = "crossref";
    public static final String DEFAULT_BASE_URL = "https://api.crossref.org";

    private static final Pattern XML_TAGS = Pattern.compile("<[^>]+>");

    private final String baseUrl;
    private final String mailtoEmail;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public CrossrefSourceProvider() {
        this(
                System.getProperty("originality.crossref.base_url",
                        System.getenv().getOrDefault("ORIGINALITY_CROSSREF_BASE_URL", DEFAULT_BASE_URL)),
                System.getProperty("originality.crossref.mailto",
                        System.getenv().getOrDefault("ORIGINALITY_CROSSREF_MAILTO", "integrity@originalitydesk.org")),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                Duration.ofSeconds(15)
        );
    }

    public CrossrefSourceProvider(String baseUrl, String mailtoEmail, HttpClient httpClient, Duration requestTimeout) {
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
        return "Crossref";
    }

    @Override
    public boolean isAvailable() {
        String enabled = System.getenv("ORIGINALITY_CROSSREF_ENABLED");
        return enabled == null || !"false".equalsIgnoreCase(enabled);
    }

    @Override
    public ExternalSearchResult search(ExternalSearchQuery query) throws IOException {
        Objects.requireNonNull(query, "query must not be null");
        long startTime = System.currentTimeMillis();

        StringBuilder urlBuilder = new StringBuilder(baseUrl).append("/works?");
        List<String> params = new ArrayList<>();

        if (query.queryText() != null && !query.queryText().isBlank()) {
            params.add("query=" + urlEncode(query.queryText()));
        }
        if (query.title() != null && !query.title().isBlank()) {
            params.add("query.title=" + urlEncode(query.title()));
        }
        if (query.author() != null && !query.author().isBlank()) {
            params.add("query.author=" + urlEncode(query.author()));
        }

        List<String> filters = new ArrayList<>();
        if (query.yearFrom() != null) {
            filters.add("from-pub-date:" + query.yearFrom() + "-01-01");
        }
        if (query.yearTo() != null) {
            filters.add("until-pub-date:" + query.yearTo() + "-12-31");
        }
        if (!filters.isEmpty()) {
            params.add("filter=" + urlEncode(String.join(",", filters)));
        }

        params.add("rows=" + query.limit());
        params.add("offset=" + query.offset());

        urlBuilder.append(String.join("&", params));

        JsonObject json = executeGet(urlBuilder.toString());
        long duration = Math.max(0L, System.currentTimeMillis() - startTime);

        long totalCount = 0;
        List<ExternalScholarlyWork> works = new ArrayList<>();

        if (json.has("message") && json.get("message").isJsonObject()) {
            JsonObject message = json.getAsJsonObject("message");
            if (message.has("total-results") && !message.get("total-results").isJsonNull()) {
                totalCount = message.get("total-results").getAsLong();
            }
            if (message.has("items") && message.get("items").isJsonArray()) {
                for (JsonElement el : message.getAsJsonArray("items")) {
                    if (el.isJsonObject()) {
                        works.add(parseWork(el.getAsJsonObject()));
                    }
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

        String url = baseUrl + "/works/" + urlEncode(normalizedDoi.get());
        try {
            JsonObject json = executeGet(url);
            if (json.has("message") && json.get("message").isJsonObject()) {
                return Optional.of(parseWork(json.getAsJsonObject("message")));
            }
            return Optional.empty();
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
                throw new ProviderException(PROVIDER_ID, 404, "Crossref record not found: " + url);
            }
            if (status == 429) {
                throw new ProviderException(PROVIDER_ID, 429, "Crossref rate limit exceeded");
            }
            throw new ProviderException(PROVIDER_ID, status, "Crossref API error (" + status + "): " + response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Crossref request was interrupted", e);
        }
    }

    static ExternalScholarlyWork parseWork(JsonObject obj) {
        String rawDoi = obj.has("DOI") && !obj.get("DOI").isJsonNull() ? obj.get("DOI").getAsString() : "";
        String doi = DoiNormalizer.normalize(rawDoi).orElse(rawDoi);

        String title = "Untitled";
        if (obj.has("title") && obj.get("title").isJsonArray()) {
            JsonArray titles = obj.getAsJsonArray("title");
            if (titles.size() > 0 && !titles.get(0).isJsonNull()) {
                title = stripXml(titles.get(0).getAsString());
            }
        }

        List<String> authors = new ArrayList<>();
        if (obj.has("author") && obj.get("author").isJsonArray()) {
            for (JsonElement a : obj.getAsJsonArray("author")) {
                if (a.isJsonObject()) {
                    JsonObject authorObj = a.getAsJsonObject();
                    String family = authorObj.has("family") && !authorObj.get("family").isJsonNull() ? authorObj.get("family").getAsString() : "";
                    String given = authorObj.has("given") && !authorObj.get("given").isJsonNull() ? authorObj.get("given").getAsString() : "";
                    if (!family.isBlank() && !given.isBlank()) {
                        authors.add(given + " " + family);
                    } else if (!family.isBlank()) {
                        authors.add(family);
                    } else if (authorObj.has("name") && !authorObj.get("name").isJsonNull()) {
                        authors.add(authorObj.get("name").getAsString());
                    }
                }
            }
        }

        Integer year = null;
        if (obj.has("issued") && obj.get("issued").isJsonObject()) {
            JsonObject issued = obj.getAsJsonObject("issued");
            if (issued.has("date-parts") && issued.get("date-parts").isJsonArray()) {
                JsonArray parts = issued.getAsJsonArray("date-parts");
                if (parts.size() > 0 && parts.get(0).isJsonArray() && parts.get(0).getAsJsonArray().size() > 0) {
                    year = parts.get(0).getAsJsonArray().get(0).getAsInt();
                }
            }
        }

        String venue = null;
        if (obj.has("container-title") && obj.get("container-title").isJsonArray()) {
            JsonArray venues = obj.getAsJsonArray("container-title");
            if (venues.size() > 0 && !venues.get(0).isJsonNull()) {
                venue = stripXml(venues.get(0).getAsString());
            }
        }

        String landingPageUrl = obj.has("URL") && !obj.get("URL").isJsonNull()
                ? obj.get("URL").getAsString() : (doi.isBlank() ? null : "https://doi.org/" + doi);

        String pdfUrl = null;
        if (obj.has("link") && obj.get("link").isJsonArray()) {
            for (JsonElement linkEl : obj.getAsJsonArray("link")) {
                if (linkEl.isJsonObject()) {
                    JsonObject linkObj = linkEl.getAsJsonObject();
                    String contentType = linkObj.has("content-type") && !linkObj.get("content-type").isJsonNull()
                            ? linkObj.get("content-type").getAsString() : "";
                    if ("application/pdf".equalsIgnoreCase(contentType) && linkObj.has("URL") && !linkObj.get("URL").isJsonNull()) {
                        pdfUrl = linkObj.get("URL").getAsString();
                        break;
                    }
                }
            }
        }

        String abstractText = null;
        if (obj.has("abstract") && !obj.get("abstract").isJsonNull()) {
            abstractText = stripXml(obj.get("abstract").getAsString()).trim();
        }

        int citations = obj.has("is-referenced-by-count") && !obj.get("is-referenced-by-count").isJsonNull()
                ? obj.get("is-referenced-by-count").getAsInt() : 0;

        SourceAttribution attribution = SourceAttribution.of(
                PROVIDER_ID,
                venue != null ? venue : "Crossref Metadata Repository",
                landingPageUrl != null ? landingPageUrl : "https://doi.org/" + doi,
                "Crossref Metadata",
                pdfUrl != null
        );

        return ExternalScholarlyWork.builder()
                .providerId(PROVIDER_ID)
                .externalId(doi)
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

    static String stripXml(String text) {
        if (text == null) return null;
        return XML_TAGS.matcher(text).replaceAll("").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").trim();
    }

    private static String urlEncode(String val) {
        return URLEncoder.encode(val, StandardCharsets.UTF_8);
    }
}
