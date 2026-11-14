package com.writingassistant.persistence.vector;

import com.writingassistant.similarity.chunking.EmbeddedChunk;

import java.util.List;

/**
 * Storage and similarity retrieval interface for dense document chunk embeddings.
 * Enforces strict tenant isolation across all storage and query operations.
 */
public interface VectorStore extends AutoCloseable {

    /**
     * Initializes database tables, indexes, and extensions if necessary.
     */
    void initializeSchema();

    /**
     * Stores a single chunk embedding under the specified tenant.
     */
    void storeEmbedding(String tenantId, EmbeddedChunk chunk);

    /**
     * Atomically stores a batch of chunk embeddings under the specified tenant.
     */
    void storeEmbeddings(String tenantId, List<EmbeddedChunk> chunks);

    /**
     * Searches for top-k similar chunks within the specified tenant using cosine similarity.
     *
     * @param tenantId    tenant isolation scope
     * @param queryVector L2-normalized dense embedding vector
     * @param limit       maximum number of results to return
     * @param minScore    minimum cosine similarity threshold [-1.0, 1.0]
     * @return ordered list of results sorted descending by cosine similarity
     */
    List<VectorSearchResult> searchSimilar(String tenantId, float[] queryVector, int limit, double minScore);

    /**
     * Deletes all chunk embeddings belonging to a specific document under a tenant.
     */
    void deleteDocumentEmbeddings(String tenantId, String documentId);

    /**
     * Deletes all chunk embeddings for an entire tenant (e.g. on workspace deletion).
     */
    void deleteTenantEmbeddings(String tenantId);

    /**
     * Returns the total count of stored embeddings for a tenant.
     */
    long countEmbeddings(String tenantId);

    /**
     * Whether the backing database connection is open and active.
     */
    boolean isAvailable();

    /**
     * Type identifier of the underlying persistence implementation (e.g. "POSTGRESQL_PGVECTOR", "SQLITE_WAL").
     */
    String getStorageType();

    @Override
    void close();
}
