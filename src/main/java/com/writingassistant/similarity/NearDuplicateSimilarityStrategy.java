package com.writingassistant.similarity;

import com.writingassistant.analysis.EvidenceCorpus;
import com.writingassistant.analysis.EvidenceEngine;
import com.writingassistant.analysis.NearMatcher;
import com.writingassistant.analysis.OverlapCoverage;
import com.writingassistant.analysis.TextNormalizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Concrete similarity strategy implementing near-duplicate and paraphrase detection
 * via windowed lexical shingling and Jaccard token overlap metrics.
 */
public class NearDuplicateSimilarityStrategy implements SimilarityStrategy {

    public static final String STRATEGY_NAME = "near-duplicate-window";

    @Override
    public SimilarityStrategyType getType() {
        return SimilarityStrategyType.NEAR_DUPLICATE;
    }

    @Override
    public String getName() {
        return STRATEGY_NAME;
    }

    @Override
    public String getDescription() {
        return "Lexical window indexing and Jaccard token overlap for paraphrase and near-duplicate detection.";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public StrategyMatchResult analyze(SimilarityTarget target, List<SimilaritySource> corpus, SimilarityOptions options) {
        Objects.requireNonNull(target, "target must not be null");
        if (corpus == null || corpus.isEmpty() || target.text().isBlank()) {
            return StrategyMatchResult.empty(SimilarityStrategyType.NEAR_DUPLICATE, STRATEGY_NAME);
        }

        long started = System.currentTimeMillis();
        List<String> limitations = new ArrayList<>();

        var normalizedTarget = TextNormalizer.normalize(target.text());
        int targetLimit = Math.min(target.activeLimit(), target.text().length());

        List<EvidenceCorpus.Document> corpusDocs = new ArrayList<>();
        Map<Integer, SimilaritySource> sourceMap = new HashMap<>();

        for (int i = 0; i < corpus.size(); i++) {
            SimilaritySource source = corpus.get(i);
            if (source.sourceId().equals(target.documentId())) {
                EvidenceCorpus.limitation(limitations, "Self-matching excluded for source: " + source.sourceId());
                continue;
            }
            sourceMap.put(corpusDocs.size(), source);
            EvidenceEngine.Source adapterSource = new EvidenceEngine.Source(
                    source.sourceId(), source.title(), source.text(), source.sourceType()
            );
            corpusDocs.add(new EvidenceCorpus.Document(
                    adapterSource,
                    TextNormalizer.normalize(source.text()),
                    Math.min(source.activeLimit(), source.text().length())
            ));
        }

        List<EvidenceCorpus.Hit> hits = new ArrayList<>();
        NearMatcher.addMatches(target.text(), normalizedTarget, targetLimit, corpusDocs, hits, limitations);

        var coverage = OverlapCoverage.calculate(normalizedTarget.tokens(), targetLimit, hits);

        List<SimilaritySpan> spans = new ArrayList<>();
        for (var hit : hits) {
            SimilaritySource source = sourceMap.get(hit.source());
            String sourceText = source != null ? source.text() : "";
            String targetPassage = target.text().substring(hit.start(), hit.end());
            String sourcePassage = sourceText.length() >= hit.sourceEnd()
                    ? sourceText.substring(hit.sourceStart(), hit.sourceEnd()) : "";

            spans.add(new SimilaritySpan(
                    source != null ? source.sourceId() : "unknown",
                    hit.start(), hit.end(),
                    hit.sourceStart(), hit.sourceEnd(),
                    hit.method(),
                    hit.value(),
                    targetPassage,
                    sourcePassage
            ));
        }

        long duration = System.currentTimeMillis() - started;
        Map<String, Object> telemetry = Map.of(
                "hitCount", spans.size(),
                "durationMs", duration,
                "corpusSize", corpusDocs.size()
        );

        return new StrategyMatchResult(
                SimilarityStrategyType.NEAR_DUPLICATE,
                STRATEGY_NAME,
                spans,
                coverage.near(),
                limitations,
                telemetry
        );
    }
}
