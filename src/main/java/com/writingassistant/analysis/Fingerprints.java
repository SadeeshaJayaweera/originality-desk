package com.writingassistant.analysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Collision-free normalized word shingles; offsets always refer to the original input. */
public final class Fingerprints {
    private static final Set<String> STOP = Set.of("a", "an", "and", "are", "as", "at", "be", "been", "by",
            "for", "from", "has", "have", "in", "is", "it", "its", "of", "on", "or", "that", "the", "their",
            "these", "this", "to", "was", "were", "which", "will", "with", "not", "we", "you", "our", "all");
    private static final List<String> BOILERPLATE = List.of("all rights reserved", "this page intentionally left blank",
            "terms and conditions", "for informational purposes only", "no part of this publication may be reproduced");

    private Fingerprints() { }
    public record Fingerprint(String value, int tokenIndex, int start, int end) { }

    public static List<Fingerprint> wordShingles(String text, int words) {
        return wordShingles(TextNormalizer.normalize(text), words);
    }

    public static List<Fingerprint> wordShingles(TextNormalizer.NormalizedText text, int words) {
        if (words < 1 || words > 64) throw new IllegalArgumentException("Shingle size must be 1..64");
        List<TextNormalizer.Token> tokens = text.tokens();
        List<Fingerprint> result = new ArrayList<>();
        for (int i = 0; i + words <= tokens.size(); i++) {
            StringBuilder key = new StringBuilder();
            boolean tooLong = false;
            for (int j = i; j < i + words; j++) {
                if (tokens.get(j).value().length() > 64) { tooLong = true; break; }
                if (j != i) key.append(' ');
                key.append(tokens.get(j).value());
            }
            if (!tooLong) result.add(new Fingerprint(key.toString(), i, tokens.get(i).start(), tokens.get(i + words - 1).end()));
        }
        return List.copyOf(result);
    }

    static boolean informative(String shingle) {
        for (String phrase : BOILERPLATE) if (shingle.contains(phrase)) return false;
        Set<String> distinct = new HashSet<>();
        int content = 0;
        for (String word : shingle.split(" ")) {
            distinct.add(word);
            if (contentWord(word)) content++;
        }
        return distinct.size() >= 4 && content >= 3;
    }

    static boolean contentWord(String word) {
        return word.length() >= 3 && word.length() <= 64 && !STOP.contains(word);
    }
}
