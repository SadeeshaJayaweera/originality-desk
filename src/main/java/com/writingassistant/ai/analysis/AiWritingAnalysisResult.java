package com.writingassistant.ai.analysis;

import com.writingassistant.ai.evaluation.StylometryMetrics;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Objective, non-punitive statistical output from experimental AI-writing stylometry analysis.
 * Explicitly avoids binary accusations, fake confidence percentages, or plagiarism verdicts.
 */
public record AiWritingAnalysisResult(
        String status,
        boolean active,
        StylometryMetrics metrics,
        String entropyProfile,
        String burstinessProfile,
        String summaryObservation,
        String ethicalWarning,
        List<String> limitations
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_DISABLED_BY_POLICY = "DISABLED_BY_POLICY";
    public static final String STATUS_UNACKNOWLEDGED_DISPARATE_IMPACT = "UNACKNOWLEDGED_DISPARATE_IMPACT";
    public static final String STATUS_INSUFFICIENT_LENGTH = "INSUFFICIENT_TEXT_LENGTH";
    public static final String STATUS_EXPERIMENTAL_STATISTICAL = "EXPERIMENTAL_STATISTICAL_OBSERVATION";

    public static final String DEFAULT_ETHICAL_WARNING =
            "DISCIPLINARY PROHIBITION: Statistical stylometry metrics (lexical entropy, sentence burstiness) "
            + "are non-deterministic and must NEVER be used to penalize students, deduct marks, or accuse authors of AI misconduct. "
            + "Empirical research (Stanford Liang et al., 2023) proves these metrics suffer from severe false-positive bias "
            + "against non-native English (ESL/EFL) and neurodivergent writers.";

    public AiWritingAnalysisResult {
        Objects.requireNonNull(status, "status must not be null");
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }

    public static AiWritingAnalysisResult disabled() {
        return new AiWritingAnalysisResult(
                STATUS_DISABLED_BY_POLICY,
                false,
                null,
                "NOT_EVALUATED",
                "NOT_EVALUATED",
                "AI writing detection is disabled by platform policy to protect student equity and prevent algorithmic false positives.",
                DEFAULT_ETHICAL_WARNING,
                List.of("Disabled by default: statistical AI text detection lacks forensic and scientific reliability.")
        );
    }

    public static AiWritingAnalysisResult unacknowledged() {
        return new AiWritingAnalysisResult(
                STATUS_UNACKNOWLEDGED_DISPARATE_IMPACT,
                false,
                null,
                "NOT_EVALUATED",
                "NOT_EVALUATED",
                "Experimental AI writing analysis requires explicit institutional acknowledgment of ESL disparate impact risks.",
                DEFAULT_ETHICAL_WARNING,
                List.of("Feature blocked: acknowledgeDisparateImpact flag is required to enable experimental stylometry analysis.")
        );
    }

    public static AiWritingAnalysisResult insufficientLength(int wordCount, int minRequired) {
        return new AiWritingAnalysisResult(
                STATUS_INSUFFICIENT_LENGTH,
                false,
                null,
                "INSUFFICIENT_DATA",
                "INSUFFICIENT_DATA",
                "Document contains " + wordCount + " words, which is below the minimum threshold of " + minRequired + " words required for statistical stylometry.",
                DEFAULT_ETHICAL_WARNING,
                List.of("Statistical variance cannot be calculated reliably on passages shorter than " + minRequired + " words.")
        );
    }
}
