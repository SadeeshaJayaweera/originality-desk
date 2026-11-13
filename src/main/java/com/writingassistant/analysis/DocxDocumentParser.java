package com.writingassistant.analysis;

import java.io.IOException;
import java.util.Locale;

/**
 * Parser for Microsoft Word OpenXML documents (.docx).
 */
public final class DocxDocumentParser implements DocumentParser {

    @Override
    public String format() {
        return "DOCX";
    }

    @Override
    public boolean supports(String filename, byte[] content) {
        if (filename == null) return false;
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".docx");
    }

    @Override
    public DocumentExtractor.ExtractedDocument parse(String filename, byte[] content) throws IOException {
        long startTime = System.currentTimeMillis();
        if (!DocumentExtractor.zipMagic(content)) {
            throw new IOException("DOCX extension does not match ZIP magic");
        }
        DocumentExtractor.ExtractedDocument doc = DocxExtraction.extract(content);
        long duration = Math.max(0L, System.currentTimeMillis() - startTime);
        ExtractionMetadata metadata = ExtractionMetadata.of("DOCX", doc.text(), doc.pageCount(), false, duration);
        return new DocumentExtractor.ExtractedDocument(doc.text(), doc.pageCount(), "DOCX", metadata);
    }
}
