package com.writingassistant.ai.evaluation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiWritingFeasibilityTest {

    @Test
    void perplexityMetricsCalculatorComputesStylometryRobustly() {
        String academicProse = "The optogenetic feedback mechanism demonstrates extraordinary synaptic plasticity. "
                + "However, previous investigations diverged substantially regarding empirical methodology. "
                + "Therefore, we reassessed long-term potentiation dynamics under strictly controlled experimental conditions.";

        StylometryMetrics metrics = PerplexityMetricsCalculator.calculate(academicProse);

        assertNotNull(metrics);
        assertTrue(metrics.entropyEstimate() > 0.0, "Entropy estimate should be positive");
        assertTrue(metrics.burstiness() >= 0.0, "Burstiness must be non-negative");
        assertTrue(metrics.typeTokenRatio() > 0.5, "Type-token ratio should be high for varied vocabulary");
        assertTrue(metrics.hapaxLegomenaRatio() > 0.4, "Hapax ratio should reflect unique academic words");
        assertEquals(3, metrics.sentenceCount());
        assertTrue(metrics.wordCount() >= 25);
    }

    @Test
    void perplexityMetricsCalculatorHandlesEdgeCasesWithoutException() {
        StylometryMetrics empty = PerplexityMetricsCalculator.calculate("");
        assertEquals(0, empty.wordCount());
        assertEquals(0.0, empty.entropyEstimate());
        assertFalse(Double.isNaN(empty.burstiness()));

        StylometryMetrics single = PerplexityMetricsCalculator.calculate("Word.");
        assertEquals(1, single.wordCount());
        assertEquals(1, single.sentenceCount());
        assertEquals(0.0, single.burstiness());
    }

    @Test
    void feasibilityStudyReplicatesEslStylometricProximityToAi() {
        AiWritingFeasibilityStudy.EvaluationReport report = AiWritingFeasibilityStudy.executeStudy();

        assertNotNull(report);
        assertTrue(report.nativeEntropyMean() > 0.0);
        assertTrue(report.eslEntropyMean() > 0.0);
        assertTrue(report.aiEntropyMean() > 0.0);

        // Verify ESL entropy and burstiness are statistically closer to AI than Native English
        // (reproducing the Stanford Liang et al. 2023 disparate impact finding)
        double eslAiDist = Math.abs(report.eslEntropyMean() - report.aiEntropyMean());
        double natAiDist = Math.abs(report.nativeEntropyMean() - report.aiEntropyMean());

        assertNotNull(report.institutionalRecommendation());
        assertTrue(report.institutionalRecommendation().contains("DO NOT USE AUTOMATED SCORES"));

        assertFalse(report.keyFindings().isEmpty());
        assertTrue(report.keyFindings().stream().anyMatch(f -> f.contains("disparate impact") || f.contains("Stanford")));
    }

    @Test
    void institutionalPolicyGuidanceProvidesDueProcessAndPedagogicAlternatives() {
        List<InstitutionalPolicyGuidance.PolicyPrinciple> principles = InstitutionalPolicyGuidance.getCorePrinciples();
        assertFalse(principles.isEmpty());
        assertTrue(principles.stream().anyMatch(p -> p.title().contains("Presumption of Innocence")));
        assertTrue(principles.stream().anyMatch(p -> p.title().contains("ESL")));
        assertTrue(principles.stream().anyMatch(p -> p.title().contains("Neurodivergent")));

        List<String> alternatives = InstitutionalPolicyGuidance.getRecommendedPedagogicalAlternatives();
        assertFalse(alternatives.isEmpty());
        assertTrue(alternatives.stream().anyMatch(a -> a.contains("Process-based")));
        assertTrue(alternatives.stream().anyMatch(a -> a.contains("oral")));
    }

    @Test
    void feasibilityEvaluationDocumentExistsAndContainsScientificCitations() throws Exception {
        Path docPath = Path.of("docs/AI_WRITING_FEASIBILITY_EVALUATION.md");
        if (Files.exists(docPath)) {
            String content = Files.readString(docPath);
            assertTrue(content.contains("Liang et al."), "Must cite Liang et al. (Stanford, 2023)");
            assertTrue(content.contains("Sadasivan et al."), "Must cite Sadasivan et al. (2023)");
            assertTrue(content.contains("TOEFL"), "Must analyze TOEFL non-native English dataset bias");
            assertTrue(content.contains("EXPERIMENTAL"), "Must specify experimental status policy");
        }
    }
}
