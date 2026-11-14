package com.writingassistant.similarity.hybrid;

import com.writingassistant.similarity.CompositeSimilarityEngine;
import com.writingassistant.similarity.ExactSimilarityStrategy;
import com.writingassistant.similarity.NearDuplicateSimilarityStrategy;
import com.writingassistant.similarity.SemanticSimilarityStrategy;
import com.writingassistant.similarity.SimilarityOptions;
import com.writingassistant.similarity.SimilaritySource;
import com.writingassistant.similarity.SimilarityStrategyRegistry;
import com.writingassistant.similarity.SimilarityTarget;
import com.writingassistant.similarity.onnx.DeterministicEmbeddingEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HybridEvidenceAggregationTest {

    private DeterministicEmbeddingEngine engine;
    private CompositeSimilarityEngine similarityEngine;
    private HybridEvidenceAggregator aggregator;

    @BeforeEach
    void setUp() {
        engine = new DeterministicEmbeddingEngine();

        SimilarityStrategyRegistry registry = new SimilarityStrategyRegistry();
        registry.register(new ExactSimilarityStrategy());
        registry.register(new NearDuplicateSimilarityStrategy());
        registry.register(new SemanticSimilarityStrategy(engine));

        similarityEngine = new CompositeSimilarityEngine(registry);
        aggregator = new HybridEvidenceAggregator(similarityEngine);
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    void aggregatorReportsDecomposedMetricsIndependentlyWithoutDoubleCounting() {
        String targetText = "Deep learning architectures have revolutionized automated medical imaging. "
                + "The quick brown fox jumps over the lazy dog in the warm summer afternoon. "
                + "\"Properly quoted scientific evidence demonstrates high reproducibility across trials\" (Smith et al., 2021). "
                + "Unattributed verbatim text directly reproduced from an external source without quotation marks or citation.";

        String sourceTextA = "The quick brown fox jumps over the lazy dog in the warm summer afternoon. "
                + "Properly quoted scientific evidence demonstrates high reproducibility across trials. "
                + "Unattributed verbatim text directly reproduced from an external source without quotation marks or citation.";

        String sourceTextB = "Convolutional neural network models demonstrate superhuman efficacy in diagnostic radiology imaging.";

        SimilarityTarget target = SimilarityTarget.of("doc-hybrid", "Hybrid Target", targetText);
        SimilaritySource sourceA = SimilaritySource.of("src-exact", "Exact Literature", sourceTextA, "REFERENCE");
        SimilaritySource sourceB = SimilaritySource.of("src-semantic", "Semantic Literature", sourceTextB, "SCHOLARLY");

        HybridSimilarityReport report = aggregator.aggregate(
                target,
                List.of(sourceA, sourceB),
                new SimilarityOptions(8, true, 0.45, 50, java.util.Map.of())
        );

        assertNotNull(report);
        DecomposedOverlapMetrics m = report.metrics();

        // 1. All decomposed metrics must be strictly within [0.0, 1.0]
        assertTrue(m.exactCoverageRatio() >= 0.0 && m.exactCoverageRatio() <= 1.0);
        assertTrue(m.nearDuplicateCoverageRatio() >= 0.0 && m.nearDuplicateCoverageRatio() <= 1.0);
        assertTrue(m.semanticCoverageRatio() >= 0.0 && m.semanticCoverageRatio() <= 1.0);
        assertTrue(m.quotedCoverageRatio() >= 0.0 && m.quotedCoverageRatio() <= 1.0);
        assertTrue(m.totalUnionRatio() >= 0.0 && m.totalUnionRatio() <= 1.0);
        assertTrue(m.netUnattributedRatio() >= 0.0 && m.netUnattributedRatio() <= 1.0);

        // 2. Total union must never exceed sum of parts and must prevent double-counting
        assertTrue(m.totalUnionRatio() <= m.exactCoverageRatio() + m.nearDuplicateCoverageRatio() + m.semanticCoverageRatio() + 1e-6,
                "Total union must be geometric union without double-counting");

        // 3. Quoted coverage should be positive due to "Properly quoted..."
        assertTrue(m.quotedCoverageRatio() > 0.05, "Quoted coverage must detect quotation marks");

        // 4. Net unattributed ratio must be strictly less than or equal to total union ratio
        assertTrue(m.netUnattributedRatio() <= m.totalUnionRatio() + 1e-6,
                "Net unattributed ratio must exclude properly quoted text");
    }

    @Test
    void aggregatorClassifiesAttributedSpanCategoriesCorrectly() {
        String targetText = "As stated in the literature: \"Exact sentence in quotation marks\" (Author, 2020).\n\n"
                + "Here is a completely separate discussion of unrelated topics that contains a long section of text.\n\n"
                + "Unattributed verbatim text directly copied from reference without any citation or quotation marks.";

        String sourceText = "Exact sentence in quotation marks. "
                + "Unattributed verbatim text directly copied from reference without any citation or quotation marks.";

        SimilarityTarget target = SimilarityTarget.of("doc-attr", "Attribution Test", targetText);
        SimilaritySource source = SimilaritySource.of("src-attr", "Source Doc", sourceText, "REFERENCE");

        HybridSimilarityReport report = aggregator.aggregate(
                target,
                List.of(source),
                SimilarityOptions.standard()
        );

        List<AttributedSpan> spans = report.attributedSpans();
        assertFalse(spans.isEmpty(), "Should produce attributed spans");

        boolean foundQuotedAndCited = false;
        boolean foundUnattributedExact = false;

        for (AttributedSpan span : spans) {
            if (span.attributionCategory().equals(AttributedSpan.CATEGORY_QUOTED_AND_CITED)) {
                foundQuotedAndCited = true;
                assertTrue(span.isQuoted());
                assertTrue(span.isCited());
            } else if (span.attributionCategory().equals(AttributedSpan.CATEGORY_UNATTRIBUTED_EXACT)) {
                foundUnattributedExact = true;
                assertFalse(span.isQuoted());
            }
        }

        assertTrue(foundUnattributedExact, "Must detect unattributed exact overlap");
    }

    @Test
    void aggregatorGeneratesPerSourceContributionsAndDisclaimers() {
        String targetText = "The quick brown fox jumps over the lazy dog in the warm summer afternoon.";
        String sourceText = "The quick brown fox jumps over the lazy dog in the warm summer afternoon.";

        SimilarityTarget target = SimilarityTarget.of("doc-src", "Source Test", targetText);
        SimilaritySource source = SimilaritySource.of("src-single", "Unique Source", sourceText, "REFERENCE");

        HybridSimilarityReport report = aggregator.aggregate(
                target,
                List.of(source),
                SimilarityOptions.standard()
        );

        // Per-source contributions
        assertEquals(1, report.sourceContributions().size());
        SourceContribution contrib = report.sourceContributions().get(0);
        assertEquals("src-single", contrib.sourceId());
        assertEquals("Unique Source", contrib.sourceTitle());
        assertTrue(contrib.totalUnionRatio() > 0.5);

        // Transparency disclaimers
        assertNotNull(report.academicTransparencyDisclaimer());
        assertTrue(report.academicTransparencyDisclaimer().contains("does NOT constitute a finding of plagiarism"));
        assertTrue(report.academicTransparencyDisclaimer().contains("qualitative review"));

        // Methodology notices
        assertFalse(report.methodologyNotices().isEmpty());
        assertTrue(report.methodologyNotices().stream().anyMatch(n -> n.contains("universal plagiarism")));
    }
}
