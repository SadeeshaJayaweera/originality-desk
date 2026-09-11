package com.writingassistant.core.rewrite;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Defensive parser for extracting clean alternative rewrites from AI responses.
 */
public final class RewriteResponseParser {

    private static final Pattern NUMBERED_LINE_PATTERN = Pattern.compile(
            "^(?:\\*\\*)?(?:Option\\s+)?(?:[0-9]{1,2}|[a-zA-Z])[.)\\]:]\\s*(?:\\*\\*)?\\s*(?:\"|')?(.*?)(?:\"|')?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern BULLET_LINE_PATTERN = Pattern.compile(
            "^(?:[-*•+]|\\*\\*[-*•+]\\*\\*)\\s*(?:\"|')?(.*?)(?:\"|')?$");

    private static final Pattern PREAMBLE_PATTERN = Pattern.compile(
            "^(?:here (?:are|is)|sure|certainly|below (?:are|is)|rewritten|options|alternatives|original|output).*?:?$",
            Pattern.CASE_INSENSITIVE);

    private RewriteResponseParser() {
    }

    /**
     * Parses the raw AI response text into a list of candidate alternative strings.
     *
     * @param rawResponse  Text returned by the AI model
     * @param originalText Fallback text in case of empty or unparseable response
     * @return List of clean alternative strings (guaranteed non-null, 1-3 items)
     */
    public static List<String> parseAlternatives(String rawResponse, String originalText) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return fallbackToOriginal(originalText);
        }

        List<String> alternatives = new ArrayList<>();
        String[] lines = rawResponse.split("\\r?\\n");

        // Pass 1: Check for explicitly numbered or labeled alternatives
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            Matcher numMatcher = NUMBERED_LINE_PATTERN.matcher(trimmed);
            if (numMatcher.matches()) {
                String candidate = cleanCandidateText(numMatcher.group(1));
                if (!candidate.isBlank()) {
                    alternatives.add(candidate);
                }
                continue;
            }

            Matcher bulletMatcher = BULLET_LINE_PATTERN.matcher(trimmed);
            if (bulletMatcher.matches()) {
                String candidate = cleanCandidateText(bulletMatcher.group(1));
                if (!candidate.isBlank() && !isPreamble(candidate)) {
                    alternatives.add(candidate);
                }
            }
        }

        // Pass 2: If no structured items were found, split clean non-preamble lines
        if (alternatives.isEmpty()) {
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !isPreamble(trimmed)) {
                    String cleaned = cleanCandidateText(trimmed);
                    if (!cleaned.isBlank()) {
                        alternatives.add(cleaned);
                    }
                }
            }
        }

        // Pass 3: If still empty or whole response is one block, clean the raw response
        if (alternatives.isEmpty()) {
            String cleaned = cleanCandidateText(rawResponse);
            if (!cleaned.isBlank()) {
                alternatives.add(cleaned);
            }
        }

        return alternatives.isEmpty() ? fallbackToOriginal(originalText) : alternatives;
    }

    private static boolean isPreamble(String line) {
        if (line.length() > 80) {
            return false;
        }
        return PREAMBLE_PATTERN.matcher(line.trim()).matches();
    }

    private static String cleanCandidateText(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.trim();

        // Strip markdown backticks
        if (cleaned.startsWith("```") && cleaned.endsWith("```")) {
            cleaned = cleaned.substring(3, cleaned.length() - 3).trim();
        }
        if (cleaned.startsWith("`") && cleaned.endsWith("`") && cleaned.length() > 2) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }

        // Strip leading/trailing double or single quotes if wrapping entire string
        if ((cleaned.startsWith("\"") && cleaned.endsWith("\"")) ||
            (cleaned.startsWith("'") && cleaned.endsWith("'")) && cleaned.length() >= 2) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }

        // Strip markdown bold asterisks around the start (e.g. **Option 1:** Text)
        if (cleaned.startsWith("**") && cleaned.contains("**")) {
            int secondBold = cleaned.indexOf("**", 2);
            if (secondBold != -1 && secondBold < 25) {
                cleaned = cleaned.substring(secondBold + 2).trim();
                if (cleaned.startsWith(":") || cleaned.startsWith("-")) {
                    cleaned = cleaned.substring(1).trim();
                }
            }
        }

        return cleaned.trim();
    }

    private static List<String> fallbackToOriginal(String originalText) {
        List<String> fallback = new ArrayList<>();
        if (originalText != null && !originalText.isBlank()) {
            fallback.add(originalText.trim());
        }
        return fallback;
    }
}
