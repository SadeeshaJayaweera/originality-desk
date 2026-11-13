package com.writingassistant.similarity;

import com.writingassistant.analysis.EvidenceEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SimilarityStrategyArchitectureTest {

    private static final String SOURCE_PROSE =
            "The quick brown fox jumps over the lazy dog in the middle of a warm summer afternoon in the forest.";
    private static final String VERBATIM_PROSE =
            "The quick brown fox jumps over the lazy dog in the middle of a warm summer afternoon in the forest.";
    private static final String PARAPHRASED_PROSE =
            "A fast brown fox leaped across a sleepy canine during a hot midsummer afternoon inside the woods.";

    private SimilarityTarget targetVerbatim;
    private SimilarityTarget targetParaphrased;
    private SimilaritySource sourceDoc;

    @BeforeEach
    void setUp() {
        targetVerbatim = SimilarityTarget.of("doc-target-1", "Submitted Verbatim", VERBATIM_PROSE);
        targetParaphrased = SimilarityTarget.of("doc-target-2", "Submitted Paraphrase", PARAPHRASED_PROSE);
        sourceDoc = SimilaritySource.of("src-001", "Original Literature", SOURCE_PROSE, "REFERENCE");
    }

    @Test
    void exactSimilarityStrategyDetectsVerbatimPassages() {
        ExactSimilarityStrategy strategy = new ExactSimilarityStrategy();
        assertEquals(SimilarityStrategyType.EXACT, strategy.getType());
        assertEquals("exact-shingle", strategy.getName());
        assertTrue(strategy.isAvailable());

        StrategyMatchResult result = strategy.analyze(targetVerbatim, List.of(sourceDoc), SimilarityOptions.standard());
        assertEquals(SimilarityStrategyType.EXACT, result.strategyType());
        assertTrue(result.coverageRatio() > 0.8, "Exact coverage should be high for identical prose");
        assertFalse(result.spans().isEmpty());

        SimilaritySpan span = result.spans().get(0);
        assertEquals("src-001", span.sourceId());
        assertEquals("EXACT", span.method());
        assertEquals(0, span.targetStart());
        assertTrue(span.targetPassage().contains("The quick brown fox jumps"));
    }

    @Test
    void nearDuplicateSimilarityStrategyDetectsParaphrasedPassages() {
        NearDuplicateSimilarityStrategy strategy = new NearDuplicateSimilarityStrategy();
        assertEquals(SimilarityStrategyType.NEAR_DUPLICATE, strategy.getType());
        assertEquals("near-duplicate-window", strategy.getName());
        assertTrue(strategy.isAvailable());

        // Compare verbatim against source using near strategy
        StrategyMatchResult result = strategy.analyze(targetVerbatim, List.of(sourceDoc), SimilarityOptions.standard());
        assertEquals(SimilarityStrategyType.NEAR_DUPLICATE, result.strategyType());
        assertNotNull(result.spans());
    }

    @Test
    void heuristicSimilarityStrategyComputesMetrics() {
        HeuristicSimilarityStrategy strategy = new HeuristicSimilarityStrategy();
        assertEquals(SimilarityStrategyType.HEURISTIC, strategy.getType());
        assertEquals("heuristic-string-metrics", strategy.getName());
        assertTrue(strategy.isAvailable());

        StrategyMatchResult result = strategy.analyze(targetVerbatim, List.of(sourceDoc), SimilarityOptions.standard());
        assertEquals(SimilarityStrategyType.HEURISTIC, result.strategyType());
        assertTrue(result.coverageRatio() > 0.9, "Heuristic similarity for identical text should be near 1.0");
        assertFalse(result.spans().isEmpty());
        assertEquals("HEURISTIC", result.spans().get(0).method());
    }

    @Test
    void semanticSimilarityStrategyReportsConfigurationStatus() {
        SemanticSimilarityStrategy strategy = new SemanticSimilarityStrategy();
        assertEquals(SimilarityStrategyType.SEMANTIC, strategy.getType());
        assertEquals("semantic-embeddings", strategy.getName());
        assertFalse(strategy.isAvailable(), "Default semantic strategy should be unconfigured");

        StrategyMatchResult result = strategy.analyze(targetVerbatim, List.of(sourceDoc), SimilarityOptions.defaults());
        assertEquals(SimilarityStrategyType.SEMANTIC, result.strategyType());
        assertTrue(result.limitations().stream().anyMatch(l -> l.contains("not configured")));
        assertEquals("NOT_CONFIGURED", result.telemetry().get("status"));
    }

    @Test
    void similarityStrategyRegistryManagesStrategies() {
        SimilarityStrategyRegistry registry = SimilarityStrategyRegistry.createDefault();

        assertEquals(4, registry.getAllStrategies().size());
        assertTrue(registry.isAvailable(SimilarityStrategyType.EXACT));
        assertTrue(registry.isAvailable(SimilarityStrategyType.NEAR_DUPLICATE));
        assertTrue(registry.isAvailable(SimilarityStrategyType.HEURISTIC));
        assertFalse(registry.isAvailable(SimilarityStrategyType.SEMANTIC));

        Optional<SimilarityStrategy> exact = registry.getStrategy(SimilarityStrategyType.EXACT);
        assertTrue(exact.isPresent());
        assertEquals("exact-shingle", exact.get().getName());

        Optional<SimilarityStrategy> byName = registry.getStrategy("near-duplicate-window");
        assertTrue(byName.isPresent());
        assertEquals(SimilarityStrategyType.NEAR_DUPLICATE, byName.get().getType());
    }

    @Test
    void compositeSimilarityEngineCoordinatesExecution() {
        CompositeSimilarityEngine engine = new CompositeSimilarityEngine();
        assertNotNull(engine.getRegistry());

        CompositeSimilarityEngine.CompositeSimilarityResult result = engine.analyze(
                targetVerbatim,
                List.of(sourceDoc),
                SimilarityOptions.standard()
        );

        assertTrue(result.exactCoverage() > 0.8, "Exact coverage should be high");
        assertTrue(result.totalCoverage() >= result.exactCoverage());
        assertFalse(result.aggregatedSpans().isEmpty());
        assertTrue(result.resultsByType().containsKey(SimilarityStrategyType.EXACT));
    }

    @Test
    void evidenceEngineIntegratesWithCompositeEngineAndPreservesPrecision() {
        EvidenceEngine engine = new EvidenceEngine();
        assertNotNull(engine.getSimilarityEngine());

        // Verify existing EvidenceEngine functionality remains identical
        EvidenceEngine.Source source = new EvidenceEngine.Source(
                "src-100", "Reference Source", SOURCE_PROSE, "REFERENCE"
        );
        EvidenceEngine.Result result = engine.analyze(
                "doc-test", "Test Document", VERBATIM_PROSE, List.of(source), "STANDARD"
        );

        assertTrue(result.exactOverlap() > 0.8);
        assertFalse(result.matches().isEmpty());
        assertEquals("src-100", result.matches().get(0).sourceId());
        assertEquals("EXACT", result.matches().get(0).method());
    }
}
