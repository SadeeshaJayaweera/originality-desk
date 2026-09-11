package com.writingassistant.core.rewrite;

import java.util.EnumMap;
import java.util.Map;

/**
 * Centralized, tunable prompt templates and system instructions for style rewriting modes.
 */
public final class RewritePromptTemplates {

    public static final String SYSTEM_INSTRUCTION =
            "You are an expert, non-destructive writing editor assisting an author with their OWN original writing. " +
            "Your objective is to enhance the phrasing according to the requested editing mode while strictly " +
            "preserving the author's intended meaning, argument, and core facts. Never alter the author's original thesis " +
            "or introduce unstated claims.";

    private static final String COMMON_OUTPUT_CONTRACT =
            "FORMATTING INSTRUCTIONS:\n" +
            "- Provide 2 to 3 high-quality alternative phrasings.\n" +
            "- Output EACH alternative on a new line prefixed with a number (e.g., '1. ...', '2. ...', '3. ...').\n" +
            "- Do NOT output any conversational preamble, commentary, greetings, or explanations.\n" +
            "- Output ONLY the numbered alternatives.";

    private static final Map<RewriteMode, String> MODE_PROMPT_TEMPLATES = new EnumMap<>(RewriteMode.class);

    static {
        MODE_PROMPT_TEMPLATES.put(RewriteMode.TIGHTEN,
                "MODE: TIGHTEN (Conciseness & Punchiness)\n" +
                "GUIDELINES:\n" +
                "- Cut wordiness, redundant modifiers, filler phrases, and unnecessary nominalizations.\n" +
                "- Strengthen weak verbs and tighten prepositional clutter.\n" +
                "- Preserve all factual information, key nuances, and the author's core message.\n\n" +
                COMMON_OUTPUT_CONTRACT + "\n\n" +
                "ORIGINAL TEXT:\n\"%s\"");

        MODE_PROMPT_TEMPLATES.put(RewriteMode.CLARITY,
                "MODE: CLARITY (Flow & Awkward Phrasing Fix)\n" +
                "GUIDELINES:\n" +
                "- Untangle confusing, run-on, or convoluted sentence structures.\n" +
                "- Improve natural rhythm, coherence, and syntax for seamless readability (vital for ESL writers).\n" +
                "- IMPORTANT: Preserve the writer's intended meaning and argument exactly; do NOT change their logic.\n\n" +
                COMMON_OUTPUT_CONTRACT + "\n\n" +
                "ORIGINAL TEXT:\n\"%s\"");

        MODE_PROMPT_TEMPLATES.put(RewriteMode.FORMAL,
                "MODE: FORMAL (Academic & Professional Tone Shift)\n" +
                "GUIDELINES:\n" +
                "- Elevate vocabulary and syntax for academic, professional, and institutional contexts.\n" +
                "- Eliminate casual slang, informal contractions, and colloquial phrasing.\n" +
                "- Maintain an authoritative, objective, and scholarly voice while preserving core content.\n\n" +
                COMMON_OUTPUT_CONTRACT + "\n\n" +
                "ORIGINAL TEXT:\n\"%s\"");

        MODE_PROMPT_TEMPLATES.put(RewriteMode.CASUAL,
                "MODE: CASUAL (Conversational & Natural Tone Shift)\n" +
                "GUIDELINES:\n" +
                "- Shift phrasing to a conversational, friendly, and accessible tone.\n" +
                "- Use natural phrasing, everyday idioms, and standard contractions where appropriate.\n" +
                "- Keep the writing direct, approachable, and engaging without losing substance.\n\n" +
                COMMON_OUTPUT_CONTRACT + "\n\n" +
                "ORIGINAL TEXT:\n\"%s\"");

        MODE_PROMPT_TEMPLATES.put(RewriteMode.GRAMMAR_FIX,
                "MODE: GRAMMAR_FIX (Grammar & Syntax Fix)\n" +
                "GUIDELINES:\n" +
                "- Fix all grammatical errors, spelling mistakes, punctuation faults, and subject-verb mismatches.\n" +
                "- Correct misplaced or dangling modifiers and tense inconsistencies.\n" +
                "- CRITICAL: Do NOT alter the author's stylistic voice or tone; make only necessary grammatical corrections.\n\n" +
                COMMON_OUTPUT_CONTRACT + "\n\n" +
                "ORIGINAL TEXT:\n\"%s\"");
    }

    private RewritePromptTemplates() {
    }

    /**
     * Builds the complete prompt for a given text and rewrite mode.
     *
     * @param originalText Text passage to rewrite
     * @param mode         Target rewriting mode
     * @return Populated prompt string
     */
    public static String buildPrompt(String originalText, RewriteMode mode) {
        if (originalText == null) {
            originalText = "";
        }
        RewriteMode targetMode = (mode != null) ? mode : RewriteMode.CLARITY;
        String template = MODE_PROMPT_TEMPLATES.getOrDefault(targetMode, MODE_PROMPT_TEMPLATES.get(RewriteMode.CLARITY));
        return String.format(template, originalText.trim());
    }

    /**
     * Returns the system instruction for rewrite operations.
     */
    public static String getSystemInstruction() {
        return SYSTEM_INSTRUCTION;
    }
}
