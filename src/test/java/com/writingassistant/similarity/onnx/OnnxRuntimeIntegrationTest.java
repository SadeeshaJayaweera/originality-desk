package com.writingassistant.similarity.onnx;

import com.writingassistant.similarity.SimilarityOptions;
import com.writingassistant.similarity.SimilaritySource;
import com.writingassistant.similarity.SimilaritySpan;
import com.writingassistant.similarity.SimilarityStrategyType;
import com.writingassistant.similarity.SimilarityTarget;
import com.writingassistant.similarity.StrategyMatchResult;
import com.writingassistant.similarity.SemanticSimilarityStrategy;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OnnxRuntimeIntegrationTest {

    @Test
    void tensorPoolingMeanPoolComputesMaskWeightedAverage() {
        // 3 tokens, 2 dimensions
        float[][] tokenEmbeddings = new float[][]{
                {2.0f, 4.0f}, // token 1 (active)
                {4.0f, 6.0f}, // token 2 (active)
                {100.0f, 100.0f} // token 3 (padding, mask = 0)
        };
        long[] attentionMask = new long[]{1L, 1L, 0L};

        float[] pooled = TensorPooling.meanPool(tokenEmbeddings, attentionMask);
        assertEquals(2, pooled.length);
        // (2.0 + 4.0) / 2 = 3.0
        assertEquals(3.0f, pooled[0], 1e-5f);
        // (4.0 + 6.0) / 2 = 5.0
        assertEquals(5.0f, pooled[1], 1e-5f);
    }

    @Test
    void tensorPoolingL2NormalizesToUnitLength() {
        float[] vector = new float[]{3.0f, 4.0f}; // magnitude = 5.0
        float[] normalized = TensorPooling.l2Normalize(vector);

        assertEquals(2, normalized.length);
        assertEquals(0.6f, normalized[0], 1e-5f);
        assertEquals(0.8f, normalized[1], 1e-5f);

        float sumSquares = normalized[0] * normalized[0] + normalized[1] * normalized[1];
        assertEquals(1.0f, sumSquares, 1e-5f, "L2 normalized vector must have Euclidean length 1.0");
    }

    @Test
    void tensorPoolingL2HandlesZeroVectorGracefully() {
        float[] zero = new float[]{0.0f, 0.0f, 0.0f};
        float[] normalized = TensorPooling.l2Normalize(zero);
        assertNotNull(normalized);
        assertEquals(3, normalized.length);
        for (float v : normalized) {
            assertEquals(0.0f, v, 1e-6f);
            assertFalse(Float.isNaN(v));
        }
    }

    @Test
    void tensorPoolingCosineSimilarityCalculatesExpectedValues() {
        float[] vecA = new float[]{1.0f, 0.0f};
        float[] vecB = new float[]{1.0f, 0.0f};
        float[] vecOrthogonal = new float[]{0.0f, 1.0f};
        float[] vecOpposite = new float[]{-1.0f, 0.0f};

        // Identical
        assertEquals(1.0, TensorPooling.cosineSimilarity(vecA, vecB), 1e-5);
        // Orthogonal
        assertEquals(0.0, TensorPooling.cosineSimilarity(vecA, vecOrthogonal), 1e-5);
        // Opposite
        assertEquals(-1.0, TensorPooling.cosineSimilarity(vecA, vecOpposite), 1e-5);
    }

    @Test
    void wordPieceTokenizerEncodesTextWithSpecialTokens() {
        WordPieceTokenizer tokenizer = new WordPieceTokenizer();
        String text = "Originality analysis for academic papers";
        WordPieceTokenizer.TokenizedInput tokenized = tokenizer.encode(text, 16);

        assertNotNull(tokenized);
        assertEquals(16, tokenized.length());
        assertEquals(16, tokenized.attentionMask().length);
        assertEquals(16, tokenized.tokenTypeIds().length);

        // First token must be [CLS] (ID 101)
        assertEquals(WordPieceTokenizer.CLS_ID, tokenized.inputIds()[0]);
        assertEquals(1L, tokenized.attentionMask()[0]);

        // There should be active tokens followed by [SEP] and then padding (0)
        long[] mask = tokenized.attentionMask();
        long[] inputIds = tokenized.inputIds();

        boolean foundSep = false;
        for (int i = 0; i < mask.length; i++) {
            if (inputIds[i] == WordPieceTokenizer.SEP_ID) {
                foundSep = true;
                assertEquals(1L, mask[i]);
            } else if (foundSep) {
                assertEquals(WordPieceTokenizer.PAD_ID, inputIds[i]);
                assertEquals(0L, mask[i]);
            }
        }
        assertTrue(foundSep, "Must contain [SEP] token at the end of text sequence");
    }

    @Test
    void deterministicEmbeddingEngineProducesNormalizedReproducibleVectors() {
        try (DeterministicEmbeddingEngine engine = new DeterministicEmbeddingEngine()) {
            assertEquals("deterministic-minilm-384", engine.getModelName());
            assertEquals(384, engine.getDimensions());
            assertTrue(engine.isAvailable());

            String textA = "Convolutional neural networks for automated image classification in medicine.";
            String textB = "Deep learning vision models applied to medical scan diagnostic imagery.";
            String textC = "Medieval European agricultural economic reforms during the fourteenth century.";

            float[] vecA1 = engine.embed(textA);
            float[] vecA2 = engine.embed(textA);
            float[] vecB = engine.embed(textB);
            float[] vecC = engine.embed(textC);

            assertEquals(384, vecA1.length);
            // Deterministic consistency
            assertArrayEquals(vecA1, vecA2, 1e-6f, "Identical input must yield bit-exact identical embeddings");

            // Vector unit length
            float sumSq = 0.0f;
            for (float v : vecA1) sumSq += v * v;
            assertEquals(1.0f, sumSq, 1e-4f, "Embedding vector must have L2 norm = 1.0");

            // Cosine similarity
            double simSelf = engine.similarity(vecA1, vecA2);
            double simRelated = engine.similarity(vecA1, vecB);
            double simUnrelated = engine.similarity(vecA1, vecC);

            assertEquals(1.0, simSelf, 1e-4, "Self similarity must be 1.0");
            assertTrue(simRelated > simUnrelated,
                    "Medical AI papers should have higher semantic similarity than 14th century agriculture: related="
                            + simRelated + ", unrelated=" + simUnrelated);
        }
    }

    @Test
    void semanticSimilarityStrategyExecutesWithDeterministicEngine() {
        DeterministicEmbeddingEngine engine = new DeterministicEmbeddingEngine();
        SemanticSimilarityStrategy strategy = new SemanticSimilarityStrategy(engine);

        assertTrue(strategy.isAvailable());
        assertEquals(SimilarityStrategyType.SEMANTIC, strategy.getType());
        assertEquals("semantic-embeddings", strategy.getName());

        SimilarityTarget target = SimilarityTarget.of(
                "target-doc",
                "Deep Learning Document",
                "Neural network methods for computer vision and image classification."
        );

        SimilaritySource relatedSource = SimilaritySource.of(
                "src-ai",
                "Machine Learning Survey",
                "Deep learning models for visual understanding and image analysis.",
                "REFERENCE"
        );

        SimilaritySource unrelatedSource = SimilaritySource.of(
                "src-history",
                "Ancient Civilizations",
                "Pottery and agriculture in Mesopotamia before the bronze age.",
                "REFERENCE"
        );

        StrategyMatchResult result = strategy.analyze(
                target,
                List.of(relatedSource, unrelatedSource),
                new SimilarityOptions(8, true, 0.4, 20, Map.of())
        );

        assertNotNull(result);
        assertEquals(SimilarityStrategyType.SEMANTIC, result.strategyType());
        assertTrue(result.coverageRatio() > 0.0);
        assertEquals("SUCCESS", result.telemetry().get("status"));
        assertEquals("deterministic-minilm-384", result.telemetry().get("model"));
        assertFalse(result.spans().isEmpty());

        SimilaritySpan topSpan = result.spans().get(0);
        assertEquals("src-ai", topSpan.sourceId());
        assertEquals("SEMANTIC", topSpan.method());
    }

    @Test
    void onnxEmbeddingEngineValidatesMissingModelFile() {
        Path nonExistent = Path.of("target/non-existent-model.onnx");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new OnnxEmbeddingEngine(nonExistent)
        );
        assertTrue(ex.getMessage().contains("does not exist"));
    }
}
