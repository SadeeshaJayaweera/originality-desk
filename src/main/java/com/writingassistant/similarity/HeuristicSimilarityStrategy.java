package com.writingassistant.similarity;

import com.writingassistant.core.similarity.DefaultSimilarityChecker;
import com.writingassistant.core.similarity.SimilarityReport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Concrete similarity strategy implementing deterministic string metric analysis
 * combining word n-grams, Longest Common Subsequence (LCS), and Levenshtein edit distance.
 */
public class HeuristicSimilarityStrategy implements SimilarityStrategy {

    public static final String STRATEGY_NAME = "heuristic-string-metrics";
    private final DefaultSimilarityChecker checker;

    public HeuristicSimilarityStrategy() {
        this.checker = new DefaultSimilarityChecker();
    }

    public HeuristicSimilarityStrategy(DefaultSimilarityChecker checker) {
        this.checker = Objects.requireNonNull(checker, "checker must not be null");
    }

    @Override
    public SimilarityStrategyType getType() {
        return SimilarityStrategyType.HEURISTIC;
    }

    @Override
    public String getName() {
        return STRATEGY_NAME;
    }

    @Override
    public String getDescription() {
        return "Deterministic passage heuristic analysis combining N-gram overlap, Word-LCS, and Levenshtein edit distance.";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public StrategyMatchResult analyze(SimilarityTarget target, List<SimilaritySource> corpus, SimilarityOptions options) {
        Objects.requireNonNull(target, "target must not be null");
        if (corpus == null || corpus.isEmpty() || target.text().isBlank()) {
            return StrategyMatchResult.empty(SimilarityStrategyType.HEURISTIC, STRATEGY_NAME);
        }

        long started = System.currentTimeMillis();
        List<SimilaritySpan> spans = new ArrayList<>();
        double highestScore = 0.0;
        double threshold = options != null ? options.minScoreThreshold() : 0.2;

        String targetText = target.text().substring(0, Math.min(target.activeLimit(), target.text().length()));

        for (SimilaritySource source : corpus) {
            if (source.sourceId().equals(target.documentId())) continue;

            String sourceText = source.text().substring(0, Math.min(source.activeLimit(), source.text().length()));
            if (sourceText.isBlank()) continue;

            // Bounded check for passages
            String boundedSource = sourceText.length() > DefaultSimilarityChecker.MAX_TEXT_CHARS
                    ? sourceText.substring(0, DefaultSimilarityChecker.MAX_TEXT_CHARS) : sourceText;
            String boundedTarget = targetText.length() > DefaultSimilarityChecker.MAX_TEXT_CHARS
                    ? targetText.substring(0, DefaultSimilarityChecker.MAX_TEXT_CHARS) : targetText;

            SimilarityReport report = checker.checkSimilarity(boundedSource, boundedTarget);
            double score = report.getSimilarityScore() / 100.0;

            if (score > highestScore) highestScore = score;

            if (score >= threshold) {
                spans.add(new SimilaritySpan(
                        source.sourceId(),
                        0, boundedTarget.length(),
                        0, boundedSource.length(),
                        "HEURISTIC",
                        score,
                        boundedTarget,
                        boundedSource
                ));
            }
        }

        long duration = System.currentTimeMillis() - started;
        Map<String, Object> telemetry = Map.of(
                "hitCount", spans.size(),
                "durationMs", duration,
                "highestScore", highestScore,
                "corpusSize", corpus.size()
        );

        return new StrategyMatchResult(
                SimilarityStrategyType.HEURISTIC,
                STRATEGY_NAME,
                spans,
                highestScore,
                List.of(),
                telemetry
        );
    }
}
