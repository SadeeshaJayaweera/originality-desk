package com.writingassistant.sources;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Federated scholarly search coordinator dispatching queries across all registered
 * academic data providers, merging duplicates, and ranking enriched scholarly works.
 */
public class UnifiedExternalReferenceSearch {

    private final ExternalSourceRegistry registry;
    private final Executor executor;
    private final Duration federatedTimeout;

    public UnifiedExternalReferenceSearch() {
        this(createDefaultRegistry(), ForkJoinPool.commonPool(), Duration.ofSeconds(10));
    }

    public UnifiedExternalReferenceSearch(ExternalSourceRegistry registry) {
        this(registry, ForkJoinPool.commonPool(), Duration.ofSeconds(10));
    }

    public UnifiedExternalReferenceSearch(ExternalSourceRegistry registry, Executor executor, Duration federatedTimeout) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.federatedTimeout = Objects.requireNonNull(federatedTimeout, "federatedTimeout must not be null");
    }

    public static ExternalSourceRegistry createDefaultRegistry() {
        ExternalSourceRegistry reg = new ExternalSourceRegistry();
        reg.register(new OpenAlexSourceProvider());
        reg.register(new CrossrefSourceProvider());
        reg.register(new SemanticScholarSourceProvider());
        reg.register(new CoreSourceProvider());
        return reg;
    }

    public ExternalSourceRegistry getRegistry() {
        return registry;
    }

    /**
     * Executes federated search across all active providers in parallel, merging and ranking results.
     */
    public UnifiedSearchResult search(ExternalSearchQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        long startTime = System.currentTimeMillis();

        List<ExternalSourceProvider> activeProviders = registry.getAvailableProviders();
        if (activeProviders.isEmpty()) {
            return UnifiedSearchResult.empty();
        }

        Map<String, ExternalSearchResult> successfulResults = new ConcurrentHashMap<>();
        List<String> failedProviders = Collections.synchronizedList(new ArrayList<>());

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (ExternalSourceProvider provider : activeProviders) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    ExternalSearchResult result = provider.search(query);
                    successfulResults.put(provider.getProviderId(), result);
                } catch (Exception e) {
                    failedProviders.add(provider.getProviderId());
                }
            }, executor);
            futures.add(future);
        }

        try {
            CompletableFuture<?>[] futuresArray = futures.toArray(new CompletableFuture<?>[0]);
            CompletableFuture.allOf(futuresArray)
                    .get(federatedTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            // Partial results on timeout
        }

        long totalCount = 0;
        Map<String, Integer> perProviderCounts = new LinkedHashMap<>();
        List<ExternalScholarlyWork> allWorks = new ArrayList<>();

        for (Map.Entry<String, ExternalSearchResult> entry : successfulResults.entrySet()) {
            ExternalSearchResult r = entry.getValue();
            perProviderCounts.put(entry.getKey(), r.works().size());
            totalCount += r.totalEstimatedResults();
            allWorks.addAll(r.works());
        }

        List<ExternalScholarlyWork> mergedWorks = deduplicateAndRank(allWorks, query.limit());
        long duration = Math.max(0L, System.currentTimeMillis() - startTime);

        return new UnifiedSearchResult(mergedWorks, perProviderCounts, failedProviders, totalCount, duration);
    }

    /**
     * Resolves an academic work by DOI, querying available providers until a match is found.
     */
    public Optional<ExternalScholarlyWork> resolveByDoi(String rawDoi) {
        if (rawDoi == null || rawDoi.isBlank()) return Optional.empty();
        Optional<String> normalizedDoi = DoiNormalizer.normalize(rawDoi);
        if (normalizedDoi.isEmpty()) return Optional.empty();

        for (ExternalSourceProvider provider : registry.getAvailableProviders()) {
            try {
                Optional<ExternalScholarlyWork> work = provider.findByDoi(normalizedDoi.get());
                if (work.isPresent()) {
                    return work;
                }
            } catch (Exception ignored) { }
        }
        return Optional.empty();
    }

    static List<ExternalScholarlyWork> deduplicateAndRank(List<ExternalScholarlyWork> works, int limit) {
        if (works == null || works.isEmpty()) return List.of();

        // Key: Normalized DOI or normalized title
        Map<String, ExternalScholarlyWork> mergedMap = new LinkedHashMap<>();

        for (ExternalScholarlyWork work : works) {
            String key = makeDeduplicationKey(work);
            if (!mergedMap.containsKey(key)) {
                mergedMap.put(key, work);
            } else {
                ExternalScholarlyWork existing = mergedMap.get(key);
                mergedMap.put(key, merge(existing, work));
            }
        }

        return mergedMap.values().stream()
                .sorted(Comparator
                        .comparing(ExternalScholarlyWork::hasFullText, Comparator.reverseOrder())
                        .thenComparing(ExternalScholarlyWork::citationCount, Comparator.reverseOrder())
                        .thenComparing(w -> w.publicationYear() != null ? w.publicationYear() : 0, Comparator.reverseOrder()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    static String makeDeduplicationKey(ExternalScholarlyWork work) {
        if (work.doi() != null && !work.doi().isBlank()) {
            return "doi:" + work.doi().toLowerCase(Locale.ROOT);
        }
        String title = work.title() != null ? work.title() : "";
        String cleanTitle = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (!cleanTitle.isBlank()) {
            return "title:" + cleanTitle;
        }
        return "id:" + work.providerId() + ":" + work.externalId();
    }

    static ExternalScholarlyWork merge(ExternalScholarlyWork a, ExternalScholarlyWork b) {
        String title = a.title().length() >= b.title().length() ? a.title() : b.title();

        List<String> combinedAuthors = new ArrayList<>(a.authors());
        for (String auth : b.authors()) {
            if (!combinedAuthors.contains(auth)) combinedAuthors.add(auth);
        }

        Integer year = a.publicationYear() != null ? a.publicationYear() : b.publicationYear();
        String venue = a.venueOrJournal() != null ? a.venueOrJournal() : b.venueOrJournal();
        String doi = a.doi() != null ? a.doi() : b.doi();
        String landing = a.landingPageUrl() != null ? a.landingPageUrl() : b.landingPageUrl();
        String pdf = a.pdfUrl() != null ? a.pdfUrl() : b.pdfUrl();
        String abs = (a.abstractText() != null && !a.abstractText().isBlank()) ? a.abstractText() : b.abstractText();
        int citations = Math.max(a.citationCount(), b.citationCount());
        boolean hasFullText = a.hasFullText() || b.hasFullText() || pdf != null;

        String repoA = (a.attribution() != null && a.attribution().sourceRepository() != null)
                ? a.attribution().sourceRepository() : a.providerId();
        String repoB = (b.attribution() != null && b.attribution().sourceRepository() != null)
                ? b.attribution().sourceRepository() : b.providerId();

        String combinedRepos = repoA;
        if (!combinedRepos.contains(repoB)) {
            combinedRepos = combinedRepos + ", " + repoB;
        }

        SourceAttribution mergedAttribution = SourceAttribution.of(
                a.providerId() + "+" + b.providerId(),
                combinedRepos,
                landing != null ? landing : a.attribution().persistentUri(),
                a.attribution().license(),
                hasFullText
        );

        return ExternalScholarlyWork.builder()
                .providerId(a.providerId())
                .externalId(a.externalId())
                .title(title)
                .authors(combinedAuthors)
                .publicationYear(year)
                .venueOrJournal(venue)
                .doi(doi)
                .landingPageUrl(landing)
                .pdfUrl(pdf)
                .abstractText(abs)
                .citationCount(citations)
                .hasFullText(hasFullText)
                .attribution(mergedAttribution)
                .build();
    }
}
