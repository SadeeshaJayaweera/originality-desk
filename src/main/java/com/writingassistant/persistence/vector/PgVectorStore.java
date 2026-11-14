package com.writingassistant.persistence.vector;

import com.writingassistant.similarity.chunking.EmbeddedChunk;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Enterprise PostgreSQL vector store powered by the pgvector extension.
 * Executes approximate and exact nearest-neighbor vector searches using HNSW index
 * and cosine distance operator (<=>) with strict tenant isolation.
 */
public class PgVectorStore implements VectorStore {

    public static final String STORAGE_TYPE = "POSTGRESQL_PGVECTOR";
    public static final int DEFAULT_DIMENSIONS = 384;

    private final Connection connection;
    private final boolean ownsConnection;
    private final int dimensions;
    private volatile boolean closed = false;

    public PgVectorStore(Connection connection) {
        this(connection, DEFAULT_DIMENSIONS);
    }

    public PgVectorStore(Connection connection, int dimensions) {
        this.connection = Objects.requireNonNull(connection, "connection must not be null");
        this.ownsConnection = false;
        this.dimensions = dimensions > 0 ? dimensions : DEFAULT_DIMENSIONS;
    }

    public PgVectorStore(String jdbcUrl, String username, String password) throws SQLException {
        this(jdbcUrl, username, password, DEFAULT_DIMENSIONS);
    }

    public PgVectorStore(String jdbcUrl, String username, String password, int dimensions) throws SQLException {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl must not be null");
        this.connection = DriverManager.getConnection(jdbcUrl, username, password);
        this.ownsConnection = true;
        this.dimensions = dimensions > 0 ? dimensions : DEFAULT_DIMENSIONS;
    }

    @Override
    public void initializeSchema() {
        try (Statement stmt = connection.createStatement()) {
            // Enable pgvector extension
            stmt.execute("CREATE EXTENSION IF NOT EXISTS vector;");

            // Main vector storage table
            String tableDdl = String.format(Locale.ROOT, """
                    CREATE TABLE IF NOT EXISTS document_chunk_vectors (
                        chunk_id VARCHAR(128) PRIMARY KEY,
                        tenant_id VARCHAR(64) NOT NULL,
                        document_id VARCHAR(64) NOT NULL,
                        chunk_index INT NOT NULL,
                        start_offset INT NOT NULL,
                        end_offset INT NOT NULL,
                        chunk_text TEXT NOT NULL,
                        model_name VARCHAR(64) NOT NULL,
                        dimensions INT NOT NULL,
                        embedding vector(%d) NOT NULL,
                        created_at BIGINT NOT NULL
                    );
                    """, dimensions);
            stmt.execute(tableDdl);

            // Tenant and document scoping index
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chunk_vectors_tenant ON document_chunk_vectors (tenant_id, document_id);");

            // HNSW Approximate Nearest Neighbor index for cosine similarity
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chunk_vectors_hnsw ON document_chunk_vectors USING hnsw (embedding vector_cosine_ops);");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize PostgreSQL pgvector schema", e);
        }
    }

    @Override
    public void storeEmbedding(String tenantId, EmbeddedChunk chunk) {
        storeEmbeddings(tenantId, List.of(chunk));
    }

    @Override
    public void storeEmbeddings(String tenantId, List<EmbeddedChunk> chunks) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (chunks == null || chunks.isEmpty()) return;

        String sql = """
                INSERT INTO document_chunk_vectors
                (chunk_id, tenant_id, document_id, chunk_index, start_offset, end_offset, chunk_text, model_name, dimensions, embedding, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::vector, ?)
                ON CONFLICT (chunk_id) DO UPDATE SET
                    embedding = EXCLUDED.embedding,
                    chunk_text = EXCLUDED.chunk_text,
                    created_at = EXCLUDED.created_at;
                """;

        try {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (EmbeddedChunk c : chunks) {
                    String vectorStr = formatVector(c.embedding());

                    ps.setString(1, c.chunkId());
                    ps.setString(2, tenantId);
                    ps.setString(3, c.documentId());
                    ps.setInt(4, c.chunk().chunkIndex());
                    ps.setInt(5, c.startOffset());
                    ps.setInt(6, c.endOffset());
                    ps.setString(7, c.text());
                    ps.setString(8, c.modelName());
                    ps.setInt(9, c.dimensions());
                    ps.setString(10, vectorStr);
                    ps.setLong(11, c.generatedAtMillis());
                    ps.addBatch();
                }
                ps.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to store vector embeddings in PostgreSQL for tenant: " + tenantId, e);
        }
    }

    @Override
    public List<VectorSearchResult> searchSimilar(String tenantId, float[] queryVector, int limit, double minScore) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(queryVector, "queryVector must not be null");
        if (limit <= 0) return Collections.emptyList();

        String vectorStr = formatVector(queryVector);

        // Cosine distance <=> returns (1 - cosine_similarity).
        // Therefore cosine_similarity = 1.0 - (embedding <=> query::vector).
        String sql = """
                SELECT chunk_id, document_id, chunk_index, start_offset, end_offset, chunk_text, model_name,
                       (1.0 - (embedding <=> ?::vector)) AS score
                FROM document_chunk_vectors
                WHERE tenant_id = ? AND (1.0 - (embedding <=> ?::vector)) >= ?
                ORDER BY embedding <=> ?::vector ASC
                LIMIT ?;
                """;

        List<VectorSearchResult> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, vectorStr);
            ps.setString(2, tenantId);
            ps.setString(3, vectorStr);
            ps.setDouble(4, minScore);
            ps.setString(5, vectorStr);
            ps.setInt(6, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new VectorSearchResult(
                            rs.getString("chunk_id"),
                            rs.getString("document_id"),
                            rs.getInt("chunk_index"),
                            rs.getInt("start_offset"),
                            rs.getInt("end_offset"),
                            rs.getString("chunk_text"),
                            rs.getDouble("score"),
                            rs.getString("model_name")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to search similar vectors in pgvector for tenant: " + tenantId, e);
        }

        return results;
    }

    @Override
    public void deleteDocumentEmbeddings(String tenantId, String documentId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");

        String sql = "DELETE FROM document_chunk_vectors WHERE tenant_id = ? AND document_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setString(2, documentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete document embeddings from pgvector for tenant: " + tenantId, e);
        }
    }

    @Override
    public void deleteTenantEmbeddings(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        String sql = "DELETE FROM document_chunk_vectors WHERE tenant_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete embeddings from pgvector for tenant: " + tenantId, e);
        }
    }

    @Override
    public long countEmbeddings(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        String sql = "SELECT COUNT(*) FROM document_chunk_vectors WHERE tenant_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count embeddings in pgvector for tenant: " + tenantId, e);
        }
        return 0L;
    }

    @Override
    public boolean isAvailable() {
        try {
            return !closed && connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public String getStorageType() {
        return STORAGE_TYPE;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            if (ownsConnection) {
                try {
                    connection.close();
                } catch (SQLException ignored) {}
            }
        }
    }

    /**
     * Converts float array into pgvector literal format: "[0.123,0.456,-0.789]".
     */
    public static String formatVector(float[] vector) {
        Objects.requireNonNull(vector, "vector must not be null");
        StringBuilder sb = new StringBuilder(vector.length * 9 + 2);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Generates standard DDL migration script for pgvector setup.
     */
    public static String getSchemaDdl(int dimensions) {
        return String.format(Locale.ROOT, """
                CREATE EXTENSION IF NOT EXISTS vector;

                CREATE TABLE IF NOT EXISTS document_chunk_vectors (
                    chunk_id VARCHAR(128) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    document_id VARCHAR(64) NOT NULL,
                    chunk_index INT NOT NULL,
                    start_offset INT NOT NULL,
                    end_offset INT NOT NULL,
                    chunk_text TEXT NOT NULL,
                    model_name VARCHAR(64) NOT NULL,
                    dimensions INT NOT NULL,
                    embedding vector(%d) NOT NULL,
                    created_at BIGINT NOT NULL
                );

                CREATE INDEX IF NOT EXISTS idx_chunk_vectors_tenant ON document_chunk_vectors (tenant_id, document_id);
                CREATE INDEX IF NOT EXISTS idx_chunk_vectors_hnsw ON document_chunk_vectors USING hnsw (embedding vector_cosine_ops);
                """, dimensions);
    }
}
