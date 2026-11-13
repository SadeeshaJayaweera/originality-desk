package com.writingassistant.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Local corpus evidence, not a plagiarism verdict. All offsets index the original Java String
 * (UTF-16, half-open). Overlaps are fractions in [0,1], using normalized word counts.
 * Exact coverage is a union; near coverage is the additional union of lexical windows, not
 * a count of copied words. REFERENCE_AWARE excludes detected trailing reference lists in
 * both submitted text and sources. Page numbers are 1-based, inferred only from form feeds.
 * No network requests, citation validation, embeddings, OCR or AI-authorship inference occur.
 */
public final class EvidenceEngine {
    public static final String ENGINE_VERSION = "local-evidence-1.0";
    public static final int MAX_SOURCES = 50;
    public static final int MAX_CORPUS_CHARACTERS = 1_000_000;

    public record Source(String id, String title, String text, String type) { }
    public record Result(String engineVersion, String profile, int wordCount, double exactOverlap,
            double nearOverlap, double totalOverlap, List<Match> matches, List<Citation> citations,
            List<String> limitations, String semanticStatus, String aiWritingStatus) {
        public Result {
            matches = List.copyOf(matches);
            citations = List.copyOf(citations);
            limitations = List.copyOf(limitations);
        }
    }
    public record Match(String sourceId, String sourceTitle, String sourceType, String submittedPassage,
            String sourcePassage, String method, double value, int start, int end, int sourceStart,
            int sourceEnd, int page, String citationObservation) { }
    public record Citation(String text, String kind, int start, int end, boolean uncertain) { }

    private enum Profile {
        QUICK(12, false), STANDARD(8, true), REFERENCE_AWARE(6, true);
        final int shingleWords;
        final boolean near;
        Profile(int shingleWords, boolean near) { this.shingleWords = shingleWords; this.near = near; }
        static Profile parse(String name) {
            if (name == null) throw new IllegalArgumentException("A profile is required: QUICK, STANDARD or REFERENCE_AWARE");
            try { return valueOf(name.trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("Unknown analysis profile: " + name, e); }
        }
    }

    public Result analyze(String documentId, String title, String text, List<Source> sources, String profile) {
        requireMetadata(documentId, "documentId");
        requireMetadata(title, "title");
        requireText(text);
        Profile selected = Profile.parse(profile);
        if (sources == null || sources.size() > MAX_SOURCES) throw new IllegalArgumentException("At most 50 nonnull sources are allowed");
        List<String> limitations = methodology(selected);
        List<EvidenceCorpus.Document> corpus = prepareSources(documentId, sources, selected, limitations);
        var normalized = TextNormalizer.normalize(text);
        var scan = CitationDetector.scan(text);
        int limit = selected == Profile.REFERENCE_AWARE ? scan.referenceStart() : text.length();
        if (scan.capped()) limitations.add("Citation observations capped at 300; additional patterns may be absent.");
        List<EvidenceCorpus.Hit> hits = ExactMatcher.match(normalized, limit, corpus, selected.shingleWords, limitations);
        if (selected.near) NearMatcher.addMatches(text, normalized, limit, corpus, hits, limitations);
        var coverage = OverlapCoverage.calculate(normalized.tokens(), limit, hits);
        List<Match> matches = new ArrayList<>();
        for (var hit : hits) {
            Source source = corpus.get(hit.source()).source();
            matches.add(new Match(source.id(), source.title(), source.type(), text.substring(hit.start(), hit.end()),
                    source.text().substring(hit.sourceStart(), hit.sourceEnd()), hit.method(), hit.value(),
                    hit.start(), hit.end(), hit.sourceStart(), hit.sourceEnd(), page(text, hit.start()),
                    CitationDetector.observation(hit.start(), hit.end(), scan)));
        }
        matches.sort(Comparator.comparingInt(Match::start).thenComparing(Match::sourceId)
                .thenComparing(Match::method).thenComparingInt(Match::sourceStart));
        return new Result(ENGINE_VERSION, selected.name(), normalized.tokens().size(), coverage.exact(), coverage.near(),
                coverage.total(), matches, scan.citations(), limitations, "NOT_IMPLEMENTED", "NOT_IMPLEMENTED");
    }

    private static List<EvidenceCorpus.Document> prepareSources(String documentId, List<Source> sources,
            Profile profile, List<String> limitations) {
        Set<String> ids = new HashSet<>();
        List<EvidenceCorpus.Document> corpus = new ArrayList<>();
        long total = 0;
        for (Source source : sources) {
            if (source == null) throw new IllegalArgumentException("Sources must not contain null");
            requireMetadata(source.id(), "source id");
            requireMetadata(source.title(), "source title");
            requireMetadata(source.type(), "source type");
            requireText(source.text());
            if (!ids.add(source.id())) throw new IllegalArgumentException("Duplicate source id: " + source.id());
            total += source.text().length();
            if (total > MAX_CORPUS_CHARACTERS) throw new IllegalArgumentException("Source corpus exceeds 1000000 UTF-16 characters");
            if (source.id().equals(documentId)) {
                EvidenceCorpus.limitation(limitations, "Source with the submitted documentId excluded to prevent self-matching.");
                continue;
            }
            int limit = profile == Profile.REFERENCE_AWARE ? CitationDetector.scan(source.text()).referenceStart() : source.text().length();
            corpus.add(new EvidenceCorpus.Document(source, TextNormalizer.normalize(source.text()), limit));
        }
        return corpus;
    }

    private static List<String> methodology(Profile profile) {
        List<String> result = new ArrayList<>();
        result.add("Only supplied corpus text is searched; no web or repository search. Overlap is evidence, not a plagiarism verdict.");
        result.add("NFKC compatibility/case/whitespace normalization removes invisible formatting and soft hyphens; offsets remain original UTF-16 half-open ranges.");
        result.add("Exact normalized word shingles require " + profile.shingleWords + " words; short, low-diversity and known boilerplate shingles are suppressed. Fingerprint tokens longer than 64 characters are omitted.");
        result.add("Coverage fractions use unions of normalized words in retained evidence: exact first, near only additional words, total their union. Near windows do not imply every word was copied.");
        result.add("Limits: 50 sources, 200000 characters per text, 1000000 corpus characters, 200 matches, 2000 characters and 160 words per exact passage; retrieval budgets can omit evidence.");
        result.add(profile.near
                ? "Near evidence is lexical content-word set Jaccard >= 0.62 with >= 5 shared terms on 6..80-word sentence/paragraph windows; up to 12 query terms, 200 candidates, 24 reranked and 3 retained per window. Not semantic similarity."
                : "QUICK runs exact matching only; lexical near matching is disabled.");
        result.add(profile == Profile.REFERENCE_AWARE
                ? "REFERENCE_AWARE excludes text from a standalone References, Bibliography or Works Cited heading to the end in submitted/source matching and the covered-word denominator; wordCount still includes the whole submission. Heading detection is uncertain."
                : "Reference lists remain in the covered-word denominator and matching for this profile.");
        result.add("Citations, quotations and reference lists are heuristic, uncertain observations; no attribution or bibliographic validation. Quoted/cited prose is not automatically exempted.");
        result.add("Page is 1-based from form-feed boundaries only; without them it is 1, not verified rendered pagination. Semantic analysis, OCR and AI-authorship scoring are not implemented.");
        return result;
    }

    private static int page(String text, int start) {
        int page = 1;
        for (int i = 0; i < start; i++) if (text.charAt(i) == '\f') page++;
        return page;
    }
    private static void requireMetadata(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 512) throw new IllegalArgumentException(name + " must contain 1..512 characters");
    }
    private static void requireText(String text) {
        if (text == null || text.length() > DocumentExtractor.MAX_CHARACTERS) throw new IllegalArgumentException("Text must be nonnull and at most 200000 UTF-16 characters");
    }
}
