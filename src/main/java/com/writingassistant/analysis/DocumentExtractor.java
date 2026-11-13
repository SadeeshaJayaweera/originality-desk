package com.writingassistant.analysis;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Synchronous, stateless extraction; callers should invoke this on their background worker. */
public final class DocumentExtractor {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    public static final int MAX_CHARACTERS = 200_000;

    /** pageCount is zero for DOCX (pagination requires rendering), one for TXT, actual for PDF. */
    public record ExtractedDocument(String text, int pageCount, String format) { }

    public ExtractedDocument extract(String filename, byte[] content) throws IOException {
        if (filename == null || content == null || content.length == 0) {
            throw new IOException("A filename and nonempty document are required");
        }
        if (content.length > MAX_BYTES) throw new IOException("Document exceeds the 10 MiB byte limit");
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            if (!starts(content, "%PDF-")) throw new IOException("PDF extension does not match PDF magic");
            return PdfExtraction.extract(content);
        }
        if (lower.endsWith(".docx")) {
            if (!zipMagic(content)) throw new IOException("DOCX extension does not match ZIP magic");
            return DocxExtraction.extract(content);
        }
        if (!lower.endsWith(".txt")) throw new IOException("Supported formats are PDF, DOCX and UTF-8 TXT");
        if (zipMagic(content) || starts(content, "%PDF-") || starts(content, "{\\rtf")) {
            throw new IOException("TXT extension does not match document magic");
        }
        String text;
        try {
            var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            CharBuffer chars = CharBuffer.allocate(MAX_CHARACTERS + 2); // Allow an optional BOM.
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
        checkLength(text.length());
        return new ExtractedDocument(text, 1, "TXT");
    }

    static void checkLength(int length) throws IOException {
        if (length > MAX_CHARACTERS) throw new IOException("Extracted text exceeds the 200000 UTF-16 character limit");
    }

    static boolean zipMagic(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K'
                && bytes[2] == 3 && bytes[3] == 4;
    }

    private static boolean starts(byte[] bytes, String prefix) {
        byte[] expected = prefix.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) if (bytes[i] != expected[i]) return false;
        return true;
    }
}
