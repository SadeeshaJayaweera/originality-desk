package com.writingassistant.config;

/**
 * Data model for persistent application configuration settings.
 */
public class AppConfig {

    private String geminiApiKey;
    private String geminiModel = "gemini-1.5-flash";
    private String defaultCitationStyle = "APA";
    private String defaultRewriteMode = "CLARITY";
    private String logLevel = "INFO";

    // Power-user tunable similarity thresholds & weights
    private double similarityModerateThreshold = 25.0;
    private double similarityHighThreshold = 55.0;
    private double similarityWeightNGram = 0.35;
    private double similarityWeightLcs = 0.40;
    private double similarityWeightLevenshtein = 0.25;

    public AppConfig() {
    }

    public AppConfig(String geminiApiKey, String geminiModel, String defaultCitationStyle) {
        this.geminiApiKey = geminiApiKey;
        if (geminiModel != null && !geminiModel.isBlank()) {
            this.geminiModel = geminiModel;
        }
        if (defaultCitationStyle != null && !defaultCitationStyle.isBlank()) {
            this.defaultCitationStyle = defaultCitationStyle;
        }
    }

    public String getGeminiApiKey() {
        return geminiApiKey;
    }

    public void setGeminiApiKey(String geminiApiKey) {
        this.geminiApiKey = geminiApiKey;
    }

    public String getGeminiModel() {
        return (geminiModel != null && !geminiModel.isBlank()) ? geminiModel : "gemini-1.5-flash";
    }

    public void setGeminiModel(String geminiModel) {
        this.geminiModel = geminiModel;
    }

    public String getDefaultCitationStyle() {
        return (defaultCitationStyle != null && !defaultCitationStyle.isBlank()) ? defaultCitationStyle : "APA";
    }

    public void setDefaultCitationStyle(String defaultCitationStyle) {
        this.defaultCitationStyle = defaultCitationStyle;
    }

    public String getDefaultRewriteMode() {
        return (defaultRewriteMode != null && !defaultRewriteMode.isBlank()) ? defaultRewriteMode : "CLARITY";
    }

    public void setDefaultRewriteMode(String defaultRewriteMode) {
        this.defaultRewriteMode = defaultRewriteMode;
    }

    public String getLogLevel() {
        return (logLevel != null && !logLevel.isBlank()) ? logLevel : "INFO";
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    public double getSimilarityModerateThreshold() {
        return similarityModerateThreshold;
    }

    public void setSimilarityModerateThreshold(double similarityModerateThreshold) {
        this.similarityModerateThreshold = similarityModerateThreshold;
    }

    public double getSimilarityHighThreshold() {
        return similarityHighThreshold;
    }

    public void setSimilarityHighThreshold(double similarityHighThreshold) {
        this.similarityHighThreshold = similarityHighThreshold;
    }

    public double getSimilarityWeightNGram() {
        return similarityWeightNGram;
    }

    public void setSimilarityWeightNGram(double similarityWeightNGram) {
        this.similarityWeightNGram = similarityWeightNGram;
    }

    public double getSimilarityWeightLcs() {
        return similarityWeightLcs;
    }

    public void setSimilarityWeightLcs(double similarityWeightLcs) {
        this.similarityWeightLcs = similarityWeightLcs;
    }

    public double getSimilarityWeightLevenshtein() {
        return similarityWeightLevenshtein;
    }

    public void setSimilarityWeightLevenshtein(double similarityWeightLevenshtein) {
        this.similarityWeightLevenshtein = similarityWeightLevenshtein;
    }
}
