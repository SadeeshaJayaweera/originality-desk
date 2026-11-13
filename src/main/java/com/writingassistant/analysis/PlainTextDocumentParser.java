package com.writingassistant.analysis;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Parser for strict UTF-8 plain text files (.txt).
 */
public final class PlainTextDocumentParser implements DocumentParser {

    @Override
    public String format() {
        return "TXT";
    }

    @Override
    public boolean supports(String filename, byte[] content) {
        if (filename == null) return false;
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".txt");
    }

    @Override
    public DocumentExtractor.ExtractedDocument parse(String filename, byte[] content) throws IOException {
        long startTime = System.currentTimeMillis();
        if (DocumentExtractor.zipMagic(content) || DocumentExtractor.starts(content, "%PDF-") || DocumentExtractor.starts(content, "{\\rtf")) {
            throw new IOException("TXT extension does not match document magic");
        }
        String text;
        try {
            var decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            CharBuffer chars = CharBuffer.allocate(DocumentExtractor.MAX_CHARACTERS + 2); // Allow an optional BOM.
            var decoded = decoder.decode(ByteBuffer.wrap(content), chars, true);
            if (decoded.isError()) decoded.throwException();
            if (decoded.isOverflow()) throw new IOException("Extracted text exceeds the 200000 character limit");
            var flushed = decoder.flush(chars);
            if (flushed.isError()) flushed.throwException();
            chars.flip();
            text = chars.toString();
        } catch (CharacterCodingException e) {
            throw new IOException("TXT must be strictly valid UTF-8", e);
        }
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t' && c != '\f') {
                throw new IOException("TXT contains NUL, binary or unsupported control characters");
            }
        }
        DocumentExtractor.checkLength(text.length());
        long duration = Math.max(0L, System.currentTimeMillis() - startTime);
        ExtractionMetadata metadata = ExtractionMetadata.of("TXT", text, 1, false, duration);
        return new DocumentExtractor.ExtractedDocument(text, 1, "TXT", metadata);
    }
}
