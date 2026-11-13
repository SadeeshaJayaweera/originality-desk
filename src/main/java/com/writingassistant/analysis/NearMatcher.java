package com.writingassistant.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class NearMatcher {
    private static final int MAX_SEGMENTS = 20_000;
    private static final int MAX_POSTINGS = 128;
    private static final int MAX_CANDIDATE_VISITS = 250_000;
    private NearMatcher() { }
    private record Scored(EvidenceSegments.Segment segment, double value) { }

    static void addMatches(String text, TextNormalizer.NormalizedText submitted, int limit,
            List<EvidenceCorpus.Document> corpus, List<EvidenceCorpus.Hit> hits, List<String> limitations) {
        if (hits.size() >= EvidenceCorpus.MAX_MATCHES) return;
        List<EvidenceSegments.Segment> segments = new ArrayList<>();
        Map<String, List<Integer>> index = new HashMap<>();
        Map<Set<String>, Set<Integer>> termSetSources = new HashMap<>();
        outer: for (int i = 0; i < corpus.size(); i++) {
            var source = corpus.get(i);
            for (var segment : EvidenceSegments.segments(i, source.source().text(), source.normalized().tokens(), source.limit())) {
                if (segments.size() == MAX_SEGMENTS) {
                    EvidenceCorpus.limitation(limitations, "Lexical index capped at 20000 source windows; retrieval is incomplete.");
                    break outer;
                }
                int id = segments.size();
                segments.add(segment);
                termSetSources.computeIfAbsent(segment.terms(), key -> new HashSet<>()).add(i);
                for (String term : segment.terms()) {
                    List<Integer> posting = index.computeIfAbsent(term, key -> new ArrayList<>());
                    if (posting.size() < MAX_POSTINGS) posting.add(id);
                    else EvidenceCorpus.limitation(limitations, "Lexical term postings retain at most 128 windows; common-term retrieval may be incomplete.");
                }
            }
        }
        int visits = 0;
        for (var query : EvidenceSegments.segments(-1, text, submitted.tokens(), limit)) {
            List<String> terms = query.terms().stream().filter(index::containsKey)
                    .sorted(Comparator.<String>comparingInt(term -> index.get(term).size()).thenComparing(Comparator.naturalOrder()))
                    .limit(12).toList();
            Map<Integer, Integer> candidates = new HashMap<>();
            for (String term : terms) {
                for (int id : index.get(term)) {
                    if (++visits > MAX_CANDIDATE_VISITS) {
                        EvidenceCorpus.limitation(limitations, "Lexical candidate work capped at 250000 posting visits; evidence may be incomplete.");
                        return;
                    }
                    if (candidates.size() < 200 || candidates.containsKey(id)) candidates.merge(id, 1, Integer::sum);
                }
            }
            List<Integer> best = candidates.keySet().stream()
                    .sorted(Comparator.<Integer>comparingInt(candidates::get).reversed().thenComparingInt(Integer::intValue))
                    .limit(24).toList();
            List<Scored> scored = new ArrayList<>();
            for (int id : best) {
                var segment = segments.get(id);
                if (termSetSources.get(segment.terms()).size() >= Math.max(3, (int) Math.ceil(corpus.size() * 0.6))) {
                    EvidenceCorpus.limitation(limitations, "Lexical windows shared by at least 60% of sources (minimum 3) are suppressed as possible boilerplate.");
                    continue;
                }
                int shared = 0;
                for (String term : query.terms()) if (segment.terms().contains(term)) shared++;
                double value = (double) shared / (query.terms().size() + segment.terms().size() - shared);
                double ratio = (double) query.terms().size() / segment.terms().size();
                if (shared >= 5 && value >= 0.62 && ratio >= 0.5 && ratio <= 2) scored.add(new Scored(segment, value));
            }
            scored.sort(Comparator.comparingDouble(Scored::value).reversed()
                    .thenComparingInt(s -> s.segment().source()).thenComparingInt(s -> s.segment().start()));
            int retained = 0;
            for (Scored candidate : scored) {
                var s = candidate.segment();
                boolean duplicate = hits.stream().anyMatch(hit -> hit.source() == s.source()
                        && hit.start() <= query.start() && hit.end() >= query.end()
                        && hit.sourceStart() <= s.start() && hit.sourceEnd() >= s.end());
                if (duplicate) continue;
                hits.add(new EvidenceCorpus.Hit(s.source(), query.start(), query.end(), s.start(), s.end(), "LEXICAL_JACCARD", candidate.value()));
                if (hits.size() == EvidenceCorpus.MAX_MATCHES) {
                    EvidenceCorpus.limitation(limitations, "Evidence capped at 200 matches; coverage describes retained evidence only.");
                    return;
                }
                if (++retained == 3) break;
            }
        }
    }
}
