package com.writingassistant.data;

import com.writingassistant.core.citation.CitationStyle;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocxServiceTest {

    @Test
    void oversizedInputFileIsRejectedBeforeParsing(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("oversized.docx");
        try (RandomAccessFile sparse = new RandomAccessFile(file.toFile(), "rw")) {
            sparse.setLength(DocxService.MAX_FILE_BYTES + 1L);
        }
        IOException error = assertThrows(IOException.class, () -> DocxService.importDocx(file));
        assertTrue(error.getMessage().contains("10 MiB"));
    }

    @Test
    void oversizedExportDoesNotTouchExistingTarget(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("keep.docx");
        Files.writeString(file, "keep existing file");
        String oversized = "a".repeat(DocxService.MAX_TEXT_CHARS + 1);
        assertThrows(IOException.class, () -> DocxService.exportDocx(file, oversized, List.of(), CitationStyle.APA));
        assertThrows(IOException.class, () -> DocxService.exportDocx(file, "body", List.of(oversized), CitationStyle.APA));
        assertEquals("keep existing file", Files.readString(file));
    }

    @Test
    void excessiveImportedTextIsRejected(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("too-much-text.docx");
        Random random = new Random(42);
        StringBuilder text = new StringBuilder(DocxService.MAX_TEXT_CHARS + 1);
        for (int i = 0; i <= DocxService.MAX_TEXT_CHARS; i++) {
            text.append((char) ('a' + random.nextInt(26)));
        }
        try (XWPFDocument document = new XWPFDocument(); OutputStream out = Files.newOutputStream(file)) {
            document.createParagraph().createRun().setText(text.toString());
            document.write(out);
        }
        IOException error = assertThrows(IOException.class, () -> DocxService.importDocx(file));
        assertTrue(error.getMessage().contains("character desktop limit"));
    }

    @Test
    void zipEntryAndAggregateExpansionAreBounded(@TempDir Path tempDir) throws Exception {
        Path entryBomb = tempDir.resolve("entry-bomb.docx");
        writeZip(entryBomb, 1, DocxService.MAX_ZIP_ENTRY_BYTES + 1);
        assertTrue(Files.size(entryBomb) < DocxService.MAX_FILE_BYTES);
        assertThrows(IOException.class, () -> DocxService.importDocx(entryBomb));

        Path aggregateBomb = tempDir.resolve("aggregate-bomb.docx");
        writeZip(aggregateBomb, 6, 9L * 1024 * 1024);
        IOException error = assertThrows(IOException.class, () -> DocxService.importDocx(aggregateBomb));
        assertTrue(error.getMessage().contains("ZIP expansion"));
    }

    @Test
    void excessiveZipEntryCountIsRejected(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("too-many-entries.docx");
        writeZip(file, DocxService.MAX_ZIP_ENTRIES + 1, 0);
        IOException error = assertThrows(IOException.class, () -> DocxService.importDocx(file));
        assertTrue(error.getMessage().contains("too many ZIP entries"));
    }

    @Test
    void poiCompressionRatioProtectionRemainsEnabled(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("ratio-bomb.docx");
        try (XWPFDocument document = new XWPFDocument(); OutputStream out = Files.newOutputStream(file)) {
            document.createParagraph().createRun().setText("a".repeat(200_000));
            document.write(out);
        }
        assertThrows(IOException.class, () -> DocxService.importDocx(file));
        assertTrue(org.apache.poi.openxml4j.util.ZipSecureFile.getMinInflateRatio() >= 0.01);
    }

    private static void writeZip(Path file, int entries, long bytesPerEntry) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            byte[] buffer = new byte[8192];
            for (int i = 0; i < entries; i++) {
                zip.putNextEntry(new ZipEntry("part-" + i + ".xml"));
                for (long remaining = bytesPerEntry; remaining > 0; remaining -= buffer.length) {
                    zip.write(buffer, 0, (int) Math.min(remaining, buffer.length));
                }
                zip.closeEntry();
            }
        }
    }

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
