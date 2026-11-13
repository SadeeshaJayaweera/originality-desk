package com.writingassistant.similarity.onnx;

import java.util.List;

/**
 * Common contract for deterministic local sentence embedding engines.
 */
public interface EmbeddingEngine extends AutoCloseable {

    /**
     * Unique identifier of the embedding model (e.g. "all-MiniLM-L6-v2").
     */
    String getModelName();

    /**
     * Dimensionality of output vectors (e.g. 384 for MiniLM, 768 for E5).
     */
    int getDimensions();

    /**
     * Whether the engine is initialized and ready to compute inference embeddings.
     */
    boolean isAvailable();

    /**
     * Encodes a single text passage into an L2-normalized dense embedding vector.
     */
    float[] embed(String text);

    /**
     * Encodes a batch of text passages into corresponding L2-normalized embedding vectors.
     */
    List<float[]> embedBatch(List<String> texts);

    /**
     * Computes cosine similarity between two embedding vectors.
     */
    double similarity(float[] vectorA, float[] vectorB);

    @Override
    void close();
}
