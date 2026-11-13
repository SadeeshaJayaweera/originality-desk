package com.writingassistant.core.similarity;

/**
 * Configuration options, weights, and thresholds for local similarity analysis.
 */
public class SimilarityConfig {

    private double weightNGram = 0.35;
    private double weightLcs = 0.40;
    private double weightLevenshtein = 0.25;

    private double moderateThreshold = 25.0;
    private double highThreshold = 55.0;

    private int minNGramSize = 2;
    private int maxNGramSize = 4;
    private int minSpanWords = 3;

    public SimilarityConfig() {
    }

    public SimilarityConfig(double weightNGram, double weightLcs, double weightLevenshtein,
                            double moderateThreshold, double highThreshold) {
        this.weightNGram = weightNGram;
        this.weightLcs = weightLcs;
        this.weightLevenshtein = weightLevenshtein;
        this.moderateThreshold = moderateThreshold;
        this.highThreshold = highThreshold;
        validate();
    }

    public void validate() {
        validateWeight(weightNGram);
        validateWeight(weightLcs);
        validateWeight(weightLevenshtein);
        if (Math.abs(weightNGram + weightLcs + weightLevenshtein - 1.0) > 1e-9) {
            throw new IllegalArgumentException("Similarity weights must sum to 1.");
        }
        validateThresholds(moderateThreshold, highThreshold);
        validateNGrams(minNGramSize, maxNGramSize);
        validateSpanWords(minSpanWords);
    }

    private static void validateWeight(double weight) {
        if (!Double.isFinite(weight) || weight < 0.0 || weight > 1.0) {
            throw new IllegalArgumentException("Similarity weights must be finite and between 0 and 1.");
        }
    }

    private static void validateThresholds(double moderate, double high) {
        if (!Double.isFinite(moderate) || !Double.isFinite(high)
                || moderate < 0.0 || high > 100.0 || moderate >= high) {
            throw new IllegalArgumentException("Similarity thresholds must satisfy 0 <= moderate < high <= 100.");
        }
    }

    private static void validateNGrams(int min, int max) {
        if (min < 1 || min > max || max > 16) {
            throw new IllegalArgumentException("N-gram sizes must satisfy 1 <= minimum <= maximum <= 16.");
        }
    }

    private static void validateSpanWords(int words) {
        if (words < 1 || words > 100) {
            throw new IllegalArgumentException("Minimum span length must be between 1 and 100 words.");
        }
    }

    public static SimilarityConfig defaultConfig() {
        return new SimilarityConfig();
    }

    public double getWeightNGram() {
        return weightNGram;
    }

    public void setWeightNGram(double weightNGram) {
        validateWeight(weightNGram);
        this.weightNGram = weightNGram;
    }

    public double getWeightLcs() {
        return weightLcs;
    }

    public void setWeightLcs(double weightLcs) {
        validateWeight(weightLcs);
        this.weightLcs = weightLcs;
    }

    public double getWeightLevenshtein() {
        return weightLevenshtein;
    }

    public void setWeightLevenshtein(double weightLevenshtein) {
        validateWeight(weightLevenshtein);
        this.weightLevenshtein = weightLevenshtein;
    }

    public double getModerateThreshold() {
        return moderateThreshold;
    }

    public void setModerateThreshold(double moderateThreshold) {
        validateThresholds(moderateThreshold, highThreshold);
        this.moderateThreshold = moderateThreshold;
    }

    public double getHighThreshold() {
        return highThreshold;
    }

    public void setHighThreshold(double highThreshold) {
        validateThresholds(moderateThreshold, highThreshold);
        this.highThreshold = highThreshold;
    }

    public int getMinNGramSize() {
        return minNGramSize;
    }

    public void setMinNGramSize(int minNGramSize) {
        validateNGrams(minNGramSize, maxNGramSize);
        this.minNGramSize = minNGramSize;
    }

    public int getMaxNGramSize() {
        return maxNGramSize;
    }

    public void setMaxNGramSize(int maxNGramSize) {
        validateNGrams(minNGramSize, maxNGramSize);
        this.maxNGramSize = maxNGramSize;
    }

    public int getMinSpanWords() {
        return minSpanWords;
    }

    public void setMinSpanWords(int minSpanWords) {
        validateSpanWords(minSpanWords);
        this.minSpanWords = minSpanWords;
    }
}
