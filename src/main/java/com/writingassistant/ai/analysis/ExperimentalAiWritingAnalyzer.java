package com.writingassistant.ai.analysis;

import com.writingassistant.ai.evaluation.PerplexityMetricsCalculator;
import com.writingassistant.ai.evaluation.StylometryMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Objective, non-punitive statistical stylometry analyzer providing experimental
 * text complexity profiles (entropy, sentence burstiness, lexical diversity).
 */
public class ExperimentalAiWritingAnalyzer {

    private final AiWritingConfig config;

    public ExperimentalAiWritingAnalyzer() {
        this(AiWritingConfig.disabled());
    }

    public ExperimentalAiWritingAnalyzer(AiWritingConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    public AiWritingConfig getConfig() {
        return config;
    }

    public AiWritingAnalysisResult analyze(String text) {
        if (!config.enabled()) {
            return AiWritingAnalysisResult.disabled();
        }

        if (!config.acknowledgeDisparateImpact()) {
            return AiWritingAnalysisResult.unacknowledged();
        }

        if (text == null || text.isBlank()) {
            return AiWritingAnalysisResult.insufficientLength(0, config.minimumWordCount());
        }

        String[] tokens = text.strip().split("\\s+");
        int wordCount = tokens.length;
        if (wordCount < config.minimumWordCount()) {
            return AiWritingAnalysisResult.insufficientLength(wordCount, config.minimumWordCount());
        }

        // Compute genuine statistical stylometry metrics
        StylometryMetrics metrics = PerplexityMetricsCalculator.calculate(text);

        // Derive descriptive statistical profiles
        String entropyProfile;
        if (metrics.entropyEstimate() >= 5.0) {
            entropyProfile = "HIGH_LEXICAL_DIVERSITY";
        } else if (metrics.entropyEstimate() >= 3.8) {
            entropyProfile = "MODERATE_LEXICAL_DIVERSITY";
        } else {
            entropyProfile = "CONSTRAINED_LEXICAL_DIVERSITY";
        }

        String burstinessProfile;
        if (metrics.burstiness() >= 0.50) {
            burstinessProfile = "HIGH_RHYTHMIC_VARIANCE";
        } else if (metrics.burstiness() >= 0.25) {
            burstinessProfile = "MODERATE_RHYTHMIC_VARIANCE";
        } else {
            burstinessProfile = "UNIFORM_SENTENCE_CADENCE";
        }

        String summary = String.format(Locale.ROOT,
                "Text exhibits an entropy estimate of %.2f bits (%s) and sentence burstiness coefficient of %.2f (%s) across %d words and %d sentences.",
                metrics.entropyEstimate(), entropyProfile, metrics.burstiness(), burstinessProfile,
                metrics.wordCount(), metrics.sentenceCount()
        );

        List<String> limitations = new ArrayList<>();
        limitations.add("Statistical stylometry measures text entropy and sentence structure only; it cannot forensicly determine human vs machine authorship.");
        limitations.add("Stanford University research (Liang et al., 2023) established that low entropy frequently indicates non-native English writing rather than AI generation.");
        limitations.add("Paraphrasing, prompting changes, and translation round-trips dramatically alter statistical profiles without altering underlying authorship.");
        limitations.add("This analysis is experimental and strictly prohibited from being used as evidence in student conduct or academic integrity hearings.");

        return new AiWritingAnalysisResult(
                AiWritingAnalysisResult.STATUS_EXPERIMENTAL_STATISTICAL,
                true,
                metrics,
                entropyProfile,
                burstinessProfile,
                summary,
                AiWritingAnalysisResult.DEFAULT_ETHICAL_WARNING,
                limitations
        );
    }
}
