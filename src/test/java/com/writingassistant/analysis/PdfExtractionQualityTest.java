package com.writingassistant.analysis;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PdfExtractionQualityTest {

    @Test
    void normalizesTypographicLigaturesAndArtifacts() {
        String input = "The pro\uFB01cient scienti\uFB01c re\uFB02ection uses e\uFB00icient a\uFB03liation and ba\uFB04ed reviewers.";
        String expected = "The proficient scientific reflection uses efficient affiliation and baffled reviewers.";
        assertEquals(expected, PdfExtraction.normalizeExtractedText(input));

        String inputWithSoftHyphens = "acad\u00ADemic inte\u00ADgrity";
        assertEquals("academic integrity", PdfExtraction.normalizeExtractedText(inputWithSoftHyphens));

        String inputWithZeroWidth = "word\u200Bwith\u200Czero\u200Dwidth\uFEFFchars";
        assertEquals("wordwithzerowidthchars", PdfExtraction.normalizeExtractedText(inputWithZeroWidth));
    }

    @Test
    void assessesQualityMetricsAccurately() {
        String normalText = "This is a normal academic page with adequate scholarly content.\fThis is page two with additional text.";
        PdfQualityMetrics highQuality = PdfQualityMetrics.assess(normalText, 2);
        assertEquals(2, highQuality.totalPages());
        assertEquals(2, highQuality.pagesWithText());
        assertFalse(highQuality.isLowTextDensity());
        assertTrue(highQuality.averageCharsPerPage() > 40.0);

        String lowDensityText = "A\fB\fC";
        PdfQualityMetrics lowQuality = PdfQualityMetrics.assess(lowDensityText, 3);
        assertEquals(3, lowQuality.totalPages());
        assertEquals(0, lowQuality.pagesWithText()); // Neither page has >= 10 alphanumeric chars
        assertTrue(lowQuality.isLowTextDensity());
        assertTrue(lowQuality.averageCharsPerPage() < 40.0);

        PdfQualityMetrics empty = PdfQualityMetrics.assess("", 0);
        assertTrue(empty.isLowTextDensity());
        assertEquals(0, empty.totalPages());
    }

    @Test
    void extractsPdfWithPositionalSorting() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            // Add text in reverse order on the page: lower text added first, header added second
            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                
                // Bottom content (Y = 100)
                stream.beginText();
                stream.newLineAtOffset(50, 100);
                stream.showText("Bottom paragraph written first in stream.");
                stream.endText();

                // Top content (Y = 700)
                stream.beginText();
                stream.newLineAtOffset(50, 700);
                stream.showText("Top title written second in stream.");
                stream.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            byte[] pdfBytes = baos.toByteArray();

            DocumentExtractor.ExtractedDocument extracted = PdfExtraction.extract(pdfBytes);
            assertEquals("PDF", extracted.format());
            assertEquals(1, extracted.pageCount());
            
            // Because sortByPosition is true, Top title (Y=700) must appear before Bottom paragraph (Y=100)
            int topIndex = extracted.text().indexOf("Top title");
            int bottomIndex = extracted.text().indexOf("Bottom paragraph");
            assertTrue(topIndex >= 0, "Top title should be extracted");
            assertTrue(bottomIndex >= 0, "Bottom paragraph should be extracted");
            assertTrue(topIndex < bottomIndex, "Top title should precede bottom paragraph due to positional sorting");
        }
    }
}
