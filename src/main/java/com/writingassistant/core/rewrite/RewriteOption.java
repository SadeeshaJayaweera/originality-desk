package com.writingassistant.core.rewrite;

/**
 * Individual rewrite candidate alternative.
 */
public class RewriteOption {

    private final String text;
    private final String explanation;

    public RewriteOption(String text, String explanation) {
        this.text = text;
        this.explanation = explanation;
    }

    public String getText() {
        return text;
    }

    public String getExplanation() {
        return explanation;
    }
}
