package com.writingassistant.data;

import com.writingassistant.core.citation.SourceCitation;
import com.writingassistant.core.citation.SourceQuote;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Data access interface for saved source citations and library search.
 */
public interface CitationDao {

    void save(SourceQuote source) throws SQLException;

    Optional<SourceQuote> findSourceById(String id) throws SQLException;

    List<SourceQuote> findAllSources() throws SQLException;

    List<SourceQuote> search(String query) throws SQLException;

    void deleteById(String id) throws SQLException;

    int count() throws SQLException;

    // --- Backward compatibility methods ---
    void saveCitation(SourceCitation citation) throws SQLException;

    Optional<SourceCitation> findById(String id) throws SQLException;

    List<SourceCitation> findAll() throws SQLException;
}
