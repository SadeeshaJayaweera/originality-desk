package com.writingassistant.data;

import com.writingassistant.core.citation.CitationStyle;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocxServiceTest {

    @Test
    @DisplayName("Exports document to .docx with italic runs and reads it back successfully")
    void testDocxExportAndImport(@TempDir Path tempDir) throws Exception {
        Path targetFile = tempDir.resolve("test_export.docx");

        String documentText = "This is a study on cognitive biases.\n\n" +
                "According to recent findings, familiarity is frequently mistaken for factual accuracy (Kahneman, 2011, p. 62).";

        List<String> bibliography = List.of(
                "Kahneman, D. (2011). *Thinking, Fast and Slow*. Farrar, Straus and Giroux.",
                "Vaswani, A., & Shazeer, N. (2017). Attention Is All You Need. *Advances in Neural Information Processing Systems*, *30*(1), 5998-6008."
        );

        // 1. Export to .docx
        DocxService.exportDocx(targetFile, documentText, bibliography, CitationStyle.APA);
        assertTrue(Files.exists(targetFile), "Exported .docx file must exist");
        assertTrue(Files.size(targetFile) > 0, "Exported .docx file must not be empty");

        // 2. Read back with DocxService.importDocx
        String importedText = DocxService.importDocx(targetFile);
        assertNotNull(importedText);
        assertTrue(importedText.contains("This is a study on cognitive biases."));
        assertTrue(importedText.contains("Kahneman, 2011"));
        assertTrue(importedText.contains("References"));
        assertTrue(importedText.contains("Thinking, Fast and Slow"));

        // 3. Inspect POI XWPFDocument structure to verify italics and hanging indents
        try (InputStream is = Files.newInputStream(targetFile);
             XWPFDocument doc = new XWPFDocument(is)) {
            List<XWPFParagraph> paragraphs = doc.getParagraphs();
            assertFalse(paragraphs.isEmpty());

            boolean foundItalicRun = false;
            boolean foundHangingIndent = false;

            for (XWPFParagraph p : paragraphs) {
                if (p.getIndentationHanging() == 720) {
                    foundHangingIndent = true;
                }
                for (XWPFRun run : p.getRuns()) {
                    if (run.isItalic()) {
                        foundItalicRun = true;
                    }
                }
            }

            assertTrue(foundItalicRun, "Document must contain italicized XWPFRuns for book/journal titles");
            assertTrue(foundHangingIndent, "Bibliography entries must have 0.5-inch (720 twips) hanging indent");
        }
    }
}
