package com.writingassistant.analysis;

import java.io.IOException;

/**
 * Strategy interface for parsing specific document formats into standardized ExtractedDocument instances.
 */
public interface DocumentParser {

    /**
     * Parses the binary document content into an {@link DocumentExtractor.ExtractedDocument}.
     *
     * @param filename original filename (e.g., "paper.pdf")
     * @param content  raw document byte array
     * @return structured extracted document
     * @throws IOException if format parsing fails, content is invalid, or extraction limits are exceeded
     */
    DocumentExtractor.ExtractedDocument parse(String filename, byte[] content) throws IOException;

    /**
     * Checks if this parser can handle the specified file based on filename and/or content header magic bytes.
     *
     * @param filename original filename
     * @param content  raw document byte array
     * @return true if supported
     */
    boolean supports(String filename, byte[] content);

    /**
     * Returns the uppercase canonical format identifier (e.g., "PDF", "DOCX", "TXT").
     */
    String format();
}
