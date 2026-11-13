package com.writingassistant.data;

import com.writingassistant.core.citation.SourceQuote;
import com.writingassistant.core.citation.SourceType;
import com.writingassistant.logging.AppLogger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * SQLite implementation of DocumentDao for document metadata, citation relationships, and rewrite logs.
 */
public class SqliteDocumentDao implements DocumentDao {

    private static final Logger LOGGER = AppLogger.getLogger(SqliteDocumentDao.class);

    private final DatabaseManager databaseManager;

    public SqliteDocumentDao() {
        this(DatabaseManager.getInstance());
    }

    public SqliteDocumentDao(DatabaseManager databaseManager) {
        this.databaseManager = (databaseManager != null) ? databaseManager : DatabaseManager.getInstance();
    }

    @Override
    public void save(DocumentRecord doc) throws SQLException {
        if (doc == null) {
            return;
        }
        if (doc.getId() == null || doc.getId().isBlank()) {
            doc.setId(UUID.randomUUID().toString());
        }

        String sql = """
                INSERT INTO documents (id, title, file_path, last_opened, created_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    title = excluded.title,
                    file_path = excluded.file_path,
                    last_opened = excluded.last_opened;
                """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, doc.getId());
            stmt.setString(2, doc.getTitle() != null ? doc.getTitle() : "Untitled Document");
            stmt.setString(3, doc.getFilePath());
            stmt.setLong(4, doc.getLastOpened() > 0 ? doc.getLastOpened() : System.currentTimeMillis());
            stmt.setLong(5, doc.getCreatedAt() > 0 ? doc.getCreatedAt() : System.currentTimeMillis());
            stmt.executeUpdate();
            LOGGER.fine("Saved document record: " + doc.getId());
        }
    }

    @Override
    public void update(DocumentRecord doc) throws SQLException {
        save(doc);
    }

    @Override
    public Optional<DocumentRecord> findById(String id) throws SQLException {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        String sql = "SELECT id, title, file_path, last_opened, created_at FROM documents WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToDocument(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<DocumentRecord> findAllRecent(int limit) throws SQLException {
        int max = limit > 0 ? limit : 10;
        List<DocumentRecord> records = new ArrayList<>();
        String sql = "SELECT id, title, file_path, last_opened, created_at FROM documents ORDER BY last_opened DESC LIMIT ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, max);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    records.add(mapRowToDocument(rs));
                }
            }
        }
        return records;
    }

    @Override
    public List<DocumentRecord> findAll() throws SQLException {
        return findAllRecent(100);
    }

    @Override
    public void deleteById(String id) throws SQLException {
        if (id == null || id.isBlank()) {
            return;
        }

        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Explicit cleanup also supports legacy tables created without cascading foreign keys.
                for (String sql : List.of(
                        "DELETE FROM rewrite_history WHERE document_id = ?",
                        "DELETE FROM document_citations WHERE document_id = ?",
                        "DELETE FROM documents WHERE id = ?")) {
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, id);
                        stmt.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            }
            LOGGER.fine("Deleted document record: " + id);
        }
    }

    @Override
    public void linkCitation(String documentId, String citationId) throws SQLException {
        if (documentId == null || citationId == null) {
            return;
        }

        String sql = "INSERT OR IGNORE INTO document_citations (document_id, citation_id) VALUES (?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, documentId);
            stmt.setString(2, citationId);
            stmt.executeUpdate();
            LOGGER.fine("Linked citation " + citationId + " to document " + documentId);
        }
    }

    @Override
    public void unlinkCitation(String documentId, String citationId) throws SQLException {
        if (documentId == null || citationId == null) {
            return;
        }

        String sql = "DELETE FROM document_citations WHERE document_id = ? AND citation_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, documentId);
            stmt.setString(2, citationId);
            stmt.executeUpdate();
            LOGGER.fine("Unlinked citation " + citationId + " from document " + documentId);
        }
    }

    @Override
    public List<SourceQuote> getCitationsForDocument(String documentId) throws SQLException {
        List<SourceQuote> list = new ArrayList<>();
        if (documentId == null || documentId.isBlank()) {
            return list;
        }

        // Query both citations and citation_library tables for compatibility
        String sql = """
                SELECT c.id, c.author, c.year, c.title, c.publication, c.page_number, c.url_or_doi, c.source_type, c.volume, c.issue, c.original_quote
                FROM document_citations dc
                JOIN citations c ON dc.citation_id = c.id
                WHERE dc.document_id = ?
                ORDER BY c.author ASC, c.year ASC
                """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, documentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToSourceQuote(rs));
                }
            }
        }
        return list;
    }

    @Override
    public void logRewrite(RewriteHistoryEntry entry) throws SQLException {
        if (entry == null) {
            return;
        }
        if (entry.getId() == null || entry.getId().isBlank()) {
            entry.setId(UUID.randomUUID().toString());
        }

        String sql = """
                INSERT INTO rewrite_history (id, document_id, original_text, mode, chosen_alternative, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, entry.getId());
            stmt.setString(2, entry.getDocumentId());
            stmt.setString(3, entry.getOriginalText());
            stmt.setString(4, entry.getMode());
            stmt.setString(5, entry.getChosenAlternative());
            stmt.setLong(6, entry.getCreatedAt() > 0 ? entry.getCreatedAt() : System.currentTimeMillis());
            stmt.executeUpdate();
            LOGGER.fine("Logged rewrite history entry: " + entry.getId());
        }
    }

    @Override
    public List<RewriteHistoryEntry> getRewriteHistory(String documentId) throws SQLException {
        List<RewriteHistoryEntry> list = new ArrayList<>();
        String sql;
        if (documentId != null && !documentId.isBlank()) {
            sql = "SELECT id, document_id, original_text, mode, chosen_alternative, created_at FROM rewrite_history WHERE document_id = ? ORDER BY created_at DESC";
        } else {
            sql = "SELECT id, document_id, original_text, mode, chosen_alternative, created_at FROM rewrite_history ORDER BY created_at DESC LIMIT 50";
        }

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (documentId != null && !documentId.isBlank()) {
                stmt.setString(1, documentId);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    RewriteHistoryEntry e = new RewriteHistoryEntry(
                            rs.getString("id"),
                            rs.getString("document_id"),
                            rs.getString("original_text"),
                            rs.getString("mode"),
                            rs.getString("chosen_alternative"),
                            rs.getLong("created_at")
                    );
                    list.add(e);
                }
            }
        }
        return list;
    }

    // --- Backward compatibility implementations ---

    @Override
    public void saveDocument(String id, String title, String filePath) throws SQLException {
        save(new DocumentRecord(id, title, filePath, System.currentTimeMillis(), System.currentTimeMillis()));
    }

    @Override
    public Optional<String> findFilePathById(String id) throws SQLException {
        return findById(id).map(DocumentRecord::getFilePath);
    }

    @Override
    public List<String> listAllDocumentTitles() throws SQLException {
        List<String> titles = new ArrayList<>();
        String sql = "SELECT title FROM documents ORDER BY last_opened DESC";
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                titles.add(rs.getString("title"));
            }
        }
        return titles;
    }

    private DocumentRecord mapRowToDocument(ResultSet rs) throws SQLException {
        return new DocumentRecord(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("file_path"),
                rs.getLong("last_opened"),
                rs.getLong("created_at")
        );
    }

    private SourceQuote mapRowToSourceQuote(ResultSet rs) throws SQLException {
        SourceQuote quote = new SourceQuote();
        quote.setId(rs.getString("id"));
        quote.setAuthor(rs.getString("author"));
        quote.setYear(rs.getString("year"));
        quote.setTitle(rs.getString("title"));
        quote.setPublication(rs.getString("publication"));
        quote.setPageNumber(rs.getString("page_number"));
        quote.setUrlOrDoi(rs.getString("url_or_doi"));
        quote.setSourceType(SourceType.fromString(rs.getString("source_type")));
        quote.setVolume(rs.getString("volume"));
        quote.setIssue(rs.getString("issue"));
        quote.setQuote(rs.getString("original_quote"));
        return quote;
    }
}
