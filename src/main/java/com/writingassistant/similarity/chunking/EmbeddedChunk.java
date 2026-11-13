package com.writingassistant.similarity.chunking;

import com.writingassistant.similarity.onnx.TensorPooling;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/**
 * A document chunk paired with its L2-normalized dense semantic vector embedding.
 */
public record EmbeddedChunk(
        DocumentChunk chunk,
        float[] embedding,
        String modelName,
        int dimensions,
        long generatedAtMillis
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public EmbeddedChunk {
        Objects.requireNonNull(chunk, "chunk must not be null");
        Objects.requireNonNull(embedding, "embedding must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");
        if (dimensions <= 0 || embedding.length != dimensions) {
            throw new IllegalArgumentException("Embedding dimension mismatch: expected " + dimensions + ", got " + embedding.length);
        }
    }

    public static EmbeddedChunk of(DocumentChunk chunk, float[] embedding, String modelName) {
        return new EmbeddedChunk(
                chunk,
                embedding,
                modelName,
                embedding.length,
                System.currentTimeMillis()
        );
    }

    public String chunkId() {
        return chunk.chunkId();
    }

    public String documentId() {
        return chunk.documentId();
    }

    public int startOffset() {
        return chunk.startOffset();
    }

    public int endOffset() {
        return chunk.endOffset();
    }

    public String text() {
        return chunk.text();
    }

    public double cosineSimilarity(EmbeddedChunk other) {
        if (other == null) return 0.0;
        return TensorPooling.cosineSimilarity(this.embedding, other.embedding);
    }

    public double cosineSimilarity(float[] queryVector) {
        if (queryVector == null) return 0.0;
        return TensorPooling.cosineSimilarity(this.embedding, queryVector);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmbeddedChunk that = (EmbeddedChunk) o;
        return dimensions == that.dimensions
                && Objects.equals(chunk, that.chunk)
                && Arrays.equals(embedding, that.embedding)
                && Objects.equals(modelName, that.modelName);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(chunk, modelName, dimensions);
        result = 31 * result + Arrays.hashCode(embedding);
        return result;
    }
}
