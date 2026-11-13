package com.writingassistant.analysis;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * High-level unified document extraction coordinator orchestrating native parsing,
 * OCR fallback for scanned materials, resource limits, and telemetry.
 */
public class UnifiedDocumentExtractor {

    private final DocumentExtractor extractor;
    private final OcrEngine ocrEngine;
    private final OcrResourceGovernor governor;

    public UnifiedDocumentExtractor() {
        this(new TesseractOcrEngine(), new OcrResourceGovernor());
    }

    public UnifiedDocumentExtractor(OcrEngine ocrEngine, OcrResourceGovernor governor) {
        this.ocrEngine = Objects.requireNonNull(ocrEngine, "ocrEngine must not be null");
        this.governor = Objects.requireNonNull(governor, "governor must not be null");
        this.extractor = new DocumentExtractor(List.of(
                new PdfDocumentParser(ocrEngine, governor),
                new DocxDocumentParser(),
                new PlainTextDocumentParser()
        ));
    }

    /**
     * Extracts text and structured metadata from a supported document.
     *
     * @param filename original filename
     * @param content  document bytes
     * @return extracted document with full telemetry
     * @throws IOException on parsing or reading failure
     */
    public DocumentExtractor.ExtractedDocument extract(String filename, byte[] content) throws IOException {
        return extractor.extract(filename, content);
    }

    public OcrEngine getOcrEngine() {
        return ocrEngine;
    }

    public OcrResourceGovernor getGovernor() {
        return governor;
    }

    public DocumentExtractor getExtractor() {
        return extractor;
    }
}
