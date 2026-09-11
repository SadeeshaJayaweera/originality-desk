package com.writingassistant.data;

import com.writingassistant.core.citation.CitationFormatter;
import com.writingassistant.core.citation.CitationStyle;
import com.writingassistant.core.citation.SourceQuote;
import com.writingassistant.core.citation.SourceType;
import com.writingassistant.logging.AppLogger;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite-backed persistence store for user citation sources, search autocomplete, and bibliography generation.
 */
public class CitationLibrary implements CitationDao {

    private static final Logger LOGGER = AppLogger.getLogger(CitationLibrary.class);

    private final DatabaseManager databaseManager;

    public CitationLibrary() {
        this(DatabaseManager.getInstance());
    }

    public CitationLibrary(DatabaseManager databaseManager) {
        this.databaseManager = (databaseManager != null) ? databaseManager : DatabaseManager.getInstance();
    }

    @Override
    public void saveCitation(com.writingassistant.core.citation.SourceCitation citation) throws SQLException {
        save(citation);
    }

    /**
     * Saves or updates a SourceQuote in the SQLite database (citations & citation_library tables).
     */
    @Override
    public void save(SourceQuote source) throws SQLException {
        if (source == null) {
            return;
        }

        if (source.getId() == null || source.getId().isBlank()) {
            source.setId(UUID.randomUUID().toString());
        }

        String sqlCitations = """
                INSERT INTO citations (id, author, year, title, publication, page_number, url_or_doi, source_type, volume, issue, original_quote)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    author = excluded.author,
                    year = excluded.year,
                    title = excluded.title,
                    publication = excluded.publication,
                    page_number = excluded.page_number,
                    url_or_doi = excluded.url_or_doi,
                    source_type = excluded.source_type,
                    volume = excluded.volume,
                    issue = excluded.issue,
                    original_quote = excluded.original_quote;
                """;

        String sqlLibrary = """
                INSERT INTO citation_library (id, author, title, container, year, page, url, source_type, style_preference, original_quote, volume, issue, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    author = excluded.author,
                    title = excluded.title,
                    container = excluded.container,
                    year = excluded.year,
                    page = excluded.page,
                    url = excluded.url,
                    source_type = excluded.source_type,
                    original_quote = excluded.original_quote,
                    volume = excluded.volume,
                    issue = excluded.issue;
                """;

        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(sqlCitations)) {
                stmt.setString(1, source.getId());
                stmt.setString(2, (source.getAuthor() != null) ? source.getAuthor().trim() : "Unknown");
                stmt.setString(3, source.getYear());
                stmt.setString(4, (source.getTitle() != null) ? source.getTitle().trim() : "Untitled");
                stmt.setString(5, source.getPublication());
                stmt.setString(6, source.getPageNumber());
                stmt.setString(7, source.getUrlOrDoi());
                stmt.setString(8, source.getSourceType() != null ? source.getSourceType().name() : SourceType.OTHER.name());
                stmt.setString(9, source.getVolume());
                stmt.setString(10, source.getIssue());
                stmt.setString(11, source.getQuote());
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement(sqlLibrary)) {
                stmt.setString(1, source.getId());
                stmt.setString(2, (source.getAuthor() != null) ? source.getAuthor().trim() : "Unknown");
                stmt.setString(3, (source.getTitle() != null) ? source.getTitle().trim() : "Untitled");
                stmt.setString(4, source.getPublication());
                stmt.setString(5, source.getYear());
                stmt.setString(6, source.getPageNumber());
                stmt.setString(7, source.getUrlOrDoi());
                stmt.setString(8, source.getSourceType() != null ? source.getSourceType().name() : SourceType.OTHER.name());
                stmt.setString(9, "APA");
                stmt.setString(10, source.getQuote());
                stmt.setString(11, source.getVolume());
                stmt.setString(12, source.getIssue());
                stmt.setLong(13, System.currentTimeMillis());
                stmt.executeUpdate();
            }

            LOGGER.fine("Saved citation to library: " + source.getId());
        }
    }

    @Override
    public Optional<com.writingassistant.core.citation.SourceCitation> findById(String id) throws SQLException {
        return findSourceById(id).map(com.writingassistant.core.citation.SourceCitation::fromSourceQuote);
    }

    /**
     * Retrieves a SourceQuote by unique ID.
     */
    @Override
    public Optional<SourceQuote> findSourceById(String id) throws SQLException {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        String sql = "SELECT * FROM citations WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToSourceQuote(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<com.writingassistant.core.citation.SourceCitation> findAll() throws SQLException {
        List<SourceQuote> quotes = findAllSources();
        List<com.writingassistant.core.citation.SourceCitation> citations = new ArrayList<>();
        for (SourceQuote q : quotes) {
            citations.add(com.writingassistant.core.citation.SourceCitation.fromSourceQuote(q));
        }
        return citations;
    }

    /**
     * Retrieves all saved SourceQuotes in the library.
     */
    @Override
    public List<SourceQuote> findAllSources() throws SQLException {
        List<SourceQuote> results = new ArrayList<>();
        String sql = "SELECT * FROM citations ORDER BY author ASC, year ASC";
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                results.add(mapRowToSourceQuote(rs));
            }
        }
        return results;
    }

    /**
     * Searches citation library by author, title, or publication for autocomplete filtering.
     */
    @Override
    public List<SourceQuote> search(String query) throws SQLException {
        if (query == null || query.isBlank()) {
            return findAllSources();
        }

        String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        String sql = """
                SELECT * FROM citations
                WHERE LOWER(author) LIKE ? OR LOWER(title) LIKE ? OR LOWER(publication) LIKE ?
                ORDER BY author ASC, year ASC
                """;

        List<SourceQuote> results = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, pattern);
            stmt.setString(2, pattern);
            stmt.setString(3, pattern);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRowToSourceQuote(rs));
                }
            }
        }
        return results;
    }

    @Override
    public void deleteById(String id) throws SQLException {
        if (id == null || id.isBlank()) {
            return;
        }
        String sql1 = "DELETE FROM citations WHERE id = ?";
        String sql2 = "DELETE FROM citation_library WHERE id = ?";
        String sql3 = "DELETE FROM document_citations WHERE citation_id = ?";

        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(sql1)) {
                stmt.setString(1, id);
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql2)) {
                stmt.setString(1, id);
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql3)) {
                stmt.setString(1, id);
                stmt.executeUpdate();
            }
            LOGGER.fine("Deleted citation from library: " + id);
        }
    }

    /**
     * Counts the total number of citations stored in the library.
     */
    @Override
    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM citations";
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Clears all citations from the library.
     */
    public void clearAll() throws SQLException {
        String sql1 = "DELETE FROM citations";
        String sql2 = "DELETE FROM citation_library";
        String sql3 = "DELETE FROM document_citations";

        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql1);
            stmt.executeUpdate(sql2);
            stmt.executeUpdate(sql3);
        }
    }

    /**
     * Generates a complete alphabetized list of formatted bibliography entries from all saved library sources.
     *
     * @param style Target citation standard (APA, MLA, Chicago)
     * @return List of formatted reference strings, alphabetized by author/title
     */
    public List<String> generateBibliography(CitationStyle style) throws SQLException {
        List<SourceQuote> sources = findAllSources();
        return generateBibliography(sources, style);
    }

    /**
     * Generates an alphabetized bibliography list for a specific collection of sources.
     *
     * @param sources Sources to format
     * @param style   Target citation standard (APA, MLA, Chicago)
     * @return Alphabetized list of formatted entries
     */
    public static List<String> generateBibliography(List<SourceQuote> sources, CitationStyle style) {
        if (sources == null || sources.isEmpty()) {
            return Collections.emptyList();
        }

        CitationStyle targetStyle = (style != null) ? style : CitationStyle.APA;

        // Sort alphabetically by author surname or title
        List<SourceQuote> sorted = new ArrayList<>(sources);
        sorted.sort((s1, s2) -> {
            String k1 = getSortKey(s1);
            String k2 = getSortKey(s2);
            return k1.compareToIgnoreCase(k2);
        });

        List<String> bibliography = new ArrayList<>();
        for (SourceQuote s : sorted) {
            String entry = CitationFormatter.formatBibliographyEntry(s, targetStyle);
            if (!entry.isBlank()) {
                bibliography.add(entry);
            }
        }
        return bibliography;
    }

    /**
     * Generates full bibliography text separated by double newlines.
     */
    public String generateBibliographyText(CitationStyle style) throws SQLException {
        List<String> entries = generateBibliography(style);
        return String.join(System.lineSeparator() + System.lineSeparator(), entries);
    }

    private static String getSortKey(SourceQuote s) {
        if (s == null) {
            return "";
        }
        if (s.getAuthor() != null && !s.getAuthor().isBlank()) {
            List<CitationFormatter.ParsedAuthor> authors = CitationFormatter.parseAuthors(s.getAuthor());
            if (!authors.isEmpty()) {
                return authors.get(0).getSurname();
            }
            return s.getAuthor().trim();
        }
        if (s.getTitle() != null && !s.getTitle().isBlank()) {
            return s.getTitle().trim();
        }
        return "";
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
