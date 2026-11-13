package com.writingassistant.analysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentParserAbstractionTest {

    @Test
    void extractionMetadataComputesWordCountAccurately() {
        assertEquals(0, ExtractionMetadata.computeWordCount(null));
        assertEquals(0, ExtractionMetadata.computeWordCount("   "));
        assertEquals(1, ExtractionMetadata.computeWordCount("Originality"));
        assertEquals(5, ExtractionMetadata.computeWordCount("Originality Desk is very fast!"));
        assertEquals(5, ExtractionMetadata.computeWordCount("Multi-term, hyphenated words check."));
    }

    @Test
    void extractionMetadataEnforcesInvariants() {
        assertThrows(NullPointerException.class, () -> new ExtractionMetadata(null, 10, 2, 1, false, 5L));
        assertThrows(IllegalArgumentException.class, () -> new ExtractionMetadata("TXT", -1, 2, 1, false, 5L));
        assertThrows(IllegalArgumentException.class, () -> new ExtractionMetadata("TXT", 10, -1, 1, false, 5L));
        assertThrows(IllegalArgumentException.class, () -> new ExtractionMetadata("TXT", 10, 2, -1, false, 5L));
        assertThrows(IllegalArgumentException.class, () -> new ExtractionMetadata("TXT", 10, 2, 1, false, -1L));

        ExtractionMetadata metadata = ExtractionMetadata.of("TXT", "Hello world from Originality Desk", 1, false, 12L);
        assertEquals("TXT", metadata.format());
        assertEquals(33, metadata.characterCount());
        assertEquals(5, metadata.wordCount());
        assertEquals(1, metadata.pageCount());
        assertFalse(metadata.isOcr());
        assertEquals(12L, metadata.parseDurationMs());
    }

    @Test
    void extractedDocumentMaintainsBackwardCompatibility() {
        DocumentExtractor.ExtractedDocument doc = new DocumentExtractor.ExtractedDocument("Hello world", 2, "PDF");
        assertEquals("Hello world", doc.text());
        assertEquals(2, doc.pageCount());
        assertEquals("PDF", doc.format());
        assertNotNull(doc.metadata());
        assertEquals("PDF", doc.metadata().format());
        assertEquals(11, doc.metadata().characterCount());
        assertEquals(2, doc.metadata().wordCount());
        assertEquals(2, doc.metadata().pageCount());
        assertFalse(doc.metadata().isOcr());
    }

    @Test
    void defaultExtractorHasStandardParsers() {
        DocumentExtractor extractor = new DocumentExtractor();
        List<DocumentParser> parsers = extractor.getParsers();
        assertEquals(3, parsers.size());
        assertEquals("PDF", parsers.get(0).format());
        assertEquals("DOCX", parsers.get(1).format());
        assertEquals("TXT", parsers.get(2).format());
    }

    @Test
    void customParsersCanBeInjected() throws IOException {
        DocumentParser customParser = new DocumentParser() {
            @Override
            public DocumentExtractor.ExtractedDocument parse(String filename, byte[] content) {
                return new DocumentExtractor.ExtractedDocument("custom content", 1, "CUSTOM");
            }

            @Override
            public boolean supports(String filename, byte[] content) {
                return filename != null && filename.endsWith(".custom");
            }

            @Override
            public String format() {
                return "CUSTOM";
            }
        };

        DocumentExtractor extractor = new DocumentExtractor(List.of(customParser));
        assertEquals(1, extractor.getParsers().size());

        DocumentExtractor.ExtractedDocument doc = extractor.extract("test.custom", "dummy".getBytes(StandardCharsets.UTF_8));
        assertEquals("custom content", doc.text());
        assertEquals("CUSTOM", doc.format());
    }

    @Test
    void extractorRejectsEmptyOrNullParserList() {
        assertThrows(IllegalArgumentException.class, () -> new DocumentExtractor(null));
        assertThrows(IllegalArgumentException.class, () -> new DocumentExtractor(List.of()));
    }

    @Test
    void plainTextParserProducesCompleteMetadata() throws IOException {
        PlainTextDocumentParser parser = new PlainTextDocumentParser();
        assertTrue(parser.supports("doc.txt", new byte[0]));
        assertTrue(parser.supports("doc.TXT", new byte[0]));
        assertFalse(parser.supports("doc.pdf", new byte[0]));
        assertEquals("TXT", parser.format());

        byte[] textBytes = "This is a comprehensive test for the plain text parser.".getBytes(StandardCharsets.UTF_8);
        DocumentExtractor.ExtractedDocument doc = parser.parse("doc.txt", textBytes);
        assertEquals("TXT", doc.format());
        assertEquals(1, doc.pageCount());
        assertNotNull(doc.metadata());
        assertEquals("TXT", doc.metadata().format());
        assertEquals(textBytes.length, doc.metadata().characterCount());
        assertEquals(10, doc.metadata().wordCount());
        assertEquals(1, doc.metadata().pageCount());
        assertFalse(doc.metadata().isOcr());
        assertTrue(doc.metadata().parseDurationMs() >= 0);
    }
}
