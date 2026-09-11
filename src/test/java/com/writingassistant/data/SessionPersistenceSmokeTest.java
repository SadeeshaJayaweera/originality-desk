package com.writingassistant.data;

import com.writingassistant.core.citation.CitationStyle;
import com.writingassistant.core.citation.SourceQuote;
import com.writingassistant.core.citation.SourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SessionPersistenceSmokeTest {

    @Test
    @DisplayName("Verify full session continuity: close & reopen app, confirm documents, citations, links, and rewrite history persist")
    void testSessionContinuityAcrossRestarts(@TempDir Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("session_continuity.db");

        String docId = "doc-session-001";
        String citationId = "cite-session-001";

        // =============================================================
        // Session 1: User works in the app
        // =============================================================
        {
            DatabaseManager dbManager1 = new DatabaseManager("jdbc:sqlite:" + dbFile.toAbsolutePath());
            SqliteDocumentDao docDao1 = new SqliteDocumentDao(dbManager1);
            CitationLibrary citationLib1 = new CitationLibrary(dbManager1);

            // 1. Create and save document
            DocumentRecord doc = new DocumentRecord(docId, "Cognitive Research Paper", "/path/to/research.docx",
                    System.currentTimeMillis(), System.currentTimeMillis());
            docDao1.save(doc);

            // 2. Create and save citation
            SourceQuote quote = new SourceQuote(
                    "A reliable way to make people believe in falsehoods is frequent repetition.",
                    "Daniel Kahneman", "2011", "Thinking, Fast and Slow", "Farrar, Straus and Giroux", "62", null, SourceType.BOOK
            );
            quote.setId(citationId);
            citationLib1.save(quote);

            // 3. Link citation to document
            docDao1.linkCitation(docId, citationId);

            // 4. Log an accepted rewrite
            RewriteHistoryEntry rewriteEntry = new RewriteHistoryEntry(
                    docId,
                    "Due to the fact that there was rain, the game stopped.",
                    "TIGHTEN",
                    "Because it rained, the game stopped."
            );
            docDao1.logRewrite(rewriteEntry);
        } // Session 1 terminates / closes

        // =============================================================
        // Session 2: User reopens the app from the same SQLite DB file
        // =============================================================
        {
            DatabaseManager dbManager2 = new DatabaseManager("jdbc:sqlite:" + dbFile.toAbsolutePath());
            SqliteDocumentDao docDao2 = new SqliteDocumentDao(dbManager2);
            CitationLibrary citationLib2 = new CitationLibrary(dbManager2);

            // 1. Verify Document Record
            Optional<DocumentRecord> reloadedDoc = docDao2.findById(docId);
            assertTrue(reloadedDoc.isPresent(), "Document record must survive app restart");
            assertEquals("Cognitive Research Paper", reloadedDoc.get().getTitle());
            assertEquals("/path/to/research.docx", reloadedDoc.get().getFilePath());

            // 2. Verify Citation Library Entry
            Optional<SourceQuote> reloadedQuote = citationLib2.findSourceById(citationId);
            assertTrue(reloadedQuote.isPresent(), "Citation library source must survive app restart");
            assertEquals("Daniel Kahneman", reloadedQuote.get().getAuthor());
            assertEquals("Thinking, Fast and Slow", reloadedQuote.get().getTitle());

            // 3. Verify Document Citation Link
            List<SourceQuote> docCitations = docDao2.getCitationsForDocument(docId);
            assertEquals(1, docCitations.size(), "Document-citation link must survive app restart");
            assertEquals("Daniel Kahneman", docCitations.get(0).getAuthor());

            // 4. Verify Per-Document Bibliography Generation from persisted state
            List<String> bibEntries = CitationLibrary.generateBibliography(docCitations, CitationStyle.APA);
            assertEquals(1, bibEntries.size());
            assertTrue(bibEntries.get(0).contains("Kahneman, D. (2011). *Thinking, Fast and Slow*."));

            // 5. Verify Rewrite History Log
            List<RewriteHistoryEntry> history = docDao2.getRewriteHistory(docId);
            assertEquals(1, history.size(), "Rewrite history log must survive app restart");
            assertEquals("Due to the fact that there was rain, the game stopped.", history.get(0).getOriginalText());
            assertEquals("Because it rained, the game stopped.", history.get(0).getChosenAlternative());
            assertEquals("TIGHTEN", history.get(0).getMode());

            System.out.println("=== Session Persistence Verification Succeeded ===");
            System.out.println("Restored Document: " + reloadedDoc.get().getTitle());
            System.out.println("Restored Citation: " + reloadedQuote.get().getAuthor() + " (" + reloadedQuote.get().getYear() + ")");
            System.out.println("Restored Rewrite:  " + history.get(0).getChosenAlternative());
        }
    }
}
