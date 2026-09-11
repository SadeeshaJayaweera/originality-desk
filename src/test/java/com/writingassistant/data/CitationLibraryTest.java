package com.writingassistant.data;

import com.writingassistant.core.citation.CitationStyle;
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

class CitationLibraryTest {

    @TempDir
    Path tempDir;

    private CitationLibrary library;

    @BeforeEach
    void setUp() {
        Path dbPath = tempDir.resolve("test_citation_library.db");
        DatabaseManager dbManager = new DatabaseManager(dbPath);
        library = new CitationLibrary(dbManager);
    }

    @Test
    @DisplayName("CitationLibrary performs CRUD operations on SourceQuote entries")
    void testCrudOperations() throws SQLException {
        assertEquals(0, library.count());

        // 1. Create & Save
        SourceQuote source1 = new SourceQuote(
                "Thinking is hard.",
                "Daniel Kahneman", "2011", "Thinking, Fast and Slow", "Farrar, Straus and Giroux",
                "10", null, SourceType.BOOK);
        source1.setId("cite-1");

        SourceQuote source2 = new SourceQuote(
                "Attention is key.",
                "Ashish Vaswani", "2017", "Attention Is All You Need", "Advances in NIPS",
                "5998", "https://doi.org/10.48550/arXiv.1706.03762", SourceType.JOURNAL);
        source2.setId("cite-2");

        library.save(source1);
        library.save(source2);

        assertEquals(2, library.count());

        // 2. Read by ID
        Optional<SourceQuote> retrieved = library.findSourceById("cite-1");
        assertTrue(retrieved.isPresent());
        assertEquals("Daniel Kahneman", retrieved.get().getAuthor());
        assertEquals("Thinking, Fast and Slow", retrieved.get().getTitle());
        assertEquals(SourceType.BOOK, retrieved.get().getSourceType());
        assertEquals("Thinking is hard.", retrieved.get().getQuote());

        // 3. Update existing
        source1.setTitle("Thinking, Fast and Slow (Updated Edition)");
        library.save(source1);
        assertEquals(2, library.count());
        Optional<SourceQuote> updated = library.findSourceById("cite-1");
        assertTrue(updated.isPresent());
        assertEquals("Thinking, Fast and Slow (Updated Edition)", updated.get().getTitle());

        // 4. Delete
        library.deleteById("cite-2");
        assertEquals(1, library.count());
        assertFalse(library.findSourceById("cite-2").isPresent());
    }

    @Test
    @DisplayName("CitationLibrary generates alphabetized bibliographies for APA, MLA, and Chicago")
    void testGenerateBibliographyAlphabetization() throws SQLException {
        // Add sources in non-alphabetical order
        SourceQuote sourceZ = new SourceQuote(null, "Slavoj Zizek", "2008", "In Defense of Lost Causes", "Verso");
        SourceQuote sourceA = new SourceQuote(null, "Hannah Arendt", "1958", "The Human Condition", "University of Chicago Press");
        SourceQuote sourceK = new SourceQuote(null, "Daniel Kahneman", "2011", "Thinking, Fast and Slow", "Farrar, Straus and Giroux");

        library.save(sourceZ);
        library.save(sourceA);
        library.save(sourceK);

        // APA Bibliography
        List<String> apaBib = library.generateBibliography(CitationStyle.APA);
        assertEquals(3, apaBib.size());
        assertTrue(apaBib.get(0).startsWith("Arendt, H."), "First entry should be Arendt: " + apaBib.get(0));
        assertTrue(apaBib.get(1).startsWith("Kahneman, D."), "Second entry should be Kahneman: " + apaBib.get(1));
        assertTrue(apaBib.get(2).startsWith("Zizek, S."), "Third entry should be Zizek: " + apaBib.get(2));

        // MLA Bibliography
        List<String> mlaBib = library.generateBibliography(CitationStyle.MLA);
        assertEquals(3, mlaBib.size());
        assertTrue(mlaBib.get(0).startsWith("Arendt, Hannah."));
        assertTrue(mlaBib.get(1).startsWith("Kahneman, Daniel."));
        assertTrue(mlaBib.get(2).startsWith("Zizek, Slavoj."));

        // Chicago Bibliography
        String chicagoText = library.generateBibliographyText(CitationStyle.CHICAGO);
        assertNotNull(chicagoText);
        assertTrue(chicagoText.contains("Arendt, Hannah. 1958."));
        assertTrue(chicagoText.contains("Kahneman, Daniel. 2011."));
        assertTrue(chicagoText.contains("Zizek, Slavoj. 2008."));
    }

    @Test
    @DisplayName("CitationLibrary search filters by author, title, or publication for autocomplete")
    void testSearchAutocomplete() throws SQLException {
        SourceQuote q1 = new SourceQuote(null, "Daniel Kahneman", "2011", "Thinking, Fast and Slow", "Farrar, Straus");
        SourceQuote q2 = new SourceQuote(null, "Ashish Vaswani", "2017", "Attention Is All You Need", "Advances in NIPS");
        SourceQuote q3 = new SourceQuote(null, "Donald Knuth", "1997", "The Art of Computer Programming", "Addison-Wesley");

        library.save(q1);
        library.save(q2);
        library.save(q3);

        // Search by author substring
        List<SourceQuote> resultsAuthor = library.search("kahneman");
        assertEquals(1, resultsAuthor.size());
        assertEquals("Daniel Kahneman", resultsAuthor.get(0).getAuthor());

        // Search by title keyword
        List<SourceQuote> resultsTitle = library.search("attention");
        assertEquals(1, resultsTitle.size());
        assertEquals("Attention Is All You Need", resultsTitle.get(0).getTitle());

        // Search by publisher
        List<SourceQuote> resultsPub = library.search("addison");
        assertEquals(1, resultsPub.size());
        assertEquals("Donald Knuth", resultsPub.get(0).getAuthor());

        // Search blank returns all
        assertEquals(3, library.search("").size());
    }
}
