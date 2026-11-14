package com.writingassistant.persistence.vector;

import com.writingassistant.similarity.chunking.DocumentChunk;
import com.writingassistant.similarity.chunking.EmbeddedChunk;
import com.writingassistant.similarity.onnx.DeterministicEmbeddingEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VectorStoreIntegrationTest {

    private SqliteVectorStore sqliteStore;
    private DeterministicEmbeddingEngine engine;

    @BeforeEach
    void setUp() throws SQLException {
        sqliteStore = SqliteVectorStore.inMemory();
        engine = new DeterministicEmbeddingEngine();
    }

    @AfterEach
    void tearDown() {
        if (sqliteStore != null) {
            sqliteStore.close();
        }
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    void sqliteVectorStoreInitializesAndStoresEmbeddings() {
        assertTrue(sqliteStore.isAvailable());
        assertEquals("SQLITE_WAL", sqliteStore.getStorageType());

        DocumentChunk chunk1 = DocumentChunk.of("doc-1", 0, 0, 45, "Deep neural networks for image classification", 1);
        float[] vec1 = engine.embed(chunk1.text());
        EmbeddedChunk ec1 = EmbeddedChunk.of(chunk1, vec1, engine.getModelName());

        sqliteStore.storeEmbedding("tenant-alpha", ec1);
        assertEquals(1L, sqliteStore.countEmbeddings("tenant-alpha"));
        assertEquals(0L, sqliteStore.countEmbeddings("tenant-beta"));
    }

    @Test
    void vectorStoreEnforcesStrictTenantIsolation() {
        // Tenant A document
        DocumentChunk chunkA = DocumentChunk.of("doc-a", 0, 0, 50, "Proprietary medical research on cancer genetics", 1);
        float[] vecA = engine.embed(chunkA.text());
        EmbeddedChunk ecA = EmbeddedChunk.of(chunkA, vecA, engine.getModelName());
        sqliteStore.storeEmbedding("tenant-hospital-a", ecA);

        // Tenant B document
        DocumentChunk chunkB = DocumentChunk.of("doc-b", 0, 0, 50, "Proprietary medical research on cancer genetics", 1);
        float[] vecB = engine.embed(chunkB.text());
        EmbeddedChunk ecB = EmbeddedChunk.of(chunkB, vecB, engine.getModelName());
        sqliteStore.storeEmbedding("tenant-hospital-b", ecB);

        assertEquals(1L, sqliteStore.countEmbeddings("tenant-hospital-a"));
        assertEquals(1L, sqliteStore.countEmbeddings("tenant-hospital-b"));

        // Query with exact vector of doc-a under tenant-hospital-a
        List<VectorSearchResult> resultsA = sqliteStore.searchSimilar("tenant-hospital-a", vecA, 10, 0.5);
        assertEquals(1, resultsA.size());
        assertEquals("doc-a", resultsA.get(0).documentId());

        // Search under tenant-hospital-b should NEVER return tenant A's documents
        List<VectorSearchResult> resultsB = sqliteStore.searchSimilar("tenant-hospital-b", vecA, 10, 0.5);
        assertEquals(1, resultsB.size());
        assertEquals("doc-b", resultsB.get(0).documentId(), "Tenant B search must never see Tenant A document");

        // Search under non-existent tenant must return zero results
        List<VectorSearchResult> resultsC = sqliteStore.searchSimilar("tenant-isolated-c", vecA, 10, 0.1);
        assertTrue(resultsC.isEmpty(), "Unrelated tenant must have 0 search results");
    }

    @Test
    void searchSimilarRanksByCosineSimilarityDescending() {
        String baseText = "Transformers in natural language processing and translation.";
        String similarText = "Transformers revolutionized machine translation and natural language processing.";
        String unrelatedText = "Ancient bronze age pottery and agriculture in Mesopotamia.";

        DocumentChunk chkBase = DocumentChunk.of("doc-nlp", 0, 0, baseText.length(), baseText, 1);
        DocumentChunk chkSimilar = DocumentChunk.of("doc-nlp", 1, 0, similarText.length(), similarText, 1);
        DocumentChunk chkUnrelated = DocumentChunk.of("doc-history", 0, 0, unrelatedText.length(), unrelatedText, 1);

        EmbeddedChunk ecBase = EmbeddedChunk.of(chkBase, engine.embed(baseText), engine.getModelName());
        EmbeddedChunk ecSimilar = EmbeddedChunk.of(chkSimilar, engine.embed(similarText), engine.getModelName());
        EmbeddedChunk ecUnrelated = EmbeddedChunk.of(chkUnrelated, engine.embed(unrelatedText), engine.getModelName());

        sqliteStore.storeEmbeddings("tenant-rank", List.of(ecBase, ecSimilar, ecUnrelated));
        assertEquals(3L, sqliteStore.countEmbeddings("tenant-rank"));

        float[] queryVec = engine.embed(baseText);
        List<VectorSearchResult> results = sqliteStore.searchSimilar("tenant-rank", queryVec, 10, -1.0);

        assertEquals(3, results.size());
        // Top match should be self with score ~1.0
        assertEquals(chkBase.chunkId(), results.get(0).chunkId());
        assertEquals(1.0, results.get(0).score(), 1e-4);

        // Second match should be similar text
        assertEquals(chkSimilar.chunkId(), results.get(1).chunkId());
        assertTrue(results.get(1).score() > results.get(2).score(),
                "Similar NLP chunk should have higher score than ancient pottery chunk");

        // Verify descending order
        for (int i = 0; i < results.size() - 1; i++) {
            assertTrue(results.get(i).score() >= results.get(i + 1).score(), "Results must be sorted descending by score");
        }
    }

    @Test
    void deleteDocumentAndTenantEmbeddingsRemovesOnlyTargetedScope() {
        DocumentChunk chunk1 = DocumentChunk.of("doc-x", 0, 0, 20, "Sample document X chunk", 1);
        DocumentChunk chunk2 = DocumentChunk.of("doc-y", 0, 0, 20, "Sample document Y chunk", 1);

        EmbeddedChunk ec1 = EmbeddedChunk.of(chunk1, engine.embed(chunk1.text()), engine.getModelName());
        EmbeddedChunk ec2 = EmbeddedChunk.of(chunk2, engine.embed(chunk2.text()), engine.getModelName());

        sqliteStore.storeEmbeddings("tenant-del", List.of(ec1, ec2));
        assertEquals(2L, sqliteStore.countEmbeddings("tenant-del"));

        // Delete only doc-x
        sqliteStore.deleteDocumentEmbeddings("tenant-del", "doc-x");
        assertEquals(1L, sqliteStore.countEmbeddings("tenant-del"));

        // Confirm doc-y remains
        List<VectorSearchResult> remaining = sqliteStore.searchSimilar("tenant-del", ec2.embedding(), 5, 0.1);
        assertEquals(1, remaining.size());
        assertEquals("doc-y", remaining.get(0).documentId());

        // Delete entire tenant
        sqliteStore.deleteTenantEmbeddings("tenant-del");
        assertEquals(0L, sqliteStore.countEmbeddings("tenant-del"));
    }

    @Test
    void pgVectorStoreGeneratesValidDdlAndVectorStringFormat() {
        String ddl = PgVectorStore.getSchemaDdl(384);
        assertNotNull(ddl);
        assertTrue(ddl.contains("CREATE EXTENSION IF NOT EXISTS vector;"));
        assertTrue(ddl.contains("vector(384)"));
        assertTrue(ddl.contains("idx_chunk_vectors_tenant"));
        assertTrue(ddl.contains("idx_chunk_vectors_hnsw"));
        assertTrue(ddl.contains("vector_cosine_ops"));

        float[] sampleVec = new float[]{0.5f, -0.25f, 1.0f};
        String formatted = PgVectorStore.formatVector(sampleVec);
        assertEquals("[0.5,-0.25,1.0]", formatted);
    }

    @Test
    void vectorStoreFactoryInstantiatesSqliteStore() throws SQLException {
        try (VectorStore store = VectorStoreFactory.create("jdbc:sqlite::memory:", null, null)) {
            assertNotNull(store);
            assertEquals("SQLITE_WAL", store.getStorageType());
            assertTrue(store.isAvailable());
        }
    }
}
