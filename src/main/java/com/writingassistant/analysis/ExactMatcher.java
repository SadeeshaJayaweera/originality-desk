package com.writingassistant.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ExactMatcher {
    private static final int MAX_INDEX_SHINGLES = 100_000;
    private static final int MAX_SEED_VISITS = 250_000;
    private static final int MAX_POSTINGS_PER_SOURCE = 8;
    private ExactMatcher() { }
    private record Seed(int source, int token) { }
    private static final class Bucket {
        final List<Seed> seeds = new ArrayList<>();
        final Set<Integer> sources = new HashSet<>();
        final Map<Integer, Integer> counts = new HashMap<>();
    }

    public static List<EvidenceCorpus.Hit> match(TextNormalizer.NormalizedText submitted, int limit,
            List<EvidenceCorpus.Document> corpus, int words, List<String> limitations) {
        Map<String, Bucket> index = new HashMap<>();
        int indexed = 0;
        outer: for (int source = 0; source < corpus.size(); source++) {
            var document = corpus.get(source);
            for (var fingerprint : Fingerprints.wordShingles(document.normalized(), words)) {
                if (fingerprint.end() > document.limit() || !Fingerprints.informative(fingerprint.value())) continue;
                if (++indexed > MAX_INDEX_SHINGLES) {
                    EvidenceCorpus.limitation(limitations, "Exact shingle index capped at 100000 eligible shingles; corpus retrieval is incomplete.");
                    break outer;
                }
                Bucket bucket = index.computeIfAbsent(fingerprint.value(), key -> new Bucket());
                bucket.sources.add(source);
                int count = bucket.counts.getOrDefault(source, 0);
                if (count < MAX_POSTINGS_PER_SOURCE) {
                    bucket.seeds.add(new Seed(source, fingerprint.tokenIndex()));
                    bucket.counts.put(source, count + 1);
                } else EvidenceCorpus.limitation(limitations, "Repeated exact shingles retain at most 8 positions per source; repetitive evidence may be omitted.");
            }
        }
        List<EvidenceCorpus.Hit> hits = new ArrayList<>();
        Map<Long, Integer> coveredDiagonals = new HashMap<>();
        var tokens = submitted.tokens();
        int visits = 0;
        for (var fingerprint : Fingerprints.wordShingles(submitted, words)) {
            if (fingerprint.end() > limit) continue;
            Bucket bucket = index.get(fingerprint.value());
            if (bucket == null) continue;
            if (bucket.sources.size() >= Math.max(3, (int) Math.ceil(corpus.size() * 0.6))) {
                EvidenceCorpus.limitation(limitations, "Shingles shared by at least 60% of sources (minimum 3) are suppressed as possible boilerplate.");
                continue;
            }
            for (Seed seed : bucket.seeds) {
                if (++visits > MAX_SEED_VISITS) {
                    EvidenceCorpus.limitation(limitations, "Exact candidate work capped at 250000 seed visits; evidence may be incomplete.");
                    return hits;
                }
                int submittedToken = fingerprint.tokenIndex();
                int delta = seed.token() - submittedToken;
                long diagonal = ((long) seed.source() << 32) | Integer.toUnsignedLong(delta);
                int previouslyCovered = coveredDiagonals.getOrDefault(diagonal, -1);
                if (previouslyCovered >= submittedToken + words) continue;
                var source = corpus.get(seed.source());
                var sourceTokens = source.normalized().tokens();
                int first = submittedToken;
                int sourceFirst = seed.token();
                int count = words;
                while (first > Math.max(0, previouslyCovered - words + 1) && sourceFirst > 0 && count < EvidenceSegments.MAX_WORDS
                        && tokens.get(first - 1).value().equals(sourceTokens.get(sourceFirst - 1).value())
                        && tokens.get(submittedToken + words - 1).end() - tokens.get(first - 1).start() <= EvidenceSegments.MAX_PASSAGE
                        && sourceTokens.get(seed.token() + words - 1).end() - sourceTokens.get(sourceFirst - 1).start() <= EvidenceSegments.MAX_PASSAGE) {
                    first--; sourceFirst--; count++;
                }
                while (first + count < tokens.size() && sourceFirst + count < sourceTokens.size()
                        && count < EvidenceSegments.MAX_WORDS
                        && tokens.get(first + count).end() <= limit && sourceTokens.get(sourceFirst + count).end() <= source.limit()
                        && tokens.get(first + count).value().equals(sourceTokens.get(sourceFirst + count).value())
                        && tokens.get(first + count).end() - tokens.get(first).start() <= EvidenceSegments.MAX_PASSAGE
                        && sourceTokens.get(sourceFirst + count).end() - sourceTokens.get(sourceFirst).start() <= EvidenceSegments.MAX_PASSAGE) count++;
                int start = tokens.get(first).start();
                int end = tokens.get(first + count - 1).end();
                int sourceStart = sourceTokens.get(sourceFirst).start();
                int sourceEnd = sourceTokens.get(sourceFirst + count - 1).end();
                if (end - start > EvidenceSegments.MAX_PASSAGE || sourceEnd - sourceStart > EvidenceSegments.MAX_PASSAGE) continue;

                // Recheck the whole extended passage: an informative seed may sit inside boilerplate.
                String normalizedPassage = String.join(" ", tokens.subList(first, first + count).stream().map(TextNormalizer.Token::value).toList());
                if (!Fingerprints.informative(normalizedPassage)) continue;
                coveredDiagonals.put(diagonal, first + count);
                hits.add(new EvidenceCorpus.Hit(seed.source(), start, end, sourceStart, sourceEnd, "EXACT", 1.0));
                if (hits.size() == EvidenceCorpus.MAX_MATCHES) {
                    EvidenceCorpus.limitation(limitations, "Evidence capped at 200 matches; coverage describes retained evidence only.");
                    return hits;
                }
            }
        }
        return hits;
    }
}
