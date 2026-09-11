package com.writingassistant.core.rewrite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RewriteResponseParserTest {

    @Test
    @DisplayName("Parses standard numbered responses (1., 2., 3.)")
    void testParseNumberedResponse() {
        String canned = """
                1. Although it rained in the afternoon, the athletic competition proceeded as planned.
                2. Despite unexpected afternoon rain, the outdoor athletic competition was not cancelled.
                3. The outdoor athletic match continued on schedule despite afternoon showers.
                """;

        List<String> alternatives = RewriteResponseParser.parseAlternatives(canned, "original");
        assertEquals(3, alternatives.size());
        assertEquals("Although it rained in the afternoon, the athletic competition proceeded as planned.", alternatives.get(0));
        assertEquals("Despite unexpected afternoon rain, the outdoor athletic competition was not cancelled.", alternatives.get(1));
        assertEquals("The outdoor athletic match continued on schedule despite afternoon showers.", alternatives.get(2));
    }

    @Test
    @DisplayName("Parses responses with conversational preamble and numbered items")
    void testParseResponseWithPreamble() {
        String canned = """
                Here are three alternative phrasings for your sentence:
                
                1. We should fix this issue immediately to avoid user dissatisfaction.
                2. Addressing this defect right away will prevent further user frustration.
                3. Let's resolve this problem promptly since users are encountering issues.
                """;

        List<String> alternatives = RewriteResponseParser.parseAlternatives(canned, "original");
        assertEquals(3, alternatives.size());
        assertEquals("We should fix this issue immediately to avoid user dissatisfaction.", alternatives.get(0));
        assertFalse(alternatives.contains("Here are three alternative phrasings for your sentence:"));
    }

    @Test
    @DisplayName("Parses bullet point format and stripped quotes")
    void testParseBulletPointsWithQuotes() {
        String canned = """
                - "First concise alternative without filler."
                - "Second punchy version preserving all meaning."
                - "Third streamlined option."
                """;

        List<String> alternatives = RewriteResponseParser.parseAlternatives(canned, "original");
        assertEquals(3, alternatives.size());
        assertEquals("First concise alternative without filler.", alternatives.get(0));
        assertEquals("Second punchy version preserving all meaning.", alternatives.get(1));
        assertEquals("Third streamlined option.", alternatives.get(2));
    }

    @Test
    @DisplayName("Parses markdown bold prefixes (**Option 1:**)")
    void testParseMarkdownBoldOptions() {
        String canned = """
                **Option 1:** Elevated academic syntax for publication.
                **Option 2:** Scholarly phrasing suitable for research papers.
                """;

        List<String> alternatives = RewriteResponseParser.parseAlternatives(canned, "original");
        assertEquals(2, alternatives.size());
        assertEquals("Elevated academic syntax for publication.", alternatives.get(0));
        assertEquals("Scholarly phrasing suitable for research papers.", alternatives.get(1));
    }

    @Test
    @DisplayName("Gracefully handles malformed un-numbered single block of text")
    void testParseMalformedSingleBlock() {
        String malformed = "This is a single unformatted sentence returned by an edge-case model response.";

        List<String> alternatives = RewriteResponseParser.parseAlternatives(malformed, "original");
        assertNotNull(alternatives);
        assertEquals(1, alternatives.size());
        assertEquals("This is a single unformatted sentence returned by an edge-case model response.", alternatives.get(0));
    }

    @Test
    @DisplayName("Gracefully falls back to original text when response is null or blank")
    void testParseNullOrBlankResponse() {
        String original = "Original sentence before failure.";

        List<String> fromNull = RewriteResponseParser.parseAlternatives(null, original);
        assertEquals(1, fromNull.size());
        assertEquals(original, fromNull.get(0));

        List<String> fromBlank = RewriteResponseParser.parseAlternatives("   \n\n  ", original);
        assertEquals(1, fromBlank.size());
        assertEquals(original, fromBlank.get(0));
    }
}
