package com.writingassistant.analysis;

import java.io.IOException;
import java.util.Locale;

/**
 * Parser for PDF documents (.pdf) with automated OCR fallback for scanned pages.
 */
public final class PdfDocumentParser implements DocumentParser {

    private final OcrEngine ocrEngine;
    private final OcrResourceGovernor governor;

    public PdfDocumentParser() {
        this(new TesseractOcrEngine(), new OcrResourceGovernor());
    }

    public PdfDocumentParser(OcrEngine ocrEngine, OcrResourceGovernor governor) {
        this.ocrEngine = ocrEngine;
        this.governor = governor;
    }

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
        if (!DocumentExtractor.starts(content, "%PDF-")) {
            throw new IOException("PDF extension does not match PDF magic");
        }
        return PdfExtraction.extract(content, ocrEngine, governor);
    }

    public OcrEngine getOcrEngine() {
        return ocrEngine;
    }

    public OcrResourceGovernor getGovernor() {
        return governor;
    }
}
