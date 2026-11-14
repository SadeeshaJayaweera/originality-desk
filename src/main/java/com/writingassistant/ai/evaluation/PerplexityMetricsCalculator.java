package com.writingassistant.ai.evaluation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Statistical calculator for empirical lexical entropy, sentence length burstiness,
 * and vocabulary diversity metrics.
 */
public final class PerplexityMetricsCalculator {

    private static final Pattern WORD_PATTERN = Pattern.compile("[\\p{L}\\p{N}']+");
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("[.!?]+(?:\\s+|$)");

    private PerplexityMetricsCalculator() {}

    /**
     * Computes stylometry metrics for an input text.
     */
    public static StylometryMetrics calculate(String text) {
        if (text == null || text.isBlank()) {
            return new StylometryMetrics(0.0, 0.0, 0.0, 0.0, 0.0, 0, 0);
        }

        // 1. Tokenize into words
        List<String> words = new ArrayList<>();
        Matcher m = WORD_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        Map<String, Integer> wordFrequencies = new HashMap<>();

        while (m.find()) {
            String word = m.group();
            words.add(word);
            wordFrequencies.merge(word, 1, Integer::sum);
        }

        int totalWords = words.size();
        if (totalWords == 0) {
            return new StylometryMetrics(0.0, 0.0, 0.0, 0.0, 0.0, 0, 0);
        }

        // 2. Vocabulary Richness (TTR and Hapax Legomena)
        int uniqueWords = wordFrequencies.size();
        double ttr = (double) uniqueWords / totalWords;

        int hapaxCount = 0;
        for (int count : wordFrequencies.values()) {
            if (count == 1) hapaxCount++;
        }
        double hapaxRatio = uniqueWords > 0 ? (double) hapaxCount / uniqueWords : 0.0;

        // 3. Empirical Shannon Entropy over word distribution
        double entropy = 0.0;
        for (int count : wordFrequencies.values()) {
            double p = (double) count / totalWords;
            entropy -= p * (Math.log(p) / Math.log(2));
        }

        // 4. Sentence Length Statistics and Burstiness
        String[] sentenceTexts = SENTENCE_SPLIT.split(text);
        List<Integer> sentenceLengths = new ArrayList<>();

        for (String s : sentenceTexts) {
            String trimmed = s.strip();
            if (!trimmed.isEmpty()) {
                int count = 0;
                Matcher sm = WORD_PATTERN.matcher(trimmed);
                while (sm.find()) count++;
                if (count > 0) sentenceLengths.add(count);
            }
        }

        int sentenceCount = sentenceLengths.size();
        if (sentenceCount == 0) {
            sentenceLengths.add(totalWords);
            sentenceCount = 1;
        }

        double sumLengths = 0;
        for (int len : sentenceLengths) sumLengths += len;
        double avgSentenceLength = sumLengths / sentenceCount;

        // Coefficient of variation for burstiness: stddev / mean
        double variance = 0;
        for (int len : sentenceLengths) {
            double diff = len - avgSentenceLength;
            variance += diff * diff;
        }
        variance /= sentenceCount;
        double stddev = Math.sqrt(variance);
        double burstiness = avgSentenceLength > 0 ? stddev / avgSentenceLength : 0.0;

        return new StylometryMetrics(
                entropy,
                burstiness,
                ttr,
                hapaxRatio,
                avgSentenceLength,
                sentenceCount,
                totalWords
        );
    }
}
