package com.writingassistant.core.citation;

/**
 * Prompt templates and system instructions for ethical, citation-safe quote paraphrasing.
 */
public final class CitationPromptTemplates {

    public static final String SYSTEM_INSTRUCTION =
            "You are an expert academic writing assistant specializing in ethical, citation-safe source paraphrasing. " +
            "Your objective is to produce genuine, high-quality paraphrases of direct source quotations that express " +
            "the original ideas clearly and accurately in fresh phrasing while avoiding patchwriting or structural copying.";

    public static final String PARAPHRASE_PROMPT_TEMPLATE =
            "TASK: Paraphrase the provided source quotation.\n\n" +
            "CRITICAL INSTRUCTIONS:\n" +
            "1. Preserve the original meaning, claims, numerical figures, and factual nuances exactly.\n" +
            "2. DO NOT simply substitute synonyms while keeping the same sentence structure; restructure the sentence and reorganize ideas while preserving factual accuracy.\n" +
            "3. Eliminate distinctive, idiosyncratic phrasing or unique metaphors from the source, expressing the concepts in clean, natural prose.\n" +
            "4. Return ONLY the plain text paraphrase. Do not include quotation marks, in-text citations, commentary, bullet points, or conversational preamble.\n\n" +
            "ORIGINAL QUOTATION:\n\"%s\"";

    private CitationPromptTemplates() {
    }

    /**
     * Builds the complete prompt for paraphrasing a source quote.
     *
     * @param originalQuote Raw quoted text to paraphrase
     * @return Formatted prompt string
     */
    public static String buildPrompt(String originalQuote) {
        if (originalQuote == null) {
            originalQuote = "";
        }
        return String.format(PARAPHRASE_PROMPT_TEMPLATE, originalQuote.trim());
    }

    public static String getSystemInstruction() {
        return SYSTEM_INSTRUCTION;
    }
}
