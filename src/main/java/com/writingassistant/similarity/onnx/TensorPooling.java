package com.writingassistant.similarity.onnx;

import java.util.Objects;

/**
 * Mathematical operations for transformer token tensor pooling, L2 vector normalization,
 * and vector cosine similarity.
 */
public final class TensorPooling {

    private TensorPooling() {}

    /**
     * Computes Mean Pooling over token embeddings weighted by the binary attention mask.
     *
     * @param tokenEmbeddings array of shape [sequenceLength][hiddenDim]
     * @param attentionMask   binary mask of length [sequenceLength] where 1 = active, 0 = padding
     * @return pooled vector of length [hiddenDim]
     */
    public static float[] meanPool(float[][] tokenEmbeddings, long[] attentionMask) {
        Objects.requireNonNull(tokenEmbeddings, "tokenEmbeddings must not be null");
        Objects.requireNonNull(attentionMask, "attentionMask must not be null");

        if (tokenEmbeddings.length == 0 || tokenEmbeddings[0].length == 0) {
            throw new IllegalArgumentException("tokenEmbeddings must have non-zero dimensions");
        }

        int seqLen = Math.min(tokenEmbeddings.length, attentionMask.length);
        int hiddenDim = tokenEmbeddings[0].length;
        float[] pooled = new float[hiddenDim];
        float sumMask = 0.0f;

        for (int i = 0; i < seqLen; i++) {
            if (attentionMask[i] > 0) {
                sumMask += 1.0f;
                float[] token = tokenEmbeddings[i];
                for (int d = 0; d < hiddenDim; d++) {
                    pooled[d] += token[d];
                }
            }
        }

        if (sumMask > 0.0f) {
            for (int d = 0; d < hiddenDim; d++) {
                pooled[d] /= sumMask;
            }
        }

        return pooled;
    }

    /**
     * Normalizes a vector to Euclidean unit length (L2 norm = 1.0).
     * After L2 normalization, cosine similarity is simply the inner dot product.
     *
     * @param vector input vector
     * @return L2-normalized unit vector
     */
    public static float[] l2Normalize(float[] vector) {
        Objects.requireNonNull(vector, "vector must not be null");
        float sumSquares = 0.0f;
        for (float v : vector) {
            sumSquares += v * v;
        }

        float norm = (float) Math.sqrt(sumSquares);
        float[] normalized = new float[vector.length];

        if (norm > 1e-12f) {
            for (int i = 0; i < vector.length; i++) {
                normalized[i] = vector[i] / norm;
            }
        }
        return normalized;
    }

    /**
     * Computes cosine similarity between two vectors.
     * If both vectors are already L2-normalized, returns the dot product.
     */
    public static double cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA == null || vectorB == null) return 0.0;
        int len = Math.min(vectorA.length, vectorB.length);
        if (len == 0) return 0.0;

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < len; i++) {
            dotProduct += (double) vectorA[i] * (double) vectorB[i];
            normA += (double) vectorA[i] * (double) vectorA[i];
            normB += (double) vectorB[i] * (double) vectorB[i];
        }

        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        if (denominator < 1e-12) return 0.0;

        double cosine = dotProduct / denominator;
        return Math.max(-1.0, Math.min(1.0, cosine));
    }
}
