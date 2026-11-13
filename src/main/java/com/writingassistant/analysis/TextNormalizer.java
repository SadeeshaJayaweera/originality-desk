package com.writingassistant.analysis;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Compatibility/case/whitespace normalization with original Java UTF-16, half-open coordinates. */
public final class TextNormalizer {
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{M}\\p{N}]*");
    private static final Pattern GRAPHEME = Pattern.compile("\\X");
    private TextNormalizer() { }

    public record Token(String value, int start, int end) { }
    public record Range(int start, int end) { }

    public static final class NormalizedText {
        private final String text;
        private final int[] starts;
        private final int[] ends;
        private final List<Token> tokens;

        private NormalizedText(String text, int[] starts, int[] ends, List<Token> tokens) {
            this.text = text;
            this.starts = starts;
            this.ends = ends;
            this.tokens = List.copyOf(tokens);
        }
        public String text() { return text; }
        public List<Token> tokens() { return tokens; }
        public int originalStart(int normalizedIndex) { return starts[normalizedIndex]; }
        public int originalEnd(int normalizedIndex) { return ends[normalizedIndex]; }
        public Range originalRange(int start, int end) {
            if (start < 0 || end <= start || end > text.length()) throw new IllegalArgumentException("A nonempty normalized range is required");
            return new Range(starts[start], ends[end - 1]);
        }
    }

    public static NormalizedText normalize(String original) {
        Objects.requireNonNull(original, "text");
        if (original.length() > DocumentExtractor.MAX_CHARACTERS) throw new IllegalArgumentException("Text exceeds 200000 UTF-16 characters");
        Mapping visible = new Mapping();
        for (int i = 0; i < original.length();) {
            int start = i;
            int cp = original.codePointAt(i);
            i += Character.charCount(cp);
            if (!invisible(cp)) for (char c : Character.toChars(cp)) visible.append(c, start, i);
        }
        Mapping mapping = new Mapping();
        // Grapheme boundaries preserve combining sequences and Hangul Jamo composition. Removing
        // invisibles first also lets accents compose across an original zero-width separator.
        var clusters = GRAPHEME.matcher(visible.text);
        while (clusters.find()) {
            int start = visible.starts[clusters.start()];
            int end = visible.ends[clusters.end() - 1];
            String normalized = Normalizer.normalize(clusters.group(), Normalizer.Form.NFKC)
                    .toLowerCase(Locale.ROOT);
            normalized = Normalizer.normalize(normalized, Normalizer.Form.NFKC);
            for (int j = 0; j < normalized.length();) {
                int cp = normalized.codePointAt(j);
                j += Character.charCount(cp);
                if (invisible(cp)) continue;
                if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                    mapping.space(start, end);
                } else {
                    char[] chars = Character.toChars(cp);
                    for (char c : chars) mapping.append(c, start, end);
                }
            }
        }
        String text = mapping.text.toString();
        List<Token> tokens = new ArrayList<>();
        var matcher = WORD.matcher(text);
        while (matcher.find()) {
            tokens.add(new Token(matcher.group(), mapping.starts[matcher.start()], mapping.ends[matcher.end() - 1]));
        }
        return new NormalizedText(text, Arrays.copyOf(mapping.starts, text.length()),
                Arrays.copyOf(mapping.ends, text.length()), tokens);
    }

    private static boolean invisible(int cp) {
        return Character.getType(cp) == Character.FORMAT || cp == 0x034F
                || cp >= 0xFE00 && cp <= 0xFE0F || cp >= 0xE0100 && cp <= 0xE01EF;
    }

    private static final class Mapping {
        private final StringBuilder text = new StringBuilder();
        private int[] starts = new int[256];
        private int[] ends = new int[256];
        void space(int start, int end) {
            int last = text.length() - 1;
            if (last >= 0 && text.charAt(last) == ' ') ends[last] = end;
            else append(' ', start, end);
        }
        void append(char c, int start, int end) {
            int index = text.length();
            if (index >= 1_000_000) throw new IllegalArgumentException("Normalized text expansion limit exceeded");
            if (index == starts.length) {
                starts = Arrays.copyOf(starts, starts.length * 2);
                ends = Arrays.copyOf(ends, ends.length * 2);
            }
            text.append(c);
            starts[index] = start;
            ends[index] = end;
        }
    }
}
