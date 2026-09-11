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
    }

    public static SimilarityConfig defaultConfig() {
        return new SimilarityConfig();
    }

    public double getWeightNGram() {
        return weightNGram;
    }

    public void setWeightNGram(double weightNGram) {
        this.weightNGram = weightNGram;
    }

    public double getWeightLcs() {
        return weightLcs;
    }

    public void setWeightLcs(double weightLcs) {
        this.weightLcs = weightLcs;
    }

    public double getWeightLevenshtein() {
        return weightLevenshtein;
    }

    public void setWeightLevenshtein(double weightLevenshtein) {
        this.weightLevenshtein = weightLevenshtein;
    }

    public double getModerateThreshold() {
        return moderateThreshold;
    }

    public void setModerateThreshold(double moderateThreshold) {
        this.moderateThreshold = moderateThreshold;
    }

    public double getHighThreshold() {
        return highThreshold;
    }

    public void setHighThreshold(double highThreshold) {
        this.highThreshold = highThreshold;
    }

    public int getMinNGramSize() {
        return minNGramSize;
    }

    public void setMinNGramSize(int minNGramSize) {
        this.minNGramSize = minNGramSize;
    }

    public int getMaxNGramSize() {
        return maxNGramSize;
    }

    public void setMaxNGramSize(int maxNGramSize) {
        this.maxNGramSize = maxNGramSize;
    }

    public int getMinSpanWords() {
        return minSpanWords;
    }

    public void setMinSpanWords(int minSpanWords) {
        this.minSpanWords = minSpanWords;
    }
}
