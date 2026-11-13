package com.writingassistant.analysis;

import java.io.IOException;

/**
 * Thrown when OCR processing is requested or required, but the underlying OCR engine
 * (such as Tesseract) is not installed, not configured, or fails availability checks.
 */
public class OcrUnavailableException extends IOException {
    private static final long serialVersionUID = 1L;

    public OcrUnavailableException(String message) {
        super(message);
    }

    public OcrUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
