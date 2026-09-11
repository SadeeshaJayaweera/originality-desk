package com.writingassistant.data;

import com.writingassistant.core.citation.SourceQuote;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Data access interface for document metadata, many-to-many document citations, and rewrite history.
 */
public interface DocumentDao {

    void save(DocumentRecord doc) throws SQLException;

    void update(DocumentRecord doc) throws SQLException;

    Optional<DocumentRecord> findById(String id) throws SQLException;

    List<DocumentRecord> findAllRecent(int limit) throws SQLException;

    List<DocumentRecord> findAll() throws SQLException;

    void deleteById(String id) throws SQLException;

    void linkCitation(String documentId, String citationId) throws SQLException;

    void unlinkCitation(String documentId, String citationId) throws SQLException;

    List<SourceQuote> getCitationsForDocument(String documentId) throws SQLException;

    void logRewrite(RewriteHistoryEntry entry) throws SQLException;

    List<RewriteHistoryEntry> getRewriteHistory(String documentId) throws SQLException;

    // Backward compatibility methods
    void saveDocument(String id, String title, String filePath) throws SQLException;

    Optional<String> findFilePathById(String id) throws SQLException;

    List<String> listAllDocumentTitles() throws SQLException;
}
