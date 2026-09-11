package com.writingassistant.core.rewrite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class RewritePromptTemplatesTest {

    @Test
    @DisplayName("System instruction is non-empty and contains non-destructive editing directive")
    void testSystemInstructionContent() {
        String systemInstruction = RewritePromptTemplates.getSystemInstruction();
        assertNotNull(systemInstruction);
        assertFalse(systemInstruction.isBlank());
        assertTrue(systemInstruction.contains("expert, non-destructive writing editor"));
        assertTrue(systemInstruction.contains("preserving the author's intended meaning"));
    }

    @ParameterizedTest
    @EnumSource(RewriteMode.class)
    @DisplayName("Prompt builder incorporates mode guidelines and original text for all modes")
    void testBuildPromptForAllModes(RewriteMode mode) {
        String sampleText = "This is an experimental sentence that needs editing.";
        String prompt = RewritePromptTemplates.buildPrompt(sampleText, mode);

        assertNotNull(prompt);
        assertTrue(prompt.contains(sampleText), "Prompt must embed original text");
        assertTrue(prompt.contains(mode.name()), "Prompt must contain mode identifier: " + mode.name());
        assertTrue(prompt.contains("FORMATTING INSTRUCTIONS"), "Prompt must include output formatting instructions");
    }

    @Test
    @DisplayName("CLARITY mode prompt explicitly instructs preserving the writer's argument for ESL flow")
    void testClarityPromptSpecificGuidelines() {
        String sampleText = "Because the reason of low sample size, the test was failing.";
        String prompt = RewritePromptTemplates.buildPrompt(sampleText, RewriteMode.CLARITY);

        assertTrue(prompt.contains("Untangle confusing, run-on, or convoluted"));
        assertTrue(prompt.contains("vital for ESL writers"));
        assertTrue(prompt.contains("Preserve the writer's intended meaning and argument exactly"));
    }

    @Test
    @DisplayName("TIGHTEN mode prompt specifies conciseness and filler elimination")
    void testTightenPromptSpecificGuidelines() {
        String sampleText = "In spite of the fact that it was raining, we walked.";
        String prompt = RewritePromptTemplates.buildPrompt(sampleText, RewriteMode.TIGHTEN);

        assertTrue(prompt.contains("Cut wordiness, redundant modifiers, filler phrases"));
        assertTrue(prompt.contains("Strengthen weak verbs"));
    }

    @Test
    @DisplayName("GRAMMAR_FIX mode prompt emphasizes not altering author's stylistic voice")
    void testGrammarPromptSpecificGuidelines() {
        String sampleText = "The data was showing errors yesterday.";
        String prompt = RewritePromptTemplates.buildPrompt(sampleText, RewriteMode.GRAMMAR_FIX);

        assertTrue(prompt.contains("Do NOT alter the author's stylistic voice or tone"));
        assertTrue(prompt.contains("Fix all grammatical errors"));
    }
}
