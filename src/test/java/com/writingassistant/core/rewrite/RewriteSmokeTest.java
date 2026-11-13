package com.writingassistant.core.rewrite;

import com.writingassistant.ai.AiResponse;
import com.writingassistant.ai.GeminiClient;
import com.writingassistant.config.ConfigManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RewriteSmokeTest {

    @Test
    @DisplayName("Smoke Test: Evaluates real sample sentences when GEMINI_API_KEY is configured")
    void testLiveSampleSentenceQuality() throws Exception {
        assumeTrue(Boolean.getBoolean("writingassistant.liveTests"),
                "Live Gemini calls require -Dwritingassistant.liveTests=true.");
        String effectiveKey = ConfigManager.getInstance().getEffectiveApiKey();
        assumeTrue(effectiveKey != null && !effectiveKey.isBlank(),
                "Skipping live smoke test because no GEMINI_API_KEY is configured in environment/config.");

        GeminiClient client = new GeminiClient(ConfigManager.getInstance());
        DefaultRewriteEngine engine = new DefaultRewriteEngine(client);

        Map<RewriteMode, String> samples = new LinkedHashMap<>();
        samples.put(RewriteMode.TIGHTEN,
                "Due to the fact that there was an unexpected occurrence of rain during the afternoon hours, the outdoor athletic competition was postponed until a later date.");
        samples.put(RewriteMode.CLARITY,
                "The reason why the results were not giving what we expected was because the temperature was not kept at the same level throughout the experiment which caused error.");
        samples.put(RewriteMode.FORMAL,
                "Hey folks, we gotta fix this issue ASAP because users are getting super annoyed.");
        samples.put(RewriteMode.CASUAL,
                "Notwithstanding the aforementioned considerations, the implementation shall proceed forthwith.");
        samples.put(RewriteMode.GRAMMAR_FIX,
                "Each of the students have submitted their assignment on yesterday.");

        for (Map.Entry<RewriteMode, String> entry : samples.entrySet()) {
            RewriteMode mode = entry.getKey();
            String sentence = entry.getValue();

            RewriteResult result = engine.rewrite(sentence, mode);
            assertNotNull(result, "Result should not be null for mode: " + mode);
            assertFalse(result.getAlternatives().isEmpty(), "Alternatives should not be empty for mode: " + mode);

            System.out.println("=== Live Smoke Test Result [" + mode.getDisplayName() + "] ===");
            System.out.println("Original: " + sentence);
            int idx = 1;
            for (String alt : result.getAlternatives()) {
                System.out.println("  " + idx++ + ". " + alt);
                assertFalse(alt.isBlank(), "Alternative should not be blank");
            }
            System.out.println();
        }
    }

    @Test
    @DisplayName("Simulated Quality Smoke Test: Validates prompt construction and parsing for all 5 sample sentences")
    void testSimulatedSampleSentenceQuality() {
        Map<RewriteMode, String> samples = new LinkedHashMap<>();
        samples.put(RewriteMode.TIGHTEN,
                "Due to the fact that there was an unexpected occurrence of rain during the afternoon hours, the outdoor athletic competition was postponed until a later date.");
        samples.put(RewriteMode.CLARITY,
                "The reason why the results were not giving what we expected was because the temperature was not kept at the same level throughout the experiment which caused error.");
        samples.put(RewriteMode.FORMAL,
                "Hey folks, we gotta fix this issue ASAP because users are getting super annoyed.");
        samples.put(RewriteMode.CASUAL,
                "Notwithstanding the aforementioned considerations, the implementation shall proceed forthwith.");
        samples.put(RewriteMode.GRAMMAR_FIX,
                "Each of the students have submitted their assignment on yesterday.");

        Map<RewriteMode, String> simulatedResponses = Map.of(
                RewriteMode.TIGHTEN, "1. Because of unexpected afternoon rain, the outdoor athletic competition was postponed.\n2. Unexpected afternoon rain forced the postponement of the outdoor athletic match.\n3. The outdoor match was postponed due to unexpected afternoon rain.",
                RewriteMode.CLARITY, "1. Inconsistent temperatures throughout the experiment caused the unexpected results.\n2. The unexpected results occurred because the temperature fluctuated during the experiment.\n3. Temperature variations during the experiment introduced experimental error.",
                RewriteMode.FORMAL, "1. It is imperative that this defect be resolved immediately to address growing user dissatisfaction.\n2. Prompt resolution of this issue is required to prevent further user disruption.",
                RewriteMode.CASUAL, "1. Despite all that, let's go ahead and get started right now.\n2. Regardless of those points, we're ready to roll right away.",
                RewriteMode.GRAMMAR_FIX, "1. Each of the students submitted their assignment yesterday.\n2. Each of the students has submitted their assignment yesterday."
        );

        for (Map.Entry<RewriteMode, String> entry : samples.entrySet()) {
            RewriteMode mode = entry.getKey();
            String original = entry.getValue();

            // Verify prompt generation
            String prompt = RewritePromptTemplates.buildPrompt(original, mode);
            assertTrue(prompt.contains(original));
            assertTrue(prompt.contains(mode.name()));

            // Verify response parsing
            String simulatedAiOutput = simulatedResponses.get(mode);
            List<String> parsed = RewriteResponseParser.parseAlternatives(simulatedAiOutput, original);
            assertTrue(parsed.size() >= 2, "Expected at least 2 alternatives for mode: " + mode);

            RewriteResult result = new RewriteResult(original, mode, parsed);
            assertEquals(original, result.getOriginalText());
            assertEquals(mode, result.getMode());
            assertEquals(parsed.size(), result.getAlternatives().size());
            assertEquals(parsed.size(), result.getOptions().size());
        }
    }
}
