package com.writingassistant.ai;

/**
 * Encapsulates response text and metadata returned by AI generation.
 */
public class AiResponse {

    private final String content;
    private final String model;
    private final boolean successful;
    private final String errorMessage;

    public AiResponse(String content, String model) {
        this.content = content;
        this.model = model;
        this.successful = true;
        this.errorMessage = null;
    }

    public AiResponse(String errorMessage) {
        this.content = null;
        this.model = null;
        this.successful = false;
        this.errorMessage = errorMessage;
    }

    public static AiResponse success(String content, String model) {
        return new AiResponse(content, model);
    }

    public static AiResponse failure(String errorMessage) {
        return new AiResponse(errorMessage);
    }

    public String getContent() {
        return content;
    }

    public String getModel() {
        return model;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
