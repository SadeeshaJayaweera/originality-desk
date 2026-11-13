package com.writingassistant.similarity.embedding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddingModelSelectionTest {

    private EmbeddingModelSelector selector;

    @BeforeEach
    void setUp() {
        selector = new EmbeddingModelSelector();
    }

    @Test
    void candidateModelsMetadataValidation() {
        EmbeddingModelSpec miniLm = EmbeddingModelSpec.allMiniLmL6V2();
        assertEquals("sentence-transformers/all-MiniLM-L6-v2", miniLm.modelId());
        assertEquals(384, miniLm.dimensions());
        assertEquals(256, miniLm.maxSequenceLength());
        assertEquals(22.7, miniLm.parameterCountMillions(), 0.1);
        assertEquals("Apache-2.0", miniLm.license());
        assertFalse(miniLm.isMultilingual());
        assertTrue(miniLm.averageCpuLatencyMs() < 20.0);
        assertTrue(miniLm.int8SizeBytes() < 30_000_000L);

        EmbeddingModelSpec e5 = EmbeddingModelSpec.multilingualE5Base();
        assertEquals(768, e5.dimensions());
        assertEquals("MIT", e5.license());
        assertTrue(e5.isMultilingual());

        EmbeddingModelSpec bge = EmbeddingModelSpec.bgeM3();
        assertEquals(1024, bge.dimensions());
        assertEquals(8192, bge.maxSequenceLength());
        assertEquals("MIT", bge.license());
        assertTrue(bge.isMultilingual());
    }

    @Test
    void academicBenchmarkDatasetIntegrity() {
        List<AcademicBenchmarkDataset.BenchmarkPair> pairs = AcademicBenchmarkDataset.standardCorpus();
        assertFalse(pairs.isEmpty());

        Set<String> ids = pairs.stream().map(AcademicBenchmarkDataset.BenchmarkPair::id).collect(Collectors.toSet());
        assertEquals(pairs.size(), ids.size(), "Benchmark pair IDs must be unique");

        for (AcademicBenchmarkDataset.BenchmarkPair pair : pairs) {
            assertNotNull(pair.textA());
            assertNotNull(pair.textB());
            assertNotNull(pair.pairType());
            assertTrue(pair.expectedSimilarity() >= 0.0 && pair.expectedSimilarity() <= 1.0);
            assertFalse(pair.domain().isBlank());
        }

        // Verify representative coverage across pair types
        Set<AcademicBenchmarkDataset.PairType> types = pairs.stream()
                .map(AcademicBenchmarkDataset.BenchmarkPair::pairType)
                .collect(Collectors.toSet());
        assertTrue(types.contains(AcademicBenchmarkDataset.PairType.VERBATIM));
        assertTrue(types.contains(AcademicBenchmarkDataset.PairType.LEXICAL_PARAPHRASE));
        assertTrue(types.contains(AcademicBenchmarkDataset.PairType.SEMANTIC_CONCEPTUAL));
        assertTrue(types.contains(AcademicBenchmarkDataset.PairType.CROSS_DOMAIN_NEGATIVE));
        assertTrue(types.contains(AcademicBenchmarkDataset.PairType.ADVERSARIAL_CONTRADICTION));
    }

    @Test
    void modelSelectorChoosesAllMiniLmForLocalDesktopEnvironment() {
        EmbeddingModelSelector.OperationalConstraints desktop =
                EmbeddingModelSelector.OperationalConstraints.localDesktopDefault();

        EmbeddingModelSpec selected = selector.selectOptimalModel(desktop);
        assertEquals("sentence-transformers/all-MiniLM-L6-v2", selected.modelId());
        assertEquals(384, selected.dimensions());

        List<EmbeddingModelSelector.ScoredCandidate> evaluations = selector.evaluate(desktop);
        EmbeddingModelSelector.ScoredCandidate top = evaluations.get(0);
        assertEquals("sentence-transformers/all-MiniLM-L6-v2", top.spec().modelId());
        assertTrue(top.satisfiesConstraints());
    }

    @Test
    void modelSelectorChoosesMultilingualE5ForMultilingualServer() {
        EmbeddingModelSelector.OperationalConstraints multilingual =
                EmbeddingModelSelector.OperationalConstraints.multilingualServer();

        EmbeddingModelSpec selected = selector.selectOptimalModel(multilingual);
        assertEquals("intfloat/multilingual-e5-base", selected.modelId());
        assertTrue(selected.isMultilingual());
        assertEquals(768, selected.dimensions());
    }

    @Test
    void modelSelectorChoosesBgeM3ForHighPerformanceServer() {
        EmbeddingModelSelector.OperationalConstraints highPerf =
                EmbeddingModelSelector.OperationalConstraints.highPerformanceServer();

        EmbeddingModelSpec selected = selector.selectOptimalModel(highPerf);
        assertEquals("BAAI/bge-m3", selected.modelId());
        assertEquals(8192, selected.maxSequenceLength());
        assertEquals(1024, selected.dimensions());
    }

    @Test
    void licensingConformanceAudit() {
        for (EmbeddingModelSpec spec : selector.getCandidates()) {
            String license = spec.license();
            assertTrue(
                    license.equalsIgnoreCase("Apache-2.0") || license.equalsIgnoreCase("MIT"),
                    "Candidate model " + spec.modelId() + " must possess an approved permissive license (Apache-2.0 or MIT)"
            );
        }
    }
}
