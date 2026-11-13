package com.writingassistant.analysis;

import java.io.Serializable;

/**
 * Quality assessment for PDF text extraction to detect low-density, degraded, or scanned documents.
 */
public record PdfQualityMetrics(
        int totalPages,
        int pagesWithText,
        int totalCharacters,
        double averageCharsPerPage,
        boolean isLowTextDensity
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Minimum average characters per page below which a document is considered low density (likely scanned/image-based).
     */
    public static final double LOW_TEXT_THRESHOLD_CHARS_PER_PAGE = 40.0;

    public static PdfQualityMetrics assess(String text, int pageCount) {
        if (text == null || text.isBlank() || pageCount <= 0) {
            return new PdfQualityMetrics(Math.max(0, pageCount), 0, 0, 0.0, true);
        }
        String[] pages = text.split("\f", -1);
        int pagesWithText = 0;
        int totalChars = 0;
        for (String page : pages) {
            int lettersOrDigits = (int) page.codePoints().filter(Character::isLetterOrDigit).count();
            if (lettersOrDigits >= 10) {
                pagesWithText++;
            }
            totalChars += lettersOrDigits;
        }
        double avgChars = (double) totalChars / pageCount;
        boolean lowDensity = avgChars < LOW_TEXT_THRESHOLD_CHARS_PER_PAGE || (pageCount > 1 && pagesWithText < Math.ceil(pageCount / 2.0));
        return new PdfQualityMetrics(pageCount, pagesWithText, totalChars, avgChars, lowDensity);
    }
}
