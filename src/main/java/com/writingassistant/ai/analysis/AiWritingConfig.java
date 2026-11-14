package com.writingassistant.ai.analysis;

import java.io.Serializable;

/**
 * Explicit opt-in configuration for experimental AI-writing statistical stylometry analysis.
 * Requires mandatory acknowledgment of disparate impact and statistical uncertainty.
 */
public record AiWritingConfig(
        boolean enabled,
        boolean acknowledgeDisparateImpact,
        int minimumWordCount,
        boolean outputStatisticalProfiles
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_MINIMUM_WORDS = 150;

    public AiWritingConfig {
        if (minimumWordCount <= 0) minimumWordCount = DEFAULT_MINIMUM_WORDS;
    }

    public static AiWritingConfig disabled() {
        return new AiWritingConfig(false, false, DEFAULT_MINIMUM_WORDS, false);
    }

    public static AiWritingConfig experimental(boolean acknowledgeDisparateImpact) {
        return new AiWritingConfig(true, acknowledgeDisparateImpact, DEFAULT_MINIMUM_WORDS, true);
    }
}
