package com.writingassistant.analysis;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

/**
 * Production OCR engine executing the local Tesseract binary via ProcessBuilder
 * with strict timeouts, isolated temp files, and bounded memory usage.
 */
public class TesseractOcrEngine implements OcrEngine {

    private final String binaryPath;
    private final String language;
    private final long timeoutMs;
    private volatile Boolean availableCache;

    public TesseractOcrEngine() {
        this(
                System.getProperty("originality.tesseract.path",
                        System.getenv().getOrDefault("ORIGINALITY_TESSERACT_PATH", "tesseract")),
                "eng",
                30_000L
        );
    }

    public TesseractOcrEngine(String binaryPath, String language, long timeoutMs) {
        this.binaryPath = Objects.requireNonNull(binaryPath, "binaryPath must not be null");
        this.language = Objects.requireNonNull(language, "language must not be null");
        if (timeoutMs <= 0) throw new IllegalArgumentException("timeoutMs must be positive");
        this.timeoutMs = timeoutMs;
    }

    public String getBinaryPath() {
        return binaryPath;
    }

    public String getLanguage() {
        return language;
    }

    @Override
    public boolean isAvailable() {
        if (availableCache != null) {
            return availableCache;
        }
        synchronized (this) {
            if (availableCache != null) return availableCache;
            try {
                ProcessBuilder pb = new ProcessBuilder(binaryPath, "--version");
                pb.redirectErrorStream(true);
                Process process = pb.start();
                boolean completed = process.waitFor(3, TimeUnit.SECONDS);
                if (completed && process.exitValue() == 0) {
                    availableCache = true;
                } else {
                    if (!completed) process.destroyForcibly();
                    availableCache = false;
                }
            } catch (Exception e) {
                availableCache = false;
            }
            return availableCache;
        }
    }

    /**
     * Resets the cached availability state (useful after environment changes or tests).
     */
    public synchronized void resetAvailabilityCache() {
        this.availableCache = null;
    }

    @Override
    public String extractText(BufferedImage image) throws IOException {
        if (image == null) throw new IllegalArgumentException("image must not be null");
        if (!isAvailable()) {
            throw new OcrUnavailableException("Tesseract OCR is not available at '" + binaryPath + "'");
        }

        Path tempInput = Files.createTempFile("ocr_in_", ".png");
        Path tempOutputBase = Files.createTempFile("ocr_out_", "");
        Path expectedTextFile = Path.of(tempOutputBase.toString() + ".txt");

        try {
            ImageIO.write(image, "png", tempInput.toFile());

            ProcessBuilder pb = new ProcessBuilder(
                    binaryPath,
                    tempInput.toAbsolutePath().toString(),
                    tempOutputBase.toAbsolutePath().toString(),
                    "-l", language
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Tesseract OCR execution timed out after " + timeoutMs + " ms");
            }

            if (process.exitValue() != 0) {
                String errorOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                throw new IOException("Tesseract OCR failed with code " + process.exitValue() + ": " + errorOutput);
            }

            if (!Files.exists(expectedTextFile)) {
                return "";
            }

            String text = Files.readString(expectedTextFile, StandardCharsets.UTF_8);
            return text != null ? text.trim() : "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Tesseract OCR process was interrupted", e);
        } finally {
            try { Files.deleteIfExists(tempInput); } catch (Exception ignored) { }
            try { Files.deleteIfExists(tempOutputBase); } catch (Exception ignored) { }
            try { Files.deleteIfExists(expectedTextFile); } catch (Exception ignored) { }
        }
    }

    @Override
    public String extractTextFromPdfPage(byte[] pdfBytes, int pageIndex) throws IOException {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("pdfBytes must not be empty");
        }
        if (!isAvailable()) {
            throw new OcrUnavailableException("Tesseract OCR is not available at '" + binaryPath + "'");
        }

        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            if (pageIndex < 0 || pageIndex >= doc.getNumberOfPages()) {
                throw new IllegalArgumentException("Page index " + pageIndex + " is out of range [0, " + doc.getNumberOfPages() + ")");
            }
            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, 300, ImageType.RGB);
            return extractText(image);
        }
    }
}
