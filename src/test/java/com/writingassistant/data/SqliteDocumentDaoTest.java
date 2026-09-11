package com.writingassistant.data;

import com.writingassistant.core.citation.SourceQuote;
import com.writingassistant.core.citation.SourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SqliteDocumentDaoTest {

    private DatabaseManager dbManager;
    private SqliteDocumentDao documentDao;
    private CitationLibrary citationLibrary;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        Path dbPath = tempDir.resolve("test_doc_dao.db");
        dbManager = new DatabaseManager("jdbc:sqlite:" + dbPath.toAbsolutePath());
        documentDao = new SqliteDocumentDao(dbManager);
        citationLibrary = new CitationLibrary(dbManager);
    }

    @Test
    @DisplayName("Document CRUD: save, update, findById, findAllRecent, deleteById")
    void testDocumentCrudOperations() throws SQLException {
        DocumentRecord doc1 = new DocumentRecord("Doc 1", "/path/to/doc1.docx");
        doc1.setLastOpened(1000L);
        DocumentRecord doc2 = new DocumentRecord("Doc 2", "/path/to/doc2.docx");
        doc2.setLastOpened(2000L);

        // Save
        documentDao.save(doc1);
        documentDao.save(doc2);

        // FindById
        Optional<DocumentRecord> found1 = documentDao.findById(doc1.getId());
        assertTrue(found1.isPresent());
        assertEquals("Doc 1", found1.get().getTitle());
        assertEquals("/path/to/doc1.docx", found1.get().getFilePath());

        // FindAllRecent (sorted by last_opened DESC)
        List<DocumentRecord> recent = documentDao.findAllRecent(10);
        assertEquals(2, recent.size());
        assertEquals(doc2.getId(), recent.get(0).getId(), "Doc 2 was opened more recently");

        // Update
        doc1.setTitle("Updated Doc 1");
        doc1.setLastOpened(3000L);
        documentDao.update(doc1);

        Optional<DocumentRecord> updated = documentDao.findById(doc1.getId());
        assertTrue(updated.isPresent());
        assertEquals("Updated Doc 1", updated.get().getTitle());

        List<DocumentRecord> recentAfterUpdate = documentDao.findAllRecent(10);
        assertEquals(doc1.getId(), recentAfterUpdate.get(0).getId(), "Doc 1 is now the most recently opened");

        // Delete
        documentDao.deleteById(doc1.getId());
        assertFalse(documentDao.findById(doc1.getId()).isPresent());
        assertEquals(1, documentDao.findAllRecent(10).size());
    }

    @Test
    @DisplayName("Many-to-many document-citation linking and per-document retrieval")
    void testDocumentCitationLinking() throws SQLException {
        DocumentRecord doc = new DocumentRecord("Research Paper", "/path/paper.docx");
        documentDao.save(doc);

        SourceQuote q1 = new SourceQuote("Quote 1", "Daniel Kahneman", "2011", "Thinking, Fast and Slow",
                "Farrar", "62", null, SourceType.BOOK);
        SourceQuote q2 = new SourceQuote("Quote 2", "Ashish Vaswani", "2017", "Attention Is All You Need",
                "NeurIPS", "5998", null, SourceType.JOURNAL);

        citationLibrary.save(q1);
        citationLibrary.save(q2);

        // Link citations
        documentDao.linkCitation(doc.getId(), q1.getId());
        documentDao.linkCitation(doc.getId(), q2.getId());

        // Retrieve linked citations
        List<SourceQuote> linked = documentDao.getCitationsForDocument(doc.getId());
        assertEquals(2, linked.size());
        assertTrue(linked.stream().anyMatch(s -> s.getAuthor().contains("Kahneman")));
        assertTrue(linked.stream().anyMatch(s -> s.getAuthor().contains("Vaswani")));

        // Unlink one citation
        documentDao.unlinkCitation(doc.getId(), q1.getId());
        List<SourceQuote> remaining = documentDao.getCitationsForDocument(doc.getId());
        assertEquals(1, remaining.size());
        assertEquals("Ashish Vaswani", remaining.get(0).getAuthor());
    }

    @Test
    @DisplayName("Rewrite history logging and retrieval by documentId")
    void testRewriteHistoryLogging() throws SQLException {
        String docId = "doc-123";
        RewriteHistoryEntry entry1 = new RewriteHistoryEntry(
                docId, "Original text 1", "TIGHTEN", "Tightened text 1");
        entry1.setCreatedAt(1000L);

        RewriteHistoryEntry entry2 = new RewriteHistoryEntry(
                docId, "Original text 2", "CLARITY", "Clear text 2");
        entry2.setCreatedAt(2000L);

        documentDao.logRewrite(entry1);
        documentDao.logRewrite(entry2);

        List<RewriteHistoryEntry> history = documentDao.getRewriteHistory(docId);
        assertEquals(2, history.size());
        assertEquals("Original text 2", history.get(0).getOriginalText(), "Most recent entry should be first");
        assertEquals("Clear text 2", history.get(0).getChosenAlternative());
        assertEquals("CLARITY", history.get(0).getMode());
    }
}
