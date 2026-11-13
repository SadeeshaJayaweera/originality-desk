package com.writingassistant.similarity.onnx;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic, offline sentence embedding engine generating reproducible 384-dimensional
 * unit vectors using multi-scale token feature hashing and L2 normalization.
 * Provides zero-dependency, instant semantic vector representations.
 */
public class DeterministicEmbeddingEngine implements EmbeddingEngine {

    public static final String DEFAULT_MODEL_NAME = "deterministic-minilm-384";
    public static final int DEFAULT_DIMENSIONS = 384;

    private final String modelName;
    private final int dimensions;
    private final WordPieceTokenizer tokenizer;
    private volatile boolean closed = false;

    public DeterministicEmbeddingEngine() {
        this(DEFAULT_MODEL_NAME, DEFAULT_DIMENSIONS);
    }

    public DeterministicEmbeddingEngine(String modelName, int dimensions) {
        this.modelName = Objects.requireNonNull(modelName, "modelName must not be null");
        this.dimensions = dimensions > 0 ? dimensions : DEFAULT_DIMENSIONS;
        this.tokenizer = new WordPieceTokenizer();
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
        return !closed;
    }

    @Override
    public float[] embed(String text) {
        if (closed) throw new IllegalStateException("Embedding engine is closed");
        if (text == null || text.isBlank()) {
            return new float[dimensions];
        }

        float[] vector = new float[dimensions];
        WordPieceTokenizer.TokenizedInput tokenized = tokenizer.encode(text, 128);

        List<String> tokens = tokenized.tokens();
        long prevHash = 0;
        long prev2Hash = 0;

        for (String token : tokens) {
            if (token == null || token.equals(WordPieceTokenizer.CLS_TOKEN)
                    || token.equals(WordPieceTokenizer.SEP_TOKEN)
                    || token.equals(WordPieceTokenizer.PAD_TOKEN)) {
                continue;
            }

            long tokenHash = hashString(token);

            // 1-gram token projection
            int idx1 = Math.abs((int) (hash64(tokenHash, 1) % dimensions));
            vector[idx1] += 2.0f;

            // Bigram transition projection
            if (prevHash != 0) {
                long bigramHash = (prevHash << 16) ^ tokenHash;
                int idx2 = Math.abs((int) (hash64(bigramHash, 2) % dimensions));
                vector[idx2] += 1.5f;
            }

            // Trigram context projection
            if (prev2Hash != 0) {
                long trigramHash = (prev2Hash << 32) ^ (prevHash << 16) ^ tokenHash;
                int idx3 = Math.abs((int) (hash64(trigramHash, 3) % dimensions));
                vector[idx3] += 1.0f;
            }

            // Character 3-gram sub-word morphological projections (fastText approach)
            String cleanToken = token.startsWith("##") ? token.substring(2) : token;
            if (cleanToken.length() >= 3) {
                for (int c = 0; c <= cleanToken.length() - 3; c++) {
                    String sub = cleanToken.substring(c, c + 3);
                    int charHash = Math.abs((int) (hash64(hashString(sub), 4) % dimensions));
                    vector[charHash] += 0.8f;
                }
            }

            prev2Hash = prevHash;
            prevHash = tokenHash;
        }

        // Apply L2 unit normalization
        return TensorPooling.l2Normalize(vector);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null) return List.of();
        List<float[]> results = new ArrayList<>(texts.size());
        for (String t : texts) {
            results.add(embed(t));
        }
        return results;
    }

    @Override
    public double similarity(float[] vectorA, float[] vectorB) {
        return TensorPooling.cosineSimilarity(vectorA, vectorB);
    }

    @Override
    public void close() {
        closed = true;
    }

    private static long hashString(String s) {
        long h = 1125899906842597L;
        for (int i = 0; i < s.length(); i++) {
            h = 31 * h + s.charAt(i);
        }
        return h;
    }

    private static long hash64(long key, int seed) {
        long h = key ^ (seed * 0x517cc1b727220a95L);
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }
}
