package com.writingassistant.analysis;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Strategy contract for optical character recognition on raster images and scanned PDF pages.
 */
public interface OcrEngine {

    /**
     * Checks whether the underlying OCR engine is installed, configured, and responsive.
     *
     * @return true if OCR is available on this environment
     */
    boolean isAvailable();

    /**
     * Extracts text from a rendered raster image.
     *
     * @param image rendered image
     * @return extracted plaintext
     * @throws OcrUnavailableException if the OCR engine is not available
     * @throws IOException             if an I/O error or processing failure occurs
     */
    String extractText(BufferedImage image) throws IOException;

    /**
     * Renders a specific zero-based page of a PDF document into an image and performs OCR extraction.
     *
     * @param pdfBytes  raw PDF document bytes
     * @param pageIndex 0-based page index
     * @return extracted plaintext
     * @throws OcrUnavailableException if the OCR engine is not available
     * @throws IOException             if rendering or extraction fails
     */
    String extractTextFromPdfPage(byte[] pdfBytes, int pageIndex) throws IOException;
}
