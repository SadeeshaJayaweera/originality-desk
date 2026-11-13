package com.writingassistant.analysis;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.ArrayList;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSObjectKey;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

final class PdfExtraction {
    private static final int MAX_PAGES = 2_000;
    private static final int MAX_OBJECTS = 20_000;
    private static final int MAX_STREAM_BYTES = 8 * 1024 * 1024;
    private static final int MAX_EXPANDED_BYTES = 32 * 1024 * 1024;

    private PdfExtraction() { }

    static DocumentExtractor.ExtractedDocument extract(byte[] content) throws IOException {
        return extract(content, null, null);
    }

    static DocumentExtractor.ExtractedDocument extract(byte[] content, OcrEngine ocrEngine, OcrResourceGovernor governor) throws IOException {
        long startTime = System.currentTimeMillis();
        try (PDDocument pdf = Loader.loadPDF(content)) {
            if (pdf.isEncrypted()) throw new IOException("Encrypted PDF documents are not supported");
            int pages = pdf.getNumberOfPages();
            if (pages > MAX_PAGES) throw new IOException("PDF exceeds the 2000 page limit");
            checkStreams(pdf);
            LimitedWriter writer = new LimitedWriter();
            PDFTextStripper stripper = new PDFTextStripper() {
                private int positions;
                @Override
                protected void processTextPosition(TextPosition position) {
                    // PDFTextStripper buffers a page before writing; also bound that upstream buffer.
                    positions += Math.max(1, position.getUnicode().length());
                    if (positions > DocumentExtractor.MAX_CHARACTERS) throw new TextBudgetExceeded();
                    super.processTextPosition(position);
                }
            };
            stripper.setSortByPosition(true);
            stripper.setSpacingTolerance(0.5f);
            stripper.setAverageCharTolerance(0.3f);
            stripper.setPageStart("");
            stripper.setPageEnd("\f");
            stripper.writeText(pdf, writer);
            String rawText = writer.text.toString();
            if (rawText.endsWith("\f")) rawText = rawText.substring(0, rawText.length() - 1);
            String nativeText = normalizeExtractedText(rawText);
            boolean hasNativeText = nativeText.codePoints().anyMatch(Character::isLetterOrDigit);

            PdfQualityMetrics quality = PdfQualityMetrics.assess(nativeText, pages);

            // Fast path: if native extraction produced rich, acceptable text density, return immediately.
            if (hasNativeText && !quality.isLowTextDensity()) {
                long duration = Math.max(0L, System.currentTimeMillis() - startTime);
                ExtractionMetadata metadata = ExtractionMetadata.of("PDF", nativeText, pages, false, duration);
                return new DocumentExtractor.ExtractedDocument(nativeText, pages, "PDF", metadata);
            }

            // If OCR is not available or not configured:
            if (ocrEngine == null || !ocrEngine.isAvailable()) {
                if (!hasNativeText) {
                    throw new IOException("PDF has no extractable text; OCR is unavailable");
                }
                // Return what native text exists when OCR cannot run
                long duration = Math.max(0L, System.currentTimeMillis() - startTime);
                ExtractionMetadata metadata = ExtractionMetadata.of("PDF", nativeText, pages, false, duration);
                return new DocumentExtractor.ExtractedDocument(nativeText, pages, "PDF", metadata);
            }

            // OCR is available and required (empty or low-density scanned PDF):
            if (governor != null) {
                governor.checkPageLimit(pages);
            }

            StringBuilder ocrText = new StringBuilder();
            for (int i = 0; i < pages; i++) {
                final int pageIndex = i;
                String pageOcr;
                if (governor != null) {
                    pageOcr = governor.executeWithPermit(() -> ocrEngine.extractTextFromPdfPage(content, pageIndex));
                } else {
                    pageOcr = ocrEngine.extractTextFromPdfPage(content, pageIndex);
                }

                if (pageOcr != null && !pageOcr.isBlank()) {
                    if (ocrText.length() > 0) ocrText.append("\f");
                    ocrText.append(pageOcr.trim());
                }
                DocumentExtractor.checkLength(ocrText.length());
            }

            String finalOcrText = ocrText.toString();
            boolean hasOcrText = finalOcrText.codePoints().anyMatch(Character::isLetterOrDigit);

            if (!hasOcrText) {
                if (hasNativeText) {
                    long duration = Math.max(0L, System.currentTimeMillis() - startTime);
                    ExtractionMetadata metadata = ExtractionMetadata.of("PDF", nativeText, pages, false, duration);
                    return new DocumentExtractor.ExtractedDocument(nativeText, pages, "PDF", metadata);
                }
                throw new IOException("PDF has no extractable text; OCR produced no readable characters");
            }

            long duration = Math.max(0L, System.currentTimeMillis() - startTime);
            ExtractionMetadata metadata = ExtractionMetadata.of("PDF", finalOcrText, pages, true, duration);
            return new DocumentExtractor.ExtractedDocument(finalOcrText, pages, "PDF", metadata);
        } catch (InvalidPasswordException e) {
            throw new IOException("Encrypted PDF documents are not supported", e);
        } catch (TextBudgetExceeded e) {
            throw new IOException("Extracted PDF text exceeds the 200000 character budget", e);
        } catch (RuntimeException e) {
            throw new IOException("Malformed or unsupported PDF", e);
        }
    }

    /**
     * Normalizes extracted PDF text to resolve typographic ligatures, soft hyphens,
     * and zero-width artifacts common in academic typesetting (LaTeX, InDesign).
     */
    static String normalizeExtractedText(String text) {
        if (text == null || text.isEmpty()) return "";
        return text
                .replace("\uFB00", "ff")
                .replace("\uFB01", "fi")
                .replace("\uFB02", "fl")
                .replace("\uFB03", "ffi")
                .replace("\uFB04", "ffl")
                .replace("\uFB05", "ft")
                .replace("\uFB06", "st")
                .replace("\u00AD", "")
                .replace("\u200B", "")
                .replace("\u200C", "")
                .replace("\u200D", "")
                .replace("\uFEFF", "");
    }

    static PdfQualityMetrics assessQuality(String text, int pages) {
        return PdfQualityMetrics.assess(text, pages);
    }

    private static void checkStreams(PDDocument pdf) throws IOException {
        var document = pdf.getDocument();
        if (document.getXrefTable().size() > MAX_OBJECTS) throw new IOException("PDF object limit exceeded");
        long total = 0;
        byte[] buffer = new byte[8192];
        for (COSObjectKey key : new ArrayList<>(document.getXrefTable().keySet())) {
            COSBase object = document.getObjectFromPool(key).getObject();
            if (!(object instanceof COSStream stream)) continue;
            long expanded = 0;
            try (InputStream input = stream.createInputStream()) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    expanded += count;
                    total += count;
                    if (expanded > MAX_STREAM_BYTES || total > MAX_EXPANDED_BYTES
                            || expanded > Math.max(1L, stream.getLength()) * 200L) {
                        throw new IOException("PDF expanded stream size or compression ratio limit exceeded");
                    }
                }
            }
        }
    }

    private static final class TextBudgetExceeded extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private static final class LimitedWriter extends Writer {
        private final StringBuilder text = new StringBuilder();
        @Override
        public void write(char[] chars, int offset, int count) throws IOException {
            DocumentExtractor.checkLength(text.length() + count);
            text.append(chars, offset, count);
        }
        @Override public void flush() { }
        @Override public void close() { }
    }
}
