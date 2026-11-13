package com.writingassistant.data;

import com.writingassistant.core.citation.CitationStyle;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.openxml4j.util.ZipSecureFile;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

/**
 * Service for importing and exporting Word (.docx) documents with rich formatting (italics, hanging indents)
 * powered by Apache POI.
 */
public class DocxService {

    public static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    public static final int MAX_TEXT_CHARS = 1_000_000;
    public static final long MAX_ZIP_EXPANDED_BYTES = 50L * 1024 * 1024;
    public static final long MAX_ZIP_ENTRY_BYTES = 16L * 1024 * 1024;
    public static final int MAX_ZIP_ENTRIES = 1_000;

    static {
        // POI settings are process-wide: never weaken a stricter existing policy.
        ZipSecureFile.setMinInflateRatio(Math.max(ZipSecureFile.getMinInflateRatio(), 0.01));
        ZipSecureFile.setMaxEntrySize(Math.min(ZipSecureFile.getMaxEntrySize(), MAX_ZIP_ENTRY_BYTES));
        ZipSecureFile.setMaxTextSize(Math.min(ZipSecureFile.getMaxTextSize(), MAX_TEXT_CHARS));
    }

    private static final Pattern ITALIC_PATTERN = Pattern.compile("\\*([^*]+)\\*|_([^_]+)_");

    /**
     * Reads plain text paragraphs from an existing .docx file.
     *
     * @param docxPath Path to the Word document
     * @return Extracted plain text joined by double newlines
     * @throws IOException if the file cannot be read
     */
    public static String importDocx(Path docxPath) throws IOException {
        if (!Files.exists(docxPath)) {
            throw new IOException("File not found: " + docxPath);
        }

        if (Files.size(docxPath) > MAX_FILE_BYTES) {
            throw new IOException("DOCX file exceeds the 10 MiB desktop import limit.");
        }
        byte[] bytes;
        try (InputStream is = Files.newInputStream(docxPath)) {
            bytes = is.readNBytes(MAX_FILE_BYTES + 1);
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IOException("DOCX file exceeds the 10 MiB desktop import limit.");
        }
        validateZipExpansion(bytes);
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.isBlank()) {
                    requireTextLimit((long) sb.length() + text.length() + (sb.length() > 0 ? 2 : 0));
                    if (sb.length() > 0) {
                        sb.append("\n\n");
                    }
                    sb.append(text.trim());
                }
            }
        } catch (RuntimeException e) {
            throw new IOException("DOCX could not be read safely. Check file validity and desktop import limits.", e);
        }
        return sb.toString();
    }

    /**
     * Exports document text and bibliography to a .docx file with formatted italic runs and hanging indents.
     *
     * @param targetPath          Destination .docx file path
     * @param documentText        Main document body text
     * @param bibliographyEntries Formatted reference entries (optional)
     * @param style               Citation style for heading naming
     * @throws IOException if writing fails
     */
    public static void exportDocx(Path targetPath, String documentText, List<String> bibliographyEntries, CitationStyle style)
            throws IOException {
        long textLength = documentText == null ? 0 : documentText.length();
        requireTextLimit(textLength);
        if (bibliographyEntries != null) {
            for (String entry : bibliographyEntries) {
                textLength += entry == null ? 0 : entry.length() + 2L;
                requireTextLimit(textLength);
            }
        }
        try (XWPFDocument document = new XWPFDocument()) {
            // 1. Write Main Document Body Paragraphs
            if (documentText != null && !documentText.isBlank()) {
                String[] paragraphs = documentText.split("\\r?\\n(?:\\r?\\n)+");
                for (String paraText : paragraphs) {
                    if (!paraText.isBlank()) {
                        XWPFParagraph p = document.createParagraph();
                        p.setSpacingAfter(200); // 10pt after paragraph
                        writeFormattedParagraph(p, paraText.trim());
                    }
                }
            }

            // 2. Append Bibliography Section (if provided)
            if (bibliographyEntries != null && !bibliographyEntries.isEmpty()) {
                // Section Heading
                XWPFParagraph heading = document.createParagraph();
                heading.setSpacingBefore(400);
                heading.setSpacingAfter(200);

                String headingTitle = getBibliographyHeading(style);
                XWPFRun headingRun = heading.createRun();
                headingRun.setText(headingTitle);
                headingRun.setBold(true);
                headingRun.setFontSize(14);
                headingRun.setFontFamily("Times New Roman");

                // Each Reference Entry with Hanging Indent (720 twips = 0.5 in)
                for (String entry : bibliographyEntries) {
                    if (entry != null && !entry.isBlank()) {
                        XWPFParagraph bibPara = document.createParagraph();
                        bibPara.setIndentationHanging(720); // Standard 0.5 inch hanging indent
                        bibPara.setIndentationLeft(720);
                        bibPara.setSpacingAfter(160);
                        writeFormattedParagraph(bibPara, entry.trim());
                    }
                }
            }

            // Write output file
            if (targetPath.getParent() != null && !Files.exists(targetPath.getParent())) {
                Files.createDirectories(targetPath.getParent());
            }
            try (OutputStream os = Files.newOutputStream(targetPath)) {
                document.write(os);
            }
        }
    }

    /**
     * Parses inline markdown italics (*text* or _text_) and creates corresponding normal/italic XWPFRuns.
     */
    private static void writeFormattedParagraph(XWPFParagraph paragraph, String text) {
        Matcher matcher = ITALIC_PATTERN.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();

            // Normal text preceding italic span
            if (start > lastEnd) {
                String normalText = text.substring(lastEnd, start);
                XWPFRun normalRun = paragraph.createRun();
                normalRun.setText(normalText);
                normalRun.setFontFamily("Times New Roman");
                normalRun.setFontSize(12);
            }

            // Italic text span (group 1 from *...* or group 2 from _..._)
            String italicContent = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            XWPFRun italicRun = paragraph.createRun();
            italicRun.setText(italicContent);
            italicRun.setItalic(true);
            italicRun.setFontFamily("Times New Roman");
            italicRun.setFontSize(12);

            lastEnd = end;
        }

        // Remaining trailing normal text
        if (lastEnd < text.length()) {
            String remainingText = text.substring(lastEnd);
            XWPFRun endRun = paragraph.createRun();
            endRun.setText(remainingText);
            endRun.setFontFamily("Times New Roman");
            endRun.setFontSize(12);
        }
    }

    private static void requireTextLimit(long chars) throws IOException {
        if (chars > MAX_TEXT_CHARS) {
            throw new IOException("DOCX text exceeds the " + MAX_TEXT_CHARS + " character desktop limit.");
        }
    }

    private static void validateZipExpansion(byte[] bytes) throws IOException {
        // Count actual inflated bytes, not untrusted ZIP size metadata, across all parts (including unused ones).
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            long total = 0;
            int entries = 0;
            byte[] buffer = new byte[8192];
            while (zip.getNextEntry() != null) {
                if (++entries > MAX_ZIP_ENTRIES) {
                    throw new IOException("DOCX contains too many ZIP entries.");
                }
                long entryBytes = 0;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    entryBytes += read;
                    total += read;
                    if (entryBytes > MAX_ZIP_ENTRY_BYTES || total > MAX_ZIP_EXPANDED_BYTES) {
                        throw new IOException("DOCX ZIP expansion exceeds desktop import limits.");
                    }
                }
            }
            if (entries == 0) {
                throw new IOException("DOCX is not a valid ZIP package.");
            }
        }
    }

    private static String getBibliographyHeading(CitationStyle style) {
        if (style == null) return "References";
        return switch (style) {
            case APA -> "References";
            case MLA -> "Works Cited";
            case CHICAGO -> "Bibliography";
        };
    }
}
