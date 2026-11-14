package com.writingassistant.persistence.vector;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Factory for instantiating the appropriate VectorStore implementation
 * (PostgreSQL with pgvector for production environments or SQLite for development/testing).
 */
public final class VectorStoreFactory {

    private VectorStoreFactory() {}

    /**
     * Creates a VectorStore based on the JDBC URL.
     * If the URL starts with "jdbc:postgresql:", instantiates PgVectorStore.
     * Otherwise, instantiates SqliteVectorStore.
     */
    public static VectorStore create(String jdbcUrl, String username, String password) throws SQLException {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl must not be null");
        if (jdbcUrl.startsWith("jdbc:postgresql:")) {
            return new PgVectorStore(jdbcUrl, username, password);
        } else {
            return new SqliteVectorStore(jdbcUrl);
        }
    }

    /**
     * Creates a VectorStore wrapping an active JDBC connection.
     */
    public static VectorStore create(Connection connection, boolean isPostgres) {
        Objects.requireNonNull(connection, "connection must not be null");
        if (isPostgres) {
            return new PgVectorStore(connection);
        } else {
            return new SqliteVectorStore(connection);
        }
    }
}
