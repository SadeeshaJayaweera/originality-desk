package com.writingassistant.core.rewrite;

import com.writingassistant.ai.GeminiException;

/**
 * Interface for the style rewriting engine (Phase 1).
 */
public interface RewriteEngine {

    /**
     * Rewrites input text using the specified mode and returns multiple candidate alternatives.
     *
     * @param text Original passage
     * @param mode Rewriting mode/style
     * @return RewriteResult containing generated candidates
     * @throws GeminiException if AI provider call fails or is unconfigured
     */
    RewriteResult rewrite(String text, RewriteMode mode) throws GeminiException;
}
