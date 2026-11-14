package com.writingassistant.persistence.vector;

import com.writingassistant.similarity.chunking.EmbeddedChunk;
import com.writingassistant.similarity.onnx.TensorPooling;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * SQLite-backed vector store implementing tenant-isolated vector persistence
 * and cosine similarity search for local development and testing.
 */
public class SqliteVectorStore implements VectorStore {

    private final Connection connection;
    private final boolean ownsConnection;
    private volatile boolean closed = false;

    public SqliteVectorStore(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection must not be null");
        this.ownsConnection = false;
        initializeSchema();
    }

    public SqliteVectorStore(String jdbcUrl) throws SQLException {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl must not be null");
        this.connection = DriverManager.getConnection(jdbcUrl);
        this.ownsConnection = true;
        initializeSchema();
    }

    public static SqliteVectorStore inMemory() throws SQLException {
        return new SqliteVectorStore("jdbc:sqlite::memory:");
    }

    @Override
    public void initializeSchema() {
        String ddl = """
                CREATE TABLE IF NOT EXISTS document_chunk_vectors (
                    chunk_id TEXT PRIMARY KEY,
                    tenant_id TEXT NOT NULL,
                    document_id TEXT NOT NULL,
                    chunk_index INTEGER NOT NULL,
                    start_offset INTEGER NOT NULL,
                    end_offset INTEGER NOT NULL,
                    chunk_text TEXT NOT NULL,
                    model_name TEXT NOT NULL,
                    dimensions INTEGER NOT NULL,
                    vector_blob BLOB NOT NULL,
                    created_at INTEGER NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_chunk_vectors_tenant ON document_chunk_vectors(tenant_id, document_id);
                """;
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(ddl);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize SQLite vector store schema", e);
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
                INSERT OR REPLACE INTO document_chunk_vectors
                (chunk_id, tenant_id, document_id, chunk_index, start_offset, end_offset, chunk_text, model_name, dimensions, vector_blob, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (EmbeddedChunk c : chunks) {
                    byte[] blob = serializeVector(c.embedding());

                    ps.setString(1, c.chunkId());
                    ps.setString(2, tenantId);
                    ps.setString(3, c.documentId());
                    ps.setInt(4, c.chunk().chunkIndex());
                    ps.setInt(5, c.startOffset());
                    ps.setInt(6, c.endOffset());
                    ps.setString(7, c.text());
                    ps.setString(8, c.modelName());
                    ps.setInt(9, c.dimensions());
                    ps.setBytes(10, blob);
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
            throw new RuntimeException("Failed to store vector embeddings for tenant: " + tenantId, e);
        }
    }

    @Override
    public List<VectorSearchResult> searchSimilar(String tenantId, float[] queryVector, int limit, double minScore) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(queryVector, "queryVector must not be null");
        if (limit <= 0) return Collections.emptyList();

        String sql = "SELECT chunk_id, document_id, chunk_index, start_offset, end_offset, chunk_text, model_name, dimensions, vector_blob FROM document_chunk_vectors WHERE tenant_id = ?";

        List<VectorSearchResult> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String chunkId = rs.getString("chunk_id");
                    String documentId = rs.getString("document_id");
                    int chunkIndex = rs.getInt("chunk_index");
                    int startOffset = rs.getInt("start_offset");
                    int endOffset = rs.getInt("end_offset");
                    String chunkText = rs.getString("chunk_text");
                    String modelName = rs.getString("model_name");
                    int dimensions = rs.getInt("dimensions");
                    byte[] blob = rs.getBytes("vector_blob");

                    float[] vector = deserializeVector(blob, dimensions);
                    double score = TensorPooling.cosineSimilarity(queryVector, vector);

                    if (score >= minScore) {
                        results.add(new VectorSearchResult(
                                chunkId,
                                documentId,
                                chunkIndex,
                                startOffset,
                                endOffset,
                                chunkText,
                                score,
                                modelName
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to search similar vectors for tenant: " + tenantId, e);
        }

        Collections.sort(results);
        if (results.size() > limit) {
            return results.subList(0, limit);
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
            throw new RuntimeException("Failed to delete document vectors for tenant: " + tenantId + ", doc: " + documentId, e);
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
            throw new RuntimeException("Failed to delete embeddings for tenant: " + tenantId, e);
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
            throw new RuntimeException("Failed to count embeddings for tenant: " + tenantId, e);
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
        return "SQLITE_WAL";
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

    private static byte[] serializeVector(float[] vector) {
        byte[] bytes = new byte[vector.length * Float.BYTES];
        ByteBuffer.wrap(bytes).asFloatBuffer().put(vector);
        return bytes;
    }

    private static float[] deserializeVector(byte[] bytes, int dimensions) {
        float[] vector = new float[dimensions];
        ByteBuffer.wrap(bytes).asFloatBuffer().get(vector);
        return vector;
    }
}
