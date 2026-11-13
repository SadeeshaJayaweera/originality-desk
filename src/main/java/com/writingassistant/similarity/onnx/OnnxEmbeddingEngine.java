package com.writingassistant.similarity.onnx;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * High-performance, offline deep sentence embedding engine powered by ONNX Runtime Java.
 * Executes transformer inference, WordPiece tokenization, Mean Pooling, and L2 normalization.
 */
public class OnnxEmbeddingEngine implements EmbeddingEngine {

    public static final String DEFAULT_MODEL_NAME = "sentence-transformers/all-MiniLM-L6-v2";
    public static final int DEFAULT_DIMENSIONS = 384;
    public static final int DEFAULT_MAX_SEQ_LENGTH = 256;

    private final String modelName;
    private final int dimensions;
    private final int maxSequenceLength;
    private final WordPieceTokenizer tokenizer;
    private final OrtEnvironment env;
    private final OrtSession session;
    private volatile boolean closed = false;

    public OnnxEmbeddingEngine(Path modelPath) throws OrtException {
        this(modelPath, DEFAULT_MODEL_NAME, DEFAULT_DIMENSIONS, DEFAULT_MAX_SEQ_LENGTH, new WordPieceTokenizer());
    }

    public OnnxEmbeddingEngine(Path modelPath, String modelName, int dimensions, int maxSeqLength,
                              WordPieceTokenizer tokenizer) throws OrtException {
        Objects.requireNonNull(modelPath, "modelPath must not be null");
        if (!Files.exists(modelPath)) {
            throw new IllegalArgumentException("Model file does not exist: " + modelPath);
        }

        this.modelName = modelName != null ? modelName : DEFAULT_MODEL_NAME;
        this.dimensions = dimensions > 0 ? dimensions : DEFAULT_DIMENSIONS;
        this.maxSequenceLength = maxSeqLength > 0 ? maxSeqLength : DEFAULT_MAX_SEQ_LENGTH;
        this.tokenizer = tokenizer != null ? tokenizer : new WordPieceTokenizer();

        this.env = OrtEnvironment.getEnvironment("OriginalityDesk-ONNX");
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        opts.setIntraOpNumThreads(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
        this.session = env.createSession(modelPath.toString(), opts);
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    @Override
    public int getDimensions() {
        return dimensions;
    }

    @Override
    public boolean isAvailable() {
        return !closed && session != null;
    }

    @Override
    public float[] embed(String text) {
        if (closed) throw new IllegalStateException("Embedding engine is closed");
        if (text == null || text.isBlank()) {
            return new float[dimensions];
        }

        WordPieceTokenizer.TokenizedInput tokenized = tokenizer.encode(text, maxSequenceLength);

        try {
            long[][] inputIds = new long[][]{tokenized.inputIds()};
            long[][] attentionMask = new long[][]{tokenized.attentionMask()};
            long[][] tokenTypeIds = new long[][]{tokenized.tokenTypeIds()};

            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", OnnxTensor.createTensor(env, inputIds));
            inputs.put("attention_mask", OnnxTensor.createTensor(env, attentionMask));
            inputs.put("token_type_ids", OnnxTensor.createTensor(env, tokenTypeIds));

            try (OrtSession.Result result = session.run(inputs)) {
                // Shape: [batchSize, seqLen, hiddenDim]
                float[][][] lastHiddenState = (float[][][]) result.get(0).getValue();
                float[][] tokenEmbeddings = lastHiddenState[0];

                float[] pooled = TensorPooling.meanPool(tokenEmbeddings, tokenized.attentionMask());
                return TensorPooling.l2Normalize(pooled);
            } finally {
                for (OnnxTensor tensor : inputs.values()) {
                    tensor.close();
                }
            }
        } catch (OrtException e) {
            throw new RuntimeException("ONNX embedding inference failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null) return List.of();
        List<float[]> results = new ArrayList<>(texts.size());
        for (String text : texts) {
            results.add(embed(text));
        }
        return results;
    }

    @Override
    public double similarity(float[] vectorA, float[] vectorB) {
        return TensorPooling.cosineSimilarity(vectorA, vectorB);
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            try {
                if (session != null) session.close();
            } catch (OrtException ignored) {
            } finally {
                if (env != null) env.close();
            }
        }
    }
}
