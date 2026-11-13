package com.writingassistant.analysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Synchronous, stateless extraction; callers should invoke this on their background worker. */
public final class DocumentExtractor {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    public static final int MAX_CHARACTERS = 200_000;

    private final List<DocumentParser> parsers;

    /** pageCount is zero for DOCX (pagination requires rendering), one for TXT, actual for PDF. */
    public record ExtractedDocument(String text, int pageCount, String format, ExtractionMetadata metadata) {
        public ExtractedDocument(String text, int pageCount, String format) {
            this(text, pageCount, format, ExtractionMetadata.of(format, text, pageCount, false, 0L));
        }

        public ExtractedDocument {
            if (format != null && metadata == null) {
                metadata = ExtractionMetadata.of(format, text, pageCount, false, 0L);
            }
        }
    }

    public DocumentExtractor() {
        this(List.of(
                new PdfDocumentParser(),
                new DocxDocumentParser(),
                new PlainTextDocumentParser()
        ));
    }

    public DocumentExtractor(OcrEngine ocrEngine, OcrResourceGovernor governor) {
        this(List.of(
                new PdfDocumentParser(ocrEngine, governor),
                new DocxDocumentParser(),
                new PlainTextDocumentParser()
        ));
    }

    public DocumentExtractor(List<DocumentParser> parsers) {
        if (parsers == null || parsers.isEmpty()) {
            throw new IllegalArgumentException("At least one DocumentParser is required");
        }
        this.parsers = List.copyOf(parsers);
    }

    public List<DocumentParser> getParsers() {
        return parsers;
    }

    public ExtractedDocument extract(String filename, byte[] content) throws IOException {
        if (filename == null || content == null || content.length == 0) {
            throw new IOException("A filename and nonempty document are required");
        }
        if (content.length > MAX_BYTES) throw new IOException("Document exceeds the 10 MiB byte limit");

        for (DocumentParser parser : parsers) {
            if (parser.supports(filename, content)) {
                return parser.parse(filename, content);
            }
        }

        throw new IOException("Supported formats are PDF, DOCX and UTF-8 TXT");
    }

    static void checkLength(int length) throws IOException {
        if (length > MAX_CHARACTERS) throw new IOException("Extracted text exceeds the 200000 UTF-16 character limit");
    }

    static boolean zipMagic(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K'
                && bytes[2] == 3 && bytes[3] == 4;
    }

    static boolean starts(byte[] bytes, String prefix) {
        byte[] expected = prefix.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) if (bytes[i] != expected[i]) return false;
        return true;
    }
}
