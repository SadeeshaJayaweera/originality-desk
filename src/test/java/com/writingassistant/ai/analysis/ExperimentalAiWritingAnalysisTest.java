package com.writingassistant.ai.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExperimentalAiWritingAnalysisTest {

    private static final String LONG_ACADEMIC_ESSAY =
            "The rapid emergence of generative artificial intelligence has fundamentally disrupted traditional assumptions "
            + "regarding authorship, pedagogy, and textual originality across higher education institutions. "
            + "Although automated tools offer unprecedented opportunities for drafting assistance and multilingual translation, "
            + "they simultaneously introduce profound epistemological challenges concerning the nature of student scholarship. "
            + "In this investigation, we analyze empirical data collected across three comprehensive university cohorts. "
            + "Our methodology integrates rigorous statistical stylometry, quantitative lexical variance analysis, and qualitative "
            + "student interviews to assess the broader educational implications of algorithmic writing technologies.\n\n"
            + "Section 2 reviews the established academic literature on textual entropy and burstiness heuristics. "
            + "Previous researchers, notably Liang and colleagues at Stanford University, demonstrated that statistical detectors "
            + "disproportionately misclassify non-native English writing due to constrained vocabulary profiles. "
            + "Consequently, institutional policies that rely on automated detection scores risk perpetuating systemic biases "
            + "against international and multilingual scholars. We conclude by outlining pedagogic frameworks that emphasize "
            + "process-oriented writing portfolios, iterative drafting history, and authentic classroom dialogues rather than "
            + "punitive algorithmic surveillance mechanisms.";

    @Test
    void defaultAnalyzerIsDisabledByPolicy() {
        ExperimentalAiWritingAnalyzer analyzer = new ExperimentalAiWritingAnalyzer();
        assertFalse(analyzer.getConfig().enabled());

        AiWritingAnalysisResult result = analyzer.analyze(LONG_ACADEMIC_ESSAY);

        assertNotNull(result);
        assertEquals(AiWritingAnalysisResult.STATUS_DISABLED_BY_POLICY, result.status());
        assertFalse(result.active());
        assertNull(result.metrics());
        assertEquals("NOT_EVALUATED", result.entropyProfile());
        assertNotNull(result.ethicalWarning());
        assertTrue(result.ethicalWarning().contains("DISCIPLINARY PROHIBITION"));
    }

    @Test
    void analyzerRequiresExplicitAcknowledgmentOfDisparateImpact() {
        // enabled = true, but acknowledgeDisparateImpact = false
        AiWritingConfig config = new AiWritingConfig(true, false, 150, true);
        ExperimentalAiWritingAnalyzer analyzer = new ExperimentalAiWritingAnalyzer(config);

        AiWritingAnalysisResult result = analyzer.analyze(LONG_ACADEMIC_ESSAY);

        assertNotNull(result);
        assertEquals(AiWritingAnalysisResult.STATUS_UNACKNOWLEDGED_DISPARATE_IMPACT, result.status());
        assertFalse(result.active());
        assertTrue(result.summaryObservation().contains("requires explicit institutional acknowledgment"));
    }

    @Test
    void analyzerRejectsShortPassagesAsStatisticallyInvalid() {
        AiWritingConfig config = AiWritingConfig.experimental(true);
        ExperimentalAiWritingAnalyzer analyzer = new ExperimentalAiWritingAnalyzer(config);

        String shortSnippet = "This is a brief introductory sentence that has far too few words to measure statistical variance.";
        AiWritingAnalysisResult result = analyzer.analyze(shortSnippet);

        assertNotNull(result);
        assertEquals(AiWritingAnalysisResult.STATUS_INSUFFICIENT_LENGTH, result.status());
        assertFalse(result.active());
        assertTrue(result.summaryObservation().contains("below the minimum threshold of 150 words"));
        assertNull(result.metrics());
    }

    @Test
    void experimentalAnalyzerProducesDescriptiveProfilesWithoutFakeScores() {
        AiWritingConfig config = AiWritingConfig.experimental(true);
        ExperimentalAiWritingAnalyzer analyzer = new ExperimentalAiWritingAnalyzer(config);

        AiWritingAnalysisResult result = analyzer.analyze(LONG_ACADEMIC_ESSAY);

        assertNotNull(result);
        assertEquals(AiWritingAnalysisResult.STATUS_EXPERIMENTAL_STATISTICAL, result.status());
        assertTrue(result.active());

        // Genuine statistical metrics
        assertNotNull(result.metrics());
        assertTrue(result.metrics().entropyEstimate() > 0.0);
        assertTrue(result.metrics().burstiness() >= 0.0);
        assertTrue(result.metrics().typeTokenRatio() > 0.0);
        assertTrue(result.metrics().wordCount() >= 150);

        // Descriptive profiles, NOT punitive scores
        assertNotNull(result.entropyProfile());
        assertFalse(result.entropyProfile().contains("%"));
        assertNotNull(result.burstinessProfile());
        assertFalse(result.burstinessProfile().contains("%"));

        // Mandatory ethical safeguards
        assertNotNull(result.ethicalWarning());
        assertTrue(result.ethicalWarning().contains("NEVER be used to penalize students"));
        assertTrue(result.ethicalWarning().contains("Stanford Liang et al., 2023"));

        // Documented limitations
        assertFalse(result.limitations().isEmpty());
        assertTrue(result.limitations().stream().anyMatch(l -> l.contains("cannot forensicly determine")));
    }
}
