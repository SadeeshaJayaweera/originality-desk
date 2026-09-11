package com.writingassistant.ai;

/**
 * Thrown when an AI-dependent operation is requested but no API key is configured.
 */
public class AiFeatureUnavailableException extends GeminiException {

    public AiFeatureUnavailableException(String message) {
        super(message);
    }
}
