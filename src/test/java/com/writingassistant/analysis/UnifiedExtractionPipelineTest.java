package com.writingassistant.analysis;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UnifiedExtractionPipelineTest {

    @Test
    void nativePdfBypassesOcrEngineWhenTextIsHighQuality() throws Exception {
        AtomicInteger ocrInvocations = new AtomicInteger(0);
        OcrEngine dummyOcr = new OcrEngine() {
            @Override public boolean isAvailable() { return true; }
            @Override public String extractText(BufferedImage image) { ocrInvocations.incrementAndGet(); return "ocr"; }
            @Override public String extractTextFromPdfPage(byte[] pdfBytes, int pageIndex) {
                ocrInvocations.incrementAndGet();
                return "ocr";
            }
        };

        UnifiedDocumentExtractor unified = new UnifiedDocumentExtractor(dummyOcr, new OcrResourceGovernor());

        // Create a PDF with abundant text (> 40 chars/page)
        byte[] pdfBytes;
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.beginText();
                stream.newLineAtOffset(50, 700);
                stream.showText("This is an extensive academic paper paragraph discussing research methodology and similarity.");
                stream.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            pdfBytes = baos.toByteArray();
        }

        DocumentExtractor.ExtractedDocument doc = unified.extract("paper.pdf", pdfBytes);
        assertEquals("PDF", doc.format());
        assertFalse(doc.metadata().isOcr(), "Native extraction should have been used, not OCR");
        assertEquals(0, ocrInvocations.get(), "OCR engine must not be invoked when native text is high density");
        assertTrue(doc.text().contains("research methodology and similarity"));
    }

    @Test
    void scannedPdfTriggersOcrFallbackWhenOcrIsAvailable() throws Exception {
        AtomicInteger ocrInvocations = new AtomicInteger(0);
        OcrEngine workingOcr = new OcrEngine() {
            @Override public boolean isAvailable() { return true; }
            @Override public String extractText(BufferedImage image) { return ""; }
            @Override public String extractTextFromPdfPage(byte[] pdfBytes, int pageIndex) {
                ocrInvocations.incrementAndGet();
                return "Optical Character Recognition Recovered Passage from Page " + (pageIndex + 1);
            }
        };

        UnifiedDocumentExtractor unified = new UnifiedDocumentExtractor(workingOcr, new OcrResourceGovernor());

        // Empty scanned page with 0 native text
        byte[] scannedPdfBytes;
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            scannedPdfBytes = baos.toByteArray();
        }

        DocumentExtractor.ExtractedDocument doc = unified.extract("scanned_document.pdf", scannedPdfBytes);
        assertEquals("PDF", doc.format());
        assertTrue(doc.metadata().isOcr(), "Metadata should indicate isOcr == true");
        assertEquals(1, ocrInvocations.get(), "OCR engine should have been called for the empty page");
        assertTrue(doc.text().contains("Optical Character Recognition Recovered Passage"));
        assertTrue(doc.metadata().characterCount() > 0);
    }

    @Test
    void scannedPdfThrowsInformativeErrorWhenOcrIsUnavailable() throws Exception {
        OcrEngine unavailableOcr = new OcrEngine() {
            @Override public boolean isAvailable() { return false; }
            @Override public String extractText(BufferedImage image) throws IOException { throw new OcrUnavailableException("Unavailable"); }
            @Override public String extractTextFromPdfPage(byte[] pdfBytes, int pageIndex) throws IOException { throw new OcrUnavailableException("Unavailable"); }
        };

        UnifiedDocumentExtractor unified = new UnifiedDocumentExtractor(unavailableOcr, new OcrResourceGovernor());

        byte[] emptyPdfBytes;
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            emptyPdfBytes = baos.toByteArray();
        }

        IOException ex = assertThrows(IOException.class, () -> unified.extract("scanned.pdf", emptyPdfBytes));
        assertTrue(ex.getMessage().contains("OCR is unavailable"), "Expected message containing 'OCR is unavailable', got: " + ex.getMessage());
    }

    @Test
    void extractsPlainTextThroughUnifiedPipeline() throws IOException {
        UnifiedDocumentExtractor unified = new UnifiedDocumentExtractor();
        byte[] textBytes = "Scholarly text extracted directly.".getBytes(StandardCharsets.UTF_8);
        DocumentExtractor.ExtractedDocument doc = unified.extract("notes.txt", textBytes);
        assertEquals("TXT", doc.format());
        assertFalse(doc.metadata().isOcr());
        assertEquals("Scholarly text extracted directly.", doc.text());
    }

    @Test
    void enforcesPageLimitDuringOcrFallback() throws Exception {
        OcrEngine workingOcr = new OcrEngine() {
            @Override public boolean isAvailable() { return true; }
            @Override public String extractText(BufferedImage image) { return "text"; }
            @Override public String extractTextFromPdfPage(byte[] pdfBytes, int pageIndex) { return "text"; }
        };

        // Governor allows at most 2 pages
        OcrConfig config = new OcrConfig(2, 200, 5000L, 10000L, 0.5f, 1);
        OcrResourceGovernor governor = new OcrResourceGovernor(config);
        UnifiedDocumentExtractor unified = new UnifiedDocumentExtractor(workingOcr, governor);

        // 3-page empty scan
        byte[] threePagePdf;
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            threePagePdf = baos.toByteArray();
        }

        IOException ex = assertThrows(IOException.class, () -> unified.extract("oversized_scan.pdf", threePagePdf));
        assertTrue(ex.getMessage().contains("exceeding maximum OCR page limit of 2"));
    }
}
