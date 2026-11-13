package com.writingassistant.similarity.embedding;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Benchmark evaluation dataset containing standardized academic sentence pairs,
 * conceptual paraphrases, cross-domain negative controls, and adversarial false-friends.
 */
public final class AcademicBenchmarkDataset {

    public enum PairType {
        VERBATIM,
        LEXICAL_PARAPHRASE,
        SEMANTIC_CONCEPTUAL,
        CROSS_DOMAIN_NEGATIVE,
        ADVERSARIAL_CONTRADICTION
    }

    public record BenchmarkPair(
            String id,
            String textA,
            String textB,
            PairType pairType,
            double expectedSimilarity,
            String domain
    ) implements Serializable {
        private static final long serialVersionUID = 1L;

        public BenchmarkPair {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(textA, "textA must not be null");
            Objects.requireNonNull(textB, "textB must not be null");
            Objects.requireNonNull(pairType, "pairType must not be null");
            if (expectedSimilarity < 0.0 || expectedSimilarity > 1.0) {
                throw new IllegalArgumentException("expectedSimilarity must be between 0.0 and 1.0");
            }
        }
    }

    private AcademicBenchmarkDataset() {}

    /**
     * Returns standard evaluation corpus representing typical academic similarity evaluation challenges.
     */
    public static List<BenchmarkPair> standardCorpus() {
        return List.of(
                // 1. Verbatim identity
                new BenchmarkPair(
                        "PAIR-01",
                        "Deep neural networks have shown remarkable success across various computer vision tasks.",
                        "Deep neural networks have shown remarkable success across various computer vision tasks.",
                        PairType.VERBATIM,
                        1.0,
                        "Computer Science"
                ),

                // 2. Lexical paraphrase (active/passive, synonyms)
                new BenchmarkPair(
                        "PAIR-02",
                        "The researchers synthesized novel organic polymers using low-temperature catalytic reactions.",
                        "Novel organic polymers were produced by the investigatory team utilizing catalytic reactions under low temperature.",
                        PairType.LEXICAL_PARAPHRASE,
                        0.92,
                        "Chemistry"
                ),

                // 3. Semantic / conceptual paraphrase (reworded, different sentence structure)
                new BenchmarkPair(
                        "PAIR-03",
                        "Excessive social media consumption among adolescents correlates strongly with sleep deprivation and anxiety disorders.",
                        "Teenagers who spend prolonged hours on digital networking platforms frequently exhibit disrupted sleep patterns and elevated psychological distress.",
                        PairType.SEMANTIC_CONCEPTUAL,
                        0.88,
                        "Psychology"
                ),

                // 4. Cross-domain negative control (completely unrelated domains)
                new BenchmarkPair(
                        "PAIR-04",
                        "Gradient descent optimization with momentum accelerates convergence during neural network backpropagation.",
                        "Cardiovascular patients receiving beta-blocker therapy showed a statistically significant reduction in arterial systolic pressure.",
                        PairType.CROSS_DOMAIN_NEGATIVE,
                        0.05,
                        "Multi-Domain"
                ),

                // 5. Adversarial false-friend (shared keywords, contradictory/opposite finding)
                new BenchmarkPair(
                        "PAIR-05",
                        "The randomized clinical trial demonstrated that the antiviral medication significantly accelerated patient recovery times.",
                        "The randomized clinical trial demonstrated that the antiviral medication completely failed to accelerate patient recovery times and induced adverse side effects.",
                        PairType.ADVERSARIAL_CONTRADICTION,
                        0.35,
                        "Medicine"
                )
        );
    }
}
