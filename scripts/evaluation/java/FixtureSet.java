package com.writingassistant.evaluation;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.writingassistant.analysis.DocumentExtractor;
import com.writingassistant.analysis.EvidenceEngine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class FixtureSet {
    static final Set<String> CATEGORIES = Set.of("exact", "minor_substitution", "reordered", "lexical_paraphrase",
            "quotes", "boilerplate", "bibliography", "technical", "unrelated", "short", "semantic_paraphrase");
    record Data(int schemaVersion, String name, String provenance, String labelPolicy, List<Case> cases) { }
    record Case(String id, String category, String cohort, String label, String rationale, String submittedText,
            List<EvidenceEngine.Source> sources) {
        boolean positive() { return label.equals("POSITIVE"); }
    }

    private FixtureSet() { }

    static Data load(Path file) throws IOException {
        if (Files.size(file) > 2 * 1024 * 1024) throw new IOException("Fixture manifest exceeds 2 MiB");
        Data data;
        try {
            data = new Gson().fromJson(Files.readString(file, StandardCharsets.UTF_8), Data.class);
        } catch (JsonParseException | IllegalArgumentException e) {
            throw new IOException("Invalid fixture JSON", e);
        }
        check(data != null && data.schemaVersion() == 1, "Unsupported fixture schema");
        check(data.name() != null && !data.name().isBlank(), "Fixture name required");
        check(data.provenance() != null && !data.provenance().isBlank(), "Fixture provenance required");
        check(data.labelPolicy() != null && !data.labelPolicy().isBlank(), "Label policy required");
        check(data.cases() != null && !data.cases().isEmpty(), "Cases required");
        Set<String> ids = new HashSet<>();
        Set<String> categories = new HashSet<>();
        for (Case item : data.cases()) {
            check(item != null && item.id() != null && item.id().matches("[a-z0-9-]{1,100}") && ids.add(item.id()), "Invalid/duplicate case id");
            check(item.category() != null && CATEGORIES.contains(item.category()), "Unknown category for " + item.id());
            categories.add(item.category());
            check("PRIMARY".equals(item.cohort()) || "SEMANTIC_CHALLENGE".equals(item.cohort()), "Unknown cohort for " + item.id());
            check("POSITIVE".equals(item.label()) || "NEGATIVE".equals(item.label()), "Explicit binary label required for " + item.id());
            check(item.rationale() != null && !item.rationale().isBlank(), "Manual rationale required for " + item.id());
            check(item.submittedText() != null && item.submittedText().length() <= DocumentExtractor.MAX_CHARACTERS, "Invalid submitted text");
            check(item.sources() != null && !item.sources().isEmpty() && item.sources().size() <= EvidenceEngine.MAX_SOURCES, "Invalid source count");
            Set<String> sourceIds = new HashSet<>();
            long sourceCharacters = 0;
            for (var source : item.sources()) {
                check(source != null && source.id() != null && !source.id().equals(item.id()) && sourceIds.add(source.id()), "Invalid source identity");
                check(source.title() != null && !source.title().isBlank() && source.type() != null && !source.type().isBlank(), "Source metadata required");
                check(source.text() != null && source.text().length() <= DocumentExtractor.MAX_CHARACTERS, "Invalid source text");
                sourceCharacters += source.text().length();
            }
            check(sourceCharacters <= EvidenceEngine.MAX_CORPUS_CHARACTERS, "Source corpus exceeds engine limit");
            check(item.category().equals("semantic_paraphrase") == item.cohort().equals("SEMANTIC_CHALLENGE"), "Semantic challenges must be separately identified");
        }
        check(categories.equals(CATEGORIES), "Required scenario categories are missing");
        return data;
    }

    private static void check(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }
}
