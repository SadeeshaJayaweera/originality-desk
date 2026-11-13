package com.writingassistant.analysis;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextNormalizerTest {
    @Test
    void mapsLigaturesWhitespaceSoftHyphensAndSupplementaryCharacters() {
        String original = "😀\uFEFFThe\u00a0\t oﬃce co\u00adoperates\u200b with e\u0301lites.";
        var normalized = TextNormalizer.normalize(original);
        assertEquals("😀the office cooperates with élites.", normalized.text());
        assertEquals(List.of("the", "office", "cooperates", "with", "élites"),
                normalized.tokens().stream().map(TextNormalizer.Token::value).toList());
        var office = normalized.tokens().get(1);
        assertEquals(original.indexOf('o'), office.start());
        assertEquals("oﬃce", original.substring(office.start(), office.end()));
        var cooperate = normalized.tokens().get(2);
        assertEquals("co\u00adoperates", original.substring(cooperate.start(), cooperate.end()));
        int ffi = normalized.text().indexOf("ffi");
        var range = normalized.originalRange(ffi, ffi + 3);
        assertEquals("ﬃ", original.substring(range.start(), range.end()));
        assertEquals(0, normalized.originalStart(1));
        assertEquals(2, normalized.originalEnd(1));
        assertEquals("e\u0301lites", original.substring(normalized.tokens().get(4).start(), normalized.tokens().get(4).end()));
    }

    @Test
    void compatibilityAndCaseAreLocaleIndependent() {
        var a = TextNormalizer.normalize("ＦＵＬＬ ﬂower CAFÉ");
        var b = TextNormalizer.normalize("full flower cafe\u0301");
        assertEquals(b.text(), a.text());
        assertThrows(UnsupportedOperationException.class, () -> a.tokens().clear());
    }

    @Test
    void composesAcrossInvisiblesAndSupportsHangulJamo() {
        String original = "e\u200b\u0301 \u1100\u1161";
        var normalized = TextNormalizer.normalize(original);
        assertEquals("é 가", normalized.text());
        assertEquals("e\u200b\u0301", original.substring(normalized.tokens().get(0).start(), normalized.tokens().get(0).end()));
        assertEquals("\u1100\u1161", original.substring(normalized.tokens().get(1).start(), normalized.tokens().get(1).end()));
    }

    @Test
    void fingerprintUtilitiesKeepOriginalRangesAndDoNotHashCollide() {
        String text = "😀 one\ttwo three four ﬁve six seven eight nine";
        var fingerprints = Fingerprints.wordShingles(text, 8);
        assertEquals(2, fingerprints.size());
        assertEquals("one two three four five six seven eight", fingerprints.get(0).value());
        assertEquals("one\ttwo three four ﬁve six seven eight", text.substring(fingerprints.get(0).start(), fingerprints.get(0).end()));
        assertEquals(1, fingerprints.get(1).tokenIndex());
        assertThrows(IllegalArgumentException.class, () -> Fingerprints.wordShingles(text, 0));
        assertThrows(IllegalArgumentException.class, () -> Fingerprints.wordShingles(text, 65));
    }

    @Test
    void emptyInvisibleAndOversizeInputsHaveExplicitBehavior() {
        assertTrue(TextNormalizer.normalize("\u200b\u00ad\uFEFF").tokens().isEmpty());
        assertEquals("", TextNormalizer.normalize("").text());
        assertThrows(IllegalArgumentException.class, () -> TextNormalizer.normalize("x".repeat(200_001)));
        assertThrows(IllegalArgumentException.class, () -> TextNormalizer.normalize("a").originalRange(0, 0));
    }
}
