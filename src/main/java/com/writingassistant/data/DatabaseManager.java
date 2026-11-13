package com.writingassistant.data;

import com.writingassistant.config.ConfigManager;
import com.writingassistant.logging.AppLogger;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.Locale;

import java.util.logging.Logger;

/**
 * Manages SQLite database connections, schema creation, and non-destructive migrations.
 */
public class DatabaseManager {

    private static final Logger LOGGER = AppLogger.getLogger(DatabaseManager.class);
    private static DatabaseManager instance;

    private final String jdbcUrl;

    public DatabaseManager() {
        this(ConfigManager.getInstance().getDatabasePath());
    }

    public DatabaseManager(Path dbPath) {
        this("jdbc:sqlite:" + dbPath.toAbsolutePath());
    }

    public DatabaseManager(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        initializeSchema();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public static synchronized void setInstance(DatabaseManager customInstance) {
        instance = customInstance;
    }

    /**
     * Obtains a new connection to the SQLite database.
     */
    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(jdbcUrl);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
            try (ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys")) {
                if (!rs.next() || rs.getInt(1) != 1) {
                    throw new SQLException("SQLite foreign key enforcement could not be enabled.");
                }
            }
            return conn;
        } catch (SQLException e) {
            try {
                conn.close();
            } catch (SQLException closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
    }

    /**
     * Initializes database tables if they do not exist and applies non-breaking schema migrations.
     */
    public void initializeSchema() {
        String createDocumentsTable = """
                CREATE TABLE IF NOT EXISTS documents (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    file_path TEXT,
                    last_opened INTEGER,
                    created_at INTEGER
                );
                """;

        String createCitationLibraryTable = """
                CREATE TABLE IF NOT EXISTS citation_library (
                    id TEXT PRIMARY KEY,
                    author TEXT NOT NULL,
                    title TEXT NOT NULL,
                    container TEXT,
                    year TEXT,
                    page TEXT,
                    url TEXT,
                    source_type TEXT,
                    style_preference TEXT,
                    original_quote TEXT,
                    volume TEXT,
                    issue TEXT,
                    created_at INTEGER
                );
                """;

        String createCitationsTable = """
                CREATE TABLE IF NOT EXISTS citations (
                    id TEXT PRIMARY KEY,
                    author TEXT NOT NULL,
                    year TEXT,
                    title TEXT NOT NULL,
                    publication TEXT,
                    page_number TEXT,
                    url_or_doi TEXT,
                    source_type TEXT,
                    volume TEXT,
                    issue TEXT,
                    original_quote TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
                """;

        String createDocumentCitationsTable = """
                CREATE TABLE IF NOT EXISTS document_citations (
                    document_id TEXT NOT NULL,
                    citation_id TEXT NOT NULL,
                    PRIMARY KEY (document_id, citation_id),
                    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
                    FOREIGN KEY (citation_id) REFERENCES citation_library(id) ON DELETE CASCADE
                );
                """;

        String createHistoryTable = """
                CREATE TABLE IF NOT EXISTS rewrite_history (
                    id TEXT PRIMARY KEY,
                    document_id TEXT,
                    original_text TEXT NOT NULL,
                    mode TEXT NOT NULL,
                    chosen_alternative TEXT NOT NULL,
                    created_at INTEGER
                );
                """;

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode = WAL;");
            conn.setAutoCommit(false);
            try {
                stmt.execute(createDocumentsTable);
                stmt.execute(createCitationLibraryTable);
                stmt.execute(createCitationsTable);
                stmt.execute(createDocumentCitationsTable);
                stmt.execute(createHistoryTable);

                // Safe column additions for backward compatibility / upgrades
                tryAddColumn(stmt, "documents", "last_opened", "INTEGER");
                tryAddColumn(stmt, "documents", "created_at", "INTEGER");
                tryAddColumn(stmt, "citations", "source_type", "TEXT");
                tryAddColumn(stmt, "citations", "volume", "TEXT");
                tryAddColumn(stmt, "citations", "issue", "TEXT");
                tryAddColumn(stmt, "citations", "original_quote", "TEXT");
                tryAddColumn(stmt, "rewrite_history", "document_id", "TEXT");
                tryAddColumn(stmt, "rewrite_history", "chosen_alternative", "TEXT");

                conn.commit();
            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            }
            LOGGER.info("Database schema initialized successfully with URL: " + jdbcUrl);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot initialize SQLite database at " + jdbcUrl
                    + ". Check directory permissions, available disk space, and schema compatibility before restarting.", e);
        }
    }

    private void tryAddColumn(Statement stmt, String table, String column, String type) throws SQLException {
        try {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type + ";");
        } catch (SQLException e) {
            if (e.getErrorCode() != 1 || e.getMessage() == null
                    || !e.getMessage().toLowerCase(Locale.ROOT).contains("duplicate column name: " + column)) {
                throw e;
            }
        }
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }
}
