package com.writingassistant.analysis;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EvidenceEngineTest {
    private final EvidenceEngine engine = new EvidenceEngine();
    private static final String WORDS = "alpha beta gamma delta epsilon zeta theta iota kappa lambda mu nu";

    @Test
    void exactUnicodeEvidenceReturnsActualOriginalPassagesAndPageOffsets() {
        String passage = "The oﬃce co\u00adoperates\u200b with elite\u00a0research teams across regions today";
        String text = "Intro 😀\f“" + passage + "” (Jones, 2024).";
        String originalSource = "Preface: the office cooperates with elite research teams across regions today. Epilogue.";
        var result = analyze(text, "STANDARD", source("unicode-source", originalSource));
        var match = result.matches().stream().filter(m -> m.method().equals("EXACT")).findFirst().orElseThrow();
        assertEquals(text.indexOf("The"), match.start());
        assertEquals(text.indexOf("today") + 5, match.end());
        assertEquals(passage, match.submittedPassage());
        assertEquals(originalSource.indexOf("the office"), match.sourceStart());
        assertEquals("the office cooperates with elite research teams across regions today", match.sourcePassage());
        assertEquals(2, match.page());
        assertTrue(match.citationObservation().contains("Quotation and nearby citation"));
        assertEvidenceCoordinates(result, text, List.of(source("unicode-source", originalSource)));
    }

    @Test
    void compatibilityCharactersExpandingToMultipleWordsRetainValidEvidenceRanges() {
        String submitted = "\uFDFA \uFDFA";
        String corpus = "صلى الله عليه وسلم صلى الله عليه وسلم";
        var result = analyze(submitted, "STANDARD", source("arabic", corpus));
        assertEquals(8, result.wordCount());
        assertEquals(1, result.exactOverlap());
        assertEvidenceCoordinates(result, submitted, List.of(source("arabic", corpus)));
    }

    @Test
    void quickStandardAndReferenceAwareHaveRealDistinctThresholds() {
        String six = "alpha beta gamma delta epsilon zeta";
        String eight = six + " theta iota";
        assertTrue(analyze(six, "QUICK", source("s", six)).matches().isEmpty());
        assertTrue(analyze(eight, "QUICK", source("s", eight)).matches().isEmpty());
        assertEquals(0, analyze(six, "STANDARD", source("s", six)).exactOverlap());
        assertEquals(1, analyze(eight, "STANDARD", source("s", eight)).exactOverlap());
        assertEquals(1, analyze(six, "REFERENCE_AWARE", source("s", six)).exactOverlap());
        var quick = analyze(WORDS, " quick ", source("s", WORDS));
        assertEquals("QUICK", quick.profile());
        assertEquals(1, quick.exactOverlap());
        assertEquals(0, quick.nearOverlap());
        assertTrue(quick.matches().stream().allMatch(m -> m.method().equals("EXACT")));
    }

    @Test
    void unionsDuplicateAndOverlappingSourcesInsteadOfAddingTheirCoverage() {
        String text = WORDS + " omicron pi rho sigma";
        String first = "alpha beta gamma delta epsilon zeta theta iota kappa lambda";
        String second = "epsilon zeta theta iota kappa lambda mu nu omicron pi rho sigma";
        var result = analyze(text, "STANDARD", source("a", first), source("b", second));
        assertEquals(1, result.exactOverlap());
        assertEquals(0, result.nearOverlap());
        assertEquals(1, result.totalOverlap());
        assertEquals(Set.of("a", "b"), result.matches().stream().map(EvidenceEngine.Match::sourceId).collect(Collectors.toSet()));
        assertTrue(result.matches().size() >= 2);
    }

    @Test
    void fractionsAreWordBasedNotCharacterBased() {
        String matched = "alpha beta gamma delta epsilon zeta theta iota";
        String text = matched + " extraordinaryabcdefghij tiny";
        var result = analyze(text, "STANDARD", source("s", matched));
        assertEquals(10, result.wordCount());
        assertEquals(0.8, result.exactOverlap(), 1e-12);
    }

    @Test
    void nearIsIndexedLexicalJaccardNotSemanticOrAiScoring() {
        String submitted = "Researchers measured coastal rainfall using calibrated sensors during winter storms across northern valleys.";
        String corpus = "During winter storms researchers measured rainfall across northern coastal valleys using calibrated sensors.";
        var result = analyze(submitted, "STANDARD", source("weather", corpus));
        assertEquals(0, result.exactOverlap());
        assertTrue(result.nearOverlap() > 0.9);
        var near = result.matches().stream().filter(m -> m.method().equals("LEXICAL_JACCARD")).findFirst().orElseThrow();
        assertEquals(1, near.value());
        assertEquals("NOT_IMPLEMENTED", result.semanticStatus());
        assertEquals("NOT_IMPLEMENTED", result.aiWritingStatus());
        assertTrue(analyze(submitted, "QUICK", source("weather", corpus)).matches().isEmpty());
        assertEvidenceCoordinates(result, submitted, List.of(source("weather", corpus)));
    }

    @Test
    void nearJaccardValueReflectsChangedTermsAndNotJustReordering() {
        String submitted = "Researchers measured coastal rainfall using calibrated sensors during winter storms across northern valleys.";
        String corpus = "During winter storms researchers recorded rainfall across southern coastal valleys using calibrated sensors.";
        var result = analyze(submitted, "STANDARD", source("s", corpus));
        var near = result.matches().stream().filter(m -> m.method().equals("LEXICAL_JACCARD")).findFirst().orElseThrow();
        assertEquals(11.0 / 15.0, near.value(), 1e-12);
        assertEquals(0, result.exactOverlap());
    }

    @Test
    void exactAndNearUnionCountsNearOnlyBeyondExactEvidence() {
        String shared = "alpha beta gamma delta epsilon zeta theta iota";
        String text = shared + " kappa lambda omicron sigma";
        String corpus = shared + " sigma omicron lambda kappa";
        var result = analyze(text, "STANDARD", source("s", corpus));
        assertTrue(result.exactOverlap() > 0);
        assertTrue(result.nearOverlap() > 0);
        assertEquals(1, result.totalOverlap());
        assertEquals(result.exactOverlap() + result.nearOverlap(), result.totalOverlap(), 1e-12);
    }

    @Test
    void unrelatedSourcesAndVagueSynonymsProduceNoEvidence() {
        String submission = "Marine biologists carefully surveyed coral reefs beside tropical islands during summer expeditions.";
        var result = analyze(submission, "STANDARD", source("s", "Software engineers implemented immutable database transactions with deterministic event logging and durable snapshots."));
        assertTrue(result.matches().isEmpty());
        assertEquals(0, result.totalOverlap());
        assertTrue(result.citations().isEmpty());
    }

    @Test
    void citationQuoteAndReferenceObservationsAreUncertainAndMapped() {
        String text = "😀 “" + WORDS + "” (Smith & Jones, 2023), [2–4]. Brown (2021) agrees.\nReferences\nSmith, A. (2023). Title.";
        var result = analyze(text, "STANDARD", source("s", WORDS));
        assertTrue(result.citations().stream().allMatch(EvidenceEngine.Citation::uncertain));
        assertTrue(result.citations().stream().anyMatch(c -> c.kind().equals("AUTHOR_YEAR")));
        assertTrue(result.citations().stream().anyMatch(c -> c.kind().equals("NUMERIC")));
        assertTrue(result.citations().stream().anyMatch(c -> c.kind().equals("QUOTATION")));
        assertTrue(result.citations().stream().anyMatch(c -> c.kind().equals("REFERENCE_LIST")));
        for (var citation : result.citations()) assertEquals(citation.text(), text.substring(citation.start(), citation.end()));
        assertTrue(result.matches().get(0).citationObservation().contains("not validated"));
        assertTrue(result.totalOverlap() > 0, "Citation patterns must not automatically exempt matched prose");
    }

    @Test
    void referenceAwareExcludesTrailingReferencesFromEvidenceAndDenominator() {
        String text = WORDS + "\nReferences\nUnrelated bibliography material contains many additional exclusive words";
        var standard = analyze(text, "STANDARD", source("s", WORDS));
        var aware = analyze(text, "REFERENCE_AWARE", source("s", WORDS));
        assertTrue(standard.totalOverlap() < 1);
        assertEquals(1, aware.exactOverlap());
        assertEquals(standard.wordCount(), aware.wordCount());
        assertTrue(aware.limitations().stream().anyMatch(s -> s.contains("denominator")));
        String referenceOnly = "Unrelated submission heading\nWorks Cited\n" + WORDS;
        assertTrue(analyze(referenceOnly, "REFERENCE_AWARE", source("s", WORDS)).matches().isEmpty());
        assertTrue(analyze(WORDS, "REFERENCE_AWARE", source("s", "Preface text\nBibliography\n" + WORDS)).matches().isEmpty());
    }

    @Test
    void sourceIdentityAndTypeAreNotInferredFromTitleOrOrder() {
        var first = new EvidenceEngine.Source("stable-a", "Same title", WORDS, "UPLOAD");
        var second = new EvidenceEngine.Source("stable-b", "Same title", WORDS, "LIBRARY");
        var result = analyze(WORDS, "STANDARD", first, second);
        assertEquals(Set.of("stable-a", "stable-b"), result.matches().stream().map(EvidenceEngine.Match::sourceId).collect(Collectors.toSet()));
        assertEquals(Set.of("UPLOAD", "LIBRARY"), result.matches().stream().map(EvidenceEngine.Match::sourceType).collect(Collectors.toSet()));
        assertEquals(1, result.totalOverlap());
        assertThrows(IllegalArgumentException.class, () -> analyze(WORDS, "STANDARD", first, first));
        var self = analyze(WORDS, "STANDARD", source("document", WORDS));
        assertTrue(self.matches().isEmpty());
        assertTrue(self.limitations().stream().anyMatch(s -> s.contains("self-matching")));
    }

    @Test
    void lowInformationAndKnownCommonBoilerplateAreSuppressed() {
        for (String text : List.of("the and of in the and of in the and of in", "echo echo echo echo echo echo echo echo echo echo echo echo",
                "All rights reserved no part of this publication may be reproduced without permission")) {
            assertTrue(analyze(text, "STANDARD", source("s", text)).matches().isEmpty(), text);
        }
        var common = analyze(WORDS, "STANDARD", source("a", WORDS), source("b", WORDS), source("c", WORDS));
        assertTrue(common.matches().isEmpty());
        assertTrue(common.limitations().stream().anyMatch(s -> s.contains("possible boilerplate")));
    }

    @Test
    void emptySubmissionAndEmptyCorpusRemainFiniteAndImmutable() {
        var result = analyze("", "STANDARD");
        assertEquals(0, result.wordCount());
        assertEquals(0, result.totalOverlap());
        assertThrows(UnsupportedOperationException.class, () -> result.matches().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.citations().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.limitations().clear());
        assertEquals(0, analyze(WORDS, "STANDARD").totalOverlap());
    }

    @Test
    void validatesProfileSourceCountsAndEvidenceLengths() {
        assertThrows(IllegalArgumentException.class, () -> analyze(WORDS, "imaginary"));
        assertThrows(IllegalArgumentException.class, () -> analyze(WORDS, null));
        assertThrows(IllegalArgumentException.class, () -> analyze("x".repeat(200_001), "STANDARD"));
        List<EvidenceEngine.Source> sources = new ArrayList<>();
        for (int i = 0; i < 51; i++) sources.add(source("s" + i, WORDS));
        assertThrows(IllegalArgumentException.class, () -> engine.analyze("document", "Title", WORDS, sources, "QUICK"));
        sources.clear();
        for (int i = 0; i < 6; i++) sources.add(source("s" + i, "x".repeat(200_000)));
        assertThrows(IllegalArgumentException.class, () -> engine.analyze("document", "Title", WORDS, sources, "QUICK"));
        assertThrows(IllegalArgumentException.class, () -> engine.analyze("document", "Title", WORDS, null, "STANDARD"));
    }

    @Test
    void citationsAndRepetitiveEvidenceAreBounded() {
        var result = assertTimeout(Duration.ofSeconds(10), () -> analyze((WORDS + ".\n").repeat(500), "STANDARD", source("s", (WORDS + ".\n").repeat(500))));
        assertTrue(result.matches().size() <= 200);
        for (var match : result.matches()) {
            assertTrue(match.submittedPassage().length() <= 2_000);
            assertTrue(match.sourcePassage().length() <= 2_000);
        }
        assertTrue(result.totalOverlap() <= 1);
        var citations = analyze("[1] ".repeat(400), "QUICK");
        assertEquals(300, citations.citations().size());
        assertTrue(citations.limitations().stream().anyMatch(s -> s.contains("Citation observations capped")));
    }

    private EvidenceEngine.Result analyze(String text, String profile, EvidenceEngine.Source... sources) {
        return engine.analyze("document", "Submitted title", text, List.of(sources), profile);
    }
    private static EvidenceEngine.Source source(String id, String text) {
        return new EvidenceEngine.Source(id, "Title " + id, text, "LOCAL");
    }
    private static void assertEvidenceCoordinates(EvidenceEngine.Result result, String text, List<EvidenceEngine.Source> sources) {
        for (var match : result.matches()) {
            assertEquals(match.submittedPassage(), text.substring(match.start(), match.end()));
            var source = sources.stream().filter(s -> s.id().equals(match.sourceId())).findFirst().orElseThrow();
            assertEquals(match.sourcePassage(), source.text().substring(match.sourceStart(), match.sourceEnd()));
            assertTrue(match.start() < match.end());
            assertTrue(match.sourceStart() < match.sourceEnd());
        }
    }
}
