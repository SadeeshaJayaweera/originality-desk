package com.writingassistant.similarity.semantic;

import com.writingassistant.similarity.ExactSimilarityStrategy;
import com.writingassistant.similarity.SemanticSimilarityStrategy;
import com.writingassistant.similarity.SimilarityOptions;
import com.writingassistant.similarity.SimilaritySource;
import com.writingassistant.similarity.SimilaritySpan;
import com.writingassistant.similarity.SimilarityStrategyType;
import com.writingassistant.similarity.SimilarityTarget;
import com.writingassistant.similarity.StrategyMatchResult;
import com.writingassistant.similarity.onnx.DeterministicEmbeddingEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SemanticSimilarityAnalysisTest {

    private DeterministicEmbeddingEngine engine;
    private SemanticSimilarityAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        engine = new DeterministicEmbeddingEngine();
        analyzer = new SemanticSimilarityAnalyzer(engine);
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    void semanticAnalysisDetectsConceptualParaphraseWhereExactMatchingFails() {
        String targetProse = "Convolutional neural architectures significantly enhance automated image classification in medicine.";
        String sourceProse = "Deep vision models demonstrate remarkable efficacy in diagnostic radiology classification.";

        SimilarityTarget target = SimilarityTarget.of("doc-target", "Submitted Paper", targetProse);
        SimilaritySource source = SimilaritySource.of("src-ref", "Published Paper", sourceProse, "REFERENCE");

        // 1. Verify Exact Match fails or yields very low score for conceptual paraphrase
        ExactSimilarityStrategy exactStrategy = new ExactSimilarityStrategy();
        StrategyMatchResult exactResult = exactStrategy.analyze(target, List.of(source), SimilarityOptions.standard());
        assertEquals(0.0, exactResult.coverageRatio(), 1e-4, "Exact shingle matching should find 0% verbatim overlap");

        // 2. Verify Semantic Analyzer detects the conceptual paraphrase
        SemanticAnalysisResult semanticResult = analyzer.analyze(target, List.of(source), new SimilarityOptions(8, true, 0.40, 50, java.util.Map.of()));

        assertFalse(semanticResult.matches().isEmpty(), "Semantic analyzer must detect conceptual paraphrase");
        assertTrue(semanticResult.maxSimilarity() > 0.45, "Max similarity should reflect semantic relatedness");
        assertTrue(semanticResult.coverageRatio() > 0.5, "Coverage ratio should cover paraphrased passage");

        SemanticMatch topMatch = semanticResult.matches().get(0);
        assertEquals("src-ref", topMatch.sourceId());
        assertTrue(topMatch.score() > 0.45);
        assertNotNull(topMatch.matchType());
    }

    @Test
    void semanticCoordinatesPreserveExactCharacterOffsets() {
        String targetText = "Section 1: Transformers revolutionized natural language processing and neural machine translation.";
        String sourceText = "Background: Transformers established unprecedented benchmarks in machine translation and NLP.";

        SimilarityTarget target = SimilarityTarget.of("t-doc", "T Title", targetText);
        SimilaritySource source = SimilaritySource.of("s-doc", "S Title", sourceText, "REFERENCE");

        SemanticAnalysisResult result = analyzer.analyze(target, List.of(source), new SimilarityOptions(8, true, 0.40, 50, java.util.Map.of()));

        assertFalse(result.matches().isEmpty());

        for (SemanticMatch m : result.matches()) {
            assertEquals(targetText.substring(m.targetStart(), m.targetEnd()), m.targetText(),
                    "Target substring must match exact character slice");
            assertEquals(sourceText.substring(m.sourceStart(), m.sourceEnd()), m.sourceText(),
                    "Source substring must match exact character slice");
        }

        for (SimilaritySpan span : result.similaritySpans()) {
            assertEquals("SEMANTIC", span.method());
            assertEquals("s-doc", span.sourceId());
            assertEquals(targetText.substring(span.targetStart(), span.targetEnd()), span.targetPassage());
            assertEquals(sourceText.substring(span.sourceStart(), span.sourceEnd()), span.sourcePassage());
        }
    }

    @Test
    void highlightsContainFormattedConfidenceAndLabels() {
        String targetText = "Deep neural representations capture complex hierarchical patterns in unstructured textual data.";
        String sourceText = "Hierarchical representations in neural models extract rich latent features from textual corpora.";

        SimilarityTarget target = SimilarityTarget.of("t-doc", "T", targetText);
        SimilaritySource source = SimilaritySource.of("s-doc", "S", sourceText, "REFERENCE");

        SemanticAnalysisResult result = analyzer.analyze(target, List.of(source), new SimilarityOptions(8, true, 0.40, 50, java.util.Map.of()));

        assertFalse(result.highlights().isEmpty());
        SemanticHighlight hl = result.highlights().get(0);
        assertEquals(0, hl.startOffset());
        assertEquals(targetText.length(), hl.endOffset());
        assertEquals("s-doc", hl.sourceId());
        assertTrue(hl.label().startsWith("Semantic Paraphrase ("), "Label must format semantic paraphrase confidence");
        assertTrue(hl.confidence() > 0.40);
    }

    @Test
    void semanticStrategyIntegratesWithAnalyzer() {
        SemanticSimilarityStrategy strategy = new SemanticSimilarityStrategy(analyzer);

        assertTrue(strategy.isAvailable());
        assertEquals(SimilarityStrategyType.SEMANTIC, strategy.getType());
        assertEquals("semantic-embeddings", strategy.getName());

        String targetText = "Reinforcement learning agents optimize policy gradients through environmental interaction.";
        String sourceText = "Policy gradient optimization in reinforcement learning guides autonomous agent decisions.";

        SimilarityTarget target = SimilarityTarget.of("target-rl", "RL Agent", targetText);
        SimilaritySource source = SimilaritySource.of("src-rl", "RL Survey", sourceText, "REFERENCE");

        StrategyMatchResult result = strategy.analyze(target, List.of(source), new SimilarityOptions(8, true, 0.40, 50, java.util.Map.of()));

        assertEquals(SimilarityStrategyType.SEMANTIC, result.strategyType());
        assertEquals("semantic-embeddings", result.strategyName());
        assertFalse(result.spans().isEmpty());
        assertTrue(result.coverageRatio() > 0.0);
        assertEquals("SUCCESS", result.telemetry().get("status"));
        assertEquals("deterministic-minilm-384", result.telemetry().get("model"));
    }
}
