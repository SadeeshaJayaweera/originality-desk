package com.writingassistant.analysis;

import java.io.IOException;
import java.util.Locale;

/**
 * Parser for PDF documents (.pdf).
 */
public final class PdfDocumentParser implements DocumentParser {

    @Override
    public String format() {
        return "PDF";
    }

    @Override
    public boolean supports(String filename, byte[] content) {
        if (filename == null) return false;
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".pdf");
    }

    @Override
    public DocumentExtractor.ExtractedDocument parse(String filename, byte[] content) throws IOException {
        long startTime = System.currentTimeMillis();
        if (!DocumentExtractor.starts(content, "%PDF-")) {
            throw new IOException("PDF extension does not match PDF magic");
        }
        DocumentExtractor.ExtractedDocument doc = PdfExtraction.extract(content);
        long duration = Math.max(0L, System.currentTimeMillis() - startTime);
        ExtractionMetadata metadata = ExtractionMetadata.of("PDF", doc.text(), doc.pageCount(), false, duration);
        return new DocumentExtractor.ExtractedDocument(doc.text(), doc.pageCount(), "PDF", metadata);
    }
}
