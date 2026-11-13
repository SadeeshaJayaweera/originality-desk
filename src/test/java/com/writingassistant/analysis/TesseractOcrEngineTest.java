package com.writingassistant.analysis;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TesseractOcrEngineTest {

    @Test
    void ocrUnavailableExceptionPreservesMessageAndCause() {
        OcrUnavailableException ex1 = new OcrUnavailableException("OCR not installed");
        assertEquals("OCR not installed", ex1.getMessage());

        Throwable cause = new RuntimeException("binary not found");
        OcrUnavailableException ex2 = new OcrUnavailableException("Failed to run", cause);
        assertEquals("Failed to run", ex2.getMessage());
        assertEquals(cause, ex2.getCause());
    }

    @Test
    void unavailableBinaryReportsFalseAndThrowsOcrUnavailableException() {
        TesseractOcrEngine engine = new TesseractOcrEngine("nonexistent-tesseract-binary-xyz", "eng", 5000L);
        assertFalse(engine.isAvailable());
        assertEquals("nonexistent-tesseract-binary-xyz", engine.getBinaryPath());
        assertEquals("eng", engine.getLanguage());

        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        assertThrows(OcrUnavailableException.class, () -> engine.extractText(image));
        assertThrows(OcrUnavailableException.class, () -> engine.extractTextFromPdfPage(new byte[10], 0));
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(NullPointerException.class, () -> new TesseractOcrEngine(null, "eng", 1000L));
        assertThrows(NullPointerException.class, () -> new TesseractOcrEngine("tesseract", null, 1000L));
        assertThrows(IllegalArgumentException.class, () -> new TesseractOcrEngine("tesseract", "eng", 0L));
        assertThrows(IllegalArgumentException.class, () -> new TesseractOcrEngine("tesseract", "eng", -5L));

        TesseractOcrEngine engine = new TesseractOcrEngine("dummy", "eng", 1000L);
        assertThrows(IllegalArgumentException.class, () -> engine.extractText(null));
        assertThrows(IllegalArgumentException.class, () -> engine.extractTextFromPdfPage(null, 0));
        assertThrows(IllegalArgumentException.class, () -> engine.extractTextFromPdfPage(new byte[0], 0));
    }

    @Test
    void mockTesseractBinaryExecutesAndReturnsExtractedText() throws Exception {
        // Create a mock executable script that mimics tesseract CLI: <in> <out> -l eng
        Path mockScript = Files.createTempFile("mock_tesseract_", ".sh");
        mockScript.toFile().setExecutable(true);

        try {
            // Script writes output to $2.txt when invoked
            String scriptContent = "#!/bin/sh\n"
                    + "if [ \"$1\" = \"--version\" ]; then\n"
                    + "  echo 'tesseract v5.3.0-mock'\n"
                    + "  exit 0\n"
                    + "fi\n"
                    + "echo 'Simulated Optical Character Recognition Text' > \"$2.txt\"\n"
                    + "exit 0\n";
            Files.writeString(mockScript, scriptContent);

            TesseractOcrEngine engine = new TesseractOcrEngine(mockScript.toAbsolutePath().toString(), "eng", 5000L);
            assertTrue(engine.isAvailable(), "Mock script should report available for --version");

            BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
            String result = engine.extractText(image);
            assertEquals("Simulated Optical Character Recognition Text", result);

            // Test PDF page extraction through the mock binary
            try (PDDocument doc = new PDDocument()) {
                doc.addPage(new PDPage());
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                doc.save(baos);
                byte[] pdfBytes = baos.toByteArray();

                String pdfResult = engine.extractTextFromPdfPage(pdfBytes, 0);
                assertEquals("Simulated Optical Character Recognition Text", pdfResult);

                // Check page index bounds
                assertThrows(IllegalArgumentException.class, () -> engine.extractTextFromPdfPage(pdfBytes, 1));
                assertThrows(IllegalArgumentException.class, () -> engine.extractTextFromPdfPage(pdfBytes, -1));
            }
        } finally {
            Files.deleteIfExists(mockScript);
        }
    }
}
