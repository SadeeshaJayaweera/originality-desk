package com.writingassistant.similarity.chunking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-precision, sentence-aware document chunking strategy that preserves exact character offsets,
 * respects academic abbreviations, and supports sliding window sentence overlap.
 */
public class SentenceAwareChunkingStrategy implements ChunkingStrategy {

    private static final Set<String> ACADEMIC_ABBREVIATIONS = Set.of(
            "e.g", "i.e", "et al", "al", "fig", "figs", "dr", "prof", "mr", "mrs", "ms",
            "vol", "vols", "no", "nos", "pp", "p", "ed", "eds", "ref", "refs", "vs", "etc",
            "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "sept", "oct", "nov", "dec",
            "approx", "dept", "univ", "assoc", "inc", "ltd", "co", "corp", "ca"
    );

    private record SentenceSpan(int startOffset, int endOffset, String text, int tokenCount) {}

    @Override
    public List<DocumentChunk> chunk(String documentId, String text, ChunkingOptions options) {
        Objects.requireNonNull(documentId, "documentId must not be null");
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        ChunkingOptions opts = options != null ? options : ChunkingOptions.defaults();
        List<SentenceSpan> sentences = extractSentenceSpans(text);

        if (sentences.isEmpty()) {
            // Fallback for text with no punctuation
            String trimmed = text.strip();
            int start = text.indexOf(trimmed);
            int end = start + trimmed.length();
            int tokens = countTokens(trimmed);
            return List.of(DocumentChunk.of(documentId, 0, start, end, trimmed, 1));
        }

        List<DocumentChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;
        int i = 0;
        int n = sentences.size();

        while (i < n) {
            int firstSentenceIdx = i;
            int currentTokens = 0;
            int sentenceCount = 0;
            int lastSentenceIdx = i;

            while (lastSentenceIdx < n) {
                SentenceSpan span = sentences.get(lastSentenceIdx);
                int nextTokens = currentTokens + span.tokenCount();

                // Check paragraph break if preserveParagraphs is enabled
                if (opts.preserveParagraphs() && sentenceCount > 0 && currentTokens >= opts.minTokens()) {
                    SentenceSpan prevSpan = sentences.get(lastSentenceIdx - 1);
                    String gap = text.substring(prevSpan.endOffset(), span.startOffset());
                    if (gap.contains("\n\n") || gap.contains("\r\n\r\n")) {
                        break; // Stop chunk at paragraph boundary
                    }
                }

                if (sentenceCount > 0 && nextTokens > opts.maxTokens()) {
                    break; // Hard maximum exceeded
                }

                currentTokens = nextTokens;
                sentenceCount++;
                lastSentenceIdx++;

                if (currentTokens >= opts.targetTokens()) {
                    break; // Target tokens reached
                }
            }

            int actualLastIdx = lastSentenceIdx - 1;
            int chunkStart = sentences.get(firstSentenceIdx).startOffset();
            int chunkEnd = sentences.get(actualLastIdx).endOffset();
            String chunkText = text.substring(chunkStart, chunkEnd);

            chunks.add(new DocumentChunk(
                    documentId + ":chk:" + chunkIndex,
                    documentId,
                    chunkIndex,
                    chunkStart,
                    chunkEnd,
                    chunkText,
                    sentenceCount,
                    currentTokens
            ));
            chunkIndex++;

            // Advance pointer with sliding window overlap
            if (lastSentenceIdx >= n) {
                break;
            }

            int advance = Math.max(1, sentenceCount - opts.overlapSentences());
            i += advance;
        }

        return chunks;
    }

    private List<SentenceSpan> extractSentenceSpans(String text) {
        List<SentenceSpan> spans = new ArrayList<>();
        int length = text.length();
        int sentenceStart = -1;

        for (int idx = 0; idx < length; idx++) {
            char c = text.charAt(idx);

            if (sentenceStart == -1) {
                if (!Character.isWhitespace(c)) {
                    sentenceStart = idx;
                }
                continue;
            }

            // Check for sentence terminator: '.', '!', '?', or double newline
            boolean isTerminator = (c == '.' || c == '!' || c == '?');
            boolean isDoubleNewline = (c == '\n' && idx + 1 < length && text.charAt(idx + 1) == '\n');

            if (isTerminator) {
                // Check if this is part of a decimal number (e.g. "3.14" or "0.05")
                if (c == '.' && idx > 0 && idx + 1 < length
                        && Character.isDigit(text.charAt(idx - 1))
                        && Character.isDigit(text.charAt(idx + 1))) {
                    continue;
                }

                // Check for abbreviation preceding this period
                if (c == '.' && isAbbreviation(text, sentenceStart, idx)) {
                    continue;
                }

                // Sentence terminator confirmed if followed by whitespace, quote, or end of text
                if (idx + 1 == length || Character.isWhitespace(text.charAt(idx + 1))
                        || text.charAt(idx + 1) == '"' || text.charAt(idx + 1) == '”' || text.charAt(idx + 1) == '\'') {
                    int end = idx + 1;
                    // Include any closing quotes/brackets immediately following punctuation
                    while (end < length && (text.charAt(end) == '"' || text.charAt(end) == '”'
                            || text.charAt(end) == ')' || text.charAt(end) == ']')) {
                        end++;
                    }

                    String spanText = text.substring(sentenceStart, end);
                    spans.add(new SentenceSpan(sentenceStart, end, spanText, countTokens(spanText)));
                    sentenceStart = -1;
                    idx = end - 1;
                }
            } else if (isDoubleNewline) {
                int end = idx;
                String spanText = text.substring(sentenceStart, end).stripTrailing();
                if (!spanText.isEmpty()) {
                    spans.add(new SentenceSpan(sentenceStart, sentenceStart + spanText.length(), spanText, countTokens(spanText)));
                }
                sentenceStart = -1;
            }
        }

        if (sentenceStart != -1 && sentenceStart < length) {
            String trailing = text.substring(sentenceStart).stripTrailing();
            if (!trailing.isEmpty()) {
                spans.add(new SentenceSpan(sentenceStart, sentenceStart + trailing.length(), trailing, countTokens(trailing)));
            }
        }

        return spans;
    }

    private boolean isAbbreviation(String text, int sentenceStart, int periodIdx) {
        // Extract preceding word
        int wordStart = periodIdx - 1;
        while (wordStart >= sentenceStart && !Character.isWhitespace(text.charAt(wordStart))
                && text.charAt(wordStart) != '(' && text.charAt(wordStart) != '[') {
            wordStart--;
        }
        wordStart++;

        if (wordStart < periodIdx) {
            String word = text.substring(wordStart, periodIdx).toLowerCase(Locale.ROOT);
            if (ACADEMIC_ABBREVIATIONS.contains(word)) {
                return true;
            }
            // Check for multi-part abbreviation like "et al"
            if (word.equals("al") && wordStart >= 3) {
                String prev = text.substring(Math.max(0, wordStart - 4), wordStart).strip().toLowerCase(Locale.ROOT);
                if (prev.endsWith("et")) return true;
            }
        }
        return false;
    }

    private static int countTokens(String s) {
        if (s == null || s.isBlank()) return 0;
        int tokens = 0;
        boolean inWord = false;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                inWord = false;
            } else if (!inWord) {
                inWord = true;
                tokens++;
            }
        }
        return tokens;
    }
}
