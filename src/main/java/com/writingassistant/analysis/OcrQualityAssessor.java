package com.writingassistant.analysis;

import java.util.regex.Pattern;

/**
 * Quality evaluation utilities for calculating and estimating OCR confidence and lexical integrity.
 */
public final class OcrQualityAssessor {

    private static final Pattern WORD_SPLIT = Pattern.compile("\\s+");

    private OcrQualityAssessor() { }

    /**
     * Parses standard Tesseract TSV output to compute mean word-level confidence.
     *
     * @param tsvContent tab-separated output from Tesseract
     * @return normalized confidence in range [0.0, 1.0]
     */
    public static float computeTsvConfidence(String tsvContent) {
        if (tsvContent == null || tsvContent.isBlank()) return 0.0f;

        String[] lines = tsvContent.split("\r?\n");
        if (lines.length <= 1) return 0.0f;

        // Parse header to find column index of "conf"
        String[] headers = lines[0].split("\t");
        int confCol = -1;
        for (int i = 0; i < headers.length; i++) {
            if ("conf".equalsIgnoreCase(headers[i].trim())) {
                confCol = i;
                break;
            }
        }
        if (confCol == -1) confCol = 10; // Default TSV column index for confidence

        double totalConf = 0.0;
        int wordCount = 0;

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) continue;
            String[] cols = line.split("\t");
            if (cols.length > confCol) {
                try {
                    float conf = Float.parseFloat(cols[confCol].trim());
                    // Tesseract uses -1 for block/par/line records without text
                    if (conf >= 0.0f) {
                        totalConf += conf;
                        wordCount++;
                    }
                } catch (NumberFormatException ignored) { }
            }
        }

        if (wordCount == 0) return 0.0f;
        float mean = (float) (totalConf / wordCount / 100.0);
        return Math.max(0.0f, Math.min(1.0f, mean));
    }

    /**
     * Computes heuristic confidence for plain extracted text based on dictionary/lexical properties.
     *
     * @param text extracted OCR text
     * @return normalized confidence in range [0.0, 1.0]
     */
    public static float estimateConfidence(String text) {
        if (text == null || text.isBlank()) return 0.0f;

        int totalChars = 0;
        int letterCount = 0;
        int digitCount = 0;
        int noiseCount = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) continue;
            totalChars++;
            if (Character.isLetter(c)) letterCount++;
            else if (Character.isDigit(c)) digitCount++;
            else if ("~`^|<>{}[]\\#%*@$_=+".indexOf(c) >= 0) noiseCount++;
        }

        if (totalChars == 0) return 0.0f;

        double letterRatio = (double) letterCount / totalChars;
        double noiseRatio = (double) noiseCount / totalChars;

        String[] tokens = WORD_SPLIT.split(text.trim());
        int dictionaryLikeWords = 0;
        for (String token : tokens) {
            // A realistic natural language word consists mostly of letters, length 2 to 20
            if (token.matches("(?i)^[a-z]{2,20}[.,;:]?$")) {
                dictionaryLikeWords++;
            }
        }

        double wordRatio = tokens.length > 0 ? (double) dictionaryLikeWords / tokens.length : 0.0;

        // Composite score: heavily favors alphabetic content and genuine words, penalizes noise
        double score = (letterRatio * 0.50) + (wordRatio * 0.50) - (noiseRatio * 0.60);
        return (float) Math.max(0.0, Math.min(1.0, score));
    }
}
