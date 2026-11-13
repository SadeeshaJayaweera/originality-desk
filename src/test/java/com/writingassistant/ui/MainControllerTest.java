package com.writingassistant.ui;

import com.writingassistant.config.ConfigManager;
import com.writingassistant.core.citation.CitationStyle;
import com.writingassistant.core.citation.SourceQuote;
import com.writingassistant.core.citation.SourceType;
import com.writingassistant.data.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MainControllerTest {
    @Test
    void documentSourcesNeverFallBackToLibraryAndRemovalOnlyUnlinks(@TempDir Path tempDir) throws Exception {
        ConfigManager config = new ConfigManager(tempDir);
        config.getConfig().setOfflineMode(true);
        DatabaseManager database = new DatabaseManager(tempDir.resolve("controller.db"));
        CitationLibrary library = new CitationLibrary(database);
        SqliteDocumentDao dao = new SqliteDocumentDao(database);
        DocumentRecord first = new DocumentRecord("First", null);
        DocumentRecord second = new DocumentRecord("Second", null);
        dao.save(first);
        dao.save(second);
        SourceQuote shared = new SourceQuote("Shared quote", "Author", "2024", "Shared title", null, null, null, SourceType.BOOK);
        SourceQuote unrelated = new SourceQuote("Other quote", "Other author", "2025", "Other title", null, null, null, SourceType.BOOK);
        library.save(shared);
        library.save(unrelated);
        dao.linkCitation(second.getId(), shared.getId());
        MainController controller = new MainController(config, database, first);

        assertTrue(controller.getDocumentSources().isEmpty(), "An uncited document must not show the library");
        assertTrue(CitationLibrary.generateBibliography(controller.getDocumentSources(), CitationStyle.APA).isEmpty());
        dao.linkCitation(first.getId(), shared.getId());
        assertEquals(1, controller.getDocumentSources().size());
        assertEquals(shared.getId(), controller.getDocumentSources().get(0).getId());

        controller.unlinkDocumentSource(shared.getId());

        assertTrue(controller.getDocumentSources().isEmpty(), "Removing the last citation must not repopulate it");
        assertEquals(2, library.count());
        assertEquals(shared.getId(), dao.getCitationsForDocument(second.getId()).get(0).getId());
    }
}
