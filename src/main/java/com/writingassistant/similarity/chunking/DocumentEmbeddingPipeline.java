package com.writingassistant.similarity.chunking;

import com.writingassistant.similarity.onnx.EmbeddingEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * End-to-end memory-bounded pipeline for chunking documents and generating dense vector embeddings.
 */
public class DocumentEmbeddingPipeline {

    public static final int DEFAULT_BATCH_SIZE = 16;

    private final EmbeddingEngine embeddingEngine;
    private final ChunkingStrategy chunkingStrategy;
    private final int batchSize;

    public DocumentEmbeddingPipeline(EmbeddingEngine embeddingEngine) {
        this(embeddingEngine, new SentenceAwareChunkingStrategy(), DEFAULT_BATCH_SIZE);
    }

    public DocumentEmbeddingPipeline(EmbeddingEngine embeddingEngine, ChunkingStrategy chunkingStrategy, int batchSize) {
        this.embeddingEngine = Objects.requireNonNull(embeddingEngine, "embeddingEngine must not be null");
        this.chunkingStrategy = chunkingStrategy != null ? chunkingStrategy : new SentenceAwareChunkingStrategy();
        this.batchSize = batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
    }

    public EmbeddingEngine getEmbeddingEngine() {
        return embeddingEngine;
    }

    public ChunkingStrategy getChunkingStrategy() {
        return chunkingStrategy;
    }

    public int getBatchSize() {
        return batchSize;
    }

    /**
     * Chunks a document and computes dense embeddings for all chunks in bounded batches.
     */
    public List<EmbeddedChunk> embedDocument(String documentId, String text, ChunkingOptions options) {
        Objects.requireNonNull(documentId, "documentId must not be null");
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        List<DocumentChunk> chunks = chunkingStrategy.chunk(documentId, text, options);
        return embedChunks(chunks);
    }

    /**
     * Embeds a list of pre-computed document chunks in memory-bounded batches.
     */
    public List<EmbeddedChunk> embedChunks(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptyList();
        }

        List<EmbeddedChunk> results = new ArrayList<>(chunks.size());
        String modelName = embeddingEngine.getModelName();
        int dimensions = embeddingEngine.getDimensions();

        // Process in bounded batches to limit peak working memory
        for (int i = 0; i < chunks.size(); i += batchSize) {
            int end = Math.min(chunks.size(), i + batchSize);
            List<DocumentChunk> batch = chunks.subList(i, end);

            List<String> texts = batch.stream().map(DocumentChunk::text).toList();
            List<float[]> embeddings = embeddingEngine.embedBatch(texts);

            for (int b = 0; b < batch.size(); b++) {
                results.add(new EmbeddedChunk(
                        batch.get(b),
                        embeddings.get(b),
                        modelName,
                        dimensions,
                        System.currentTimeMillis()
                ));
            }
        }

        return results;
    }

    /**
     * Compares target chunks against corpus chunks using cosine similarity,
     * filtering matches by minimum score threshold.
     */
    public List<ChunkMatch> findMatches(List<EmbeddedChunk> targetChunks, List<EmbeddedChunk> corpusChunks, double minThreshold) {
        if (targetChunks == null || targetChunks.isEmpty() || corpusChunks == null || corpusChunks.isEmpty()) {
            return Collections.emptyList();
        }

        List<ChunkMatch> matches = new ArrayList<>();
        for (EmbeddedChunk target : targetChunks) {
            for (EmbeddedChunk source : corpusChunks) {
                double score = target.cosineSimilarity(source);
                if (score >= minThreshold) {
                    matches.add(new ChunkMatch(target, source, score));
                }
            }
        }

        Collections.sort(matches);
        return matches;
    }
}
