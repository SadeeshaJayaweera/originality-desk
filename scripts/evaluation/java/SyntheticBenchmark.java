package com.writingassistant.evaluation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.writingassistant.analysis.EvidenceEngine;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Calls the engine from the supplied application JAR; never substitutes expected labels for outputs. */
public final class SyntheticBenchmark {
    private static final double THRESHOLD = 0.10;
    private static final List<String> PROFILES = List.of("QUICK", "STANDARD", "REFERENCE_AWARE");
    private static volatile int sink;
    private SyntheticBenchmark() { }

    record Evidence(String sourceId, String method, double value, int start, int end, int sourceStart,
            int sourceEnd, String citationObservation) { }
    record Observation(String caseId, String category, String cohort, String label, boolean predicted,
            String outcome, int wordCount, int submittedCharacters, int sourceCount, int sourceCharacters,
            double exactOverlap, double nearOverlap, double totalOverlap, List<Evidence> evidence,
            Map<String, Long> citationKinds, String semanticStatus, String aiWritingStatus) { }
    record CaseResult(Observation observation, EvaluationMetrics.Timing timing) { }
    record Heap(long maxBytes, long usedBeforeBytes, long usedAfterBytes, long sampledUsedHighWaterBytes,
            long committedBeforeBytes, long committedAfterBytes, long gcCollections, long gcTimeMs) { }
    record ProfileResult(String profile, Map<String, EvaluationMetrics.Classification> metrics,
            EvaluationMetrics.Timing timing, double measurementWindowMs, Heap heap, List<CaseResult> cases) { }
    record Jvm(String runtimeVersion, String vmName, String vendor, String os, String osVersion, String architecture,
            int availableProcessors, List<String> inputArguments, List<String> garbageCollectors) { }
    record Report(int schemaVersion, String dataset, String provenance, String labelPolicy, String engineVersion,
            double overlapThreshold, int warmupRounds, int measuredRounds, Jvm jvm, List<ProfileResult> profiles) { }
    record Sample(String profile, int round, String caseId, long elapsedNs) { }

    public static void main(String[] args) throws IOException {
        Map<String, String> options = options(args);
        Path fixtureFile = Path.of(required(options, "--fixtures"));
        Path output = Path.of(required(options, "--output"));
        int warmup = rounds(required(options, "--warmup"), 0, 1000);
        int iterations = rounds(required(options, "--iterations"), 1, 1000);
        EvaluationSelfTest.run();
        System.out.println("Evaluation harness self-checks passed.");
        FixtureSet.Data fixtures = FixtureSet.load(fixtureFile);
        Files.createDirectories(output);
        List<Sample> samples = new ArrayList<>();
        List<ProfileResult> reports = new ArrayList<>();
        for (String profile : PROFILES) reports.add(evaluate(profile, fixtures.cases(), warmup, iterations, samples));
        var runtime = ManagementFactory.getRuntimeMXBean();
        var jvm = new Jvm(System.getProperty("java.runtime.version"), System.getProperty("java.vm.name"),
                System.getProperty("java.vendor"), System.getProperty("os.name"), System.getProperty("os.version"),
                System.getProperty("os.arch"), Runtime.getRuntime().availableProcessors(), runtime.getInputArguments(),
                ManagementFactory.getGarbageCollectorMXBeans().stream().map(bean -> bean.getName()).toList());
        var report = new Report(1, fixtures.name(), fixtures.provenance(), fixtures.labelPolicy(), EvidenceEngine.ENGINE_VERSION,
                THRESHOLD, warmup, iterations, jvm, reports);
        Gson gson = new GsonBuilder().serializeNulls().disableHtmlEscaping().setPrettyPrinting().create();
        Files.writeString(output.resolve("report.json"), gson.toJson(report) + "\n", StandardCharsets.UTF_8);
        writeCases(output.resolve("case-results.csv"), reports);
        writeSamples(output.resolve("timings.csv"), samples);
        for (var profile : reports) {
            var m = profile.metrics().get("PRIMARY");
            System.out.printf(Locale.ROOT, "%s PRIMARY: TP=%d FP=%d TN=%d FN=%d precision=%s recall=%s F1=%s; median=%.6f ms p95=%.6f ms%n",
                    profile.profile(), m.tp(), m.fp(), m.tn(), m.fn(), m.precision(), m.recall(), m.f1(),
                    profile.timing().medianMs(), profile.timing().p95Ms());
        }
        System.out.println("Wrote report.json, case-results.csv and timings.csv. Classifications count each case once, not timing repetitions.");
    }

    private static ProfileResult evaluate(String profile, List<FixtureSet.Case> cases, int warmup,
            int iterations, List<Sample> allSamples) {
        EvidenceEngine engine = new EvidenceEngine();
        List<EvidenceEngine.Result> baseline = new ArrayList<>();
        for (var item : cases) {
            var result = analyze(engine, item, profile);
            validate(item, result);
            baseline.add(result);
        }
        for (int round = 0; round < warmup; round++) {
            for (int position = 0; position < cases.size(); position++) {
                int index = (round + position) % cases.size();
                sink ^= analyze(engine, cases.get(index), profile).hashCode();
            }
        }
        List<List<Long>> perCase = new ArrayList<>();
        for (int i = 0; i < cases.size(); i++) perCase.add(new ArrayList<>());
        List<Long> times = new ArrayList<>();
        var memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage before = memory.getHeapMemoryUsage();
        long highWater = before.getUsed();
        long collectionsBefore = gcCollections();
        long gcTimeBefore = gcTime();
        long phaseStart = System.nanoTime();
        for (int round = 0; round < iterations; round++) {
            for (int position = 0; position < cases.size(); position++) {
                int index = (round + position) % cases.size();
                var item = cases.get(index);
                long start = System.nanoTime();
                var result = analyze(engine, item, profile);
                long elapsed = System.nanoTime() - start;
                // Validation, heap sampling, sample recording and serialization are outside call timing.
                if (!result.equals(baseline.get(index))) throw new IllegalStateException("Nondeterministic result: " + item.id());
                validate(item, result);
                sink ^= result.hashCode();
                highWater = Math.max(highWater, memory.getHeapMemoryUsage().getUsed());
                perCase.get(index).add(elapsed);
                times.add(elapsed);
                allSamples.add(new Sample(profile, round, item.id(), elapsed));
            }
        }
        double phaseMs = (System.nanoTime() - phaseStart) / 1e6;
        MemoryUsage after = memory.getHeapMemoryUsage();
        var heap = new Heap(before.getMax(), before.getUsed(), after.getUsed(), Math.max(highWater, after.getUsed()),
                before.getCommitted(), after.getCommitted(), gcCollections() - collectionsBefore, gcTime() - gcTimeBefore);
        List<CaseResult> results = new ArrayList<>();
        for (int i = 0; i < cases.size(); i++) results.add(new CaseResult(observe(cases.get(i), baseline.get(i)), EvaluationMetrics.timing(perCase.get(i))));
        Map<String, EvaluationMetrics.Classification> metrics = new LinkedHashMap<>();
        for (String cohort : List.of("PRIMARY", "SEMANTIC_CHALLENGE", "ALL")) {
            metrics.put(cohort, EvaluationMetrics.classify(results.stream().map(CaseResult::observation)
                    .filter(o -> cohort.equals("ALL") || o.cohort().equals(cohort))
                    .map(o -> new EvaluationMetrics.Prediction(o.label().equals("POSITIVE"), o.predicted())).toList()));
        }
        return new ProfileResult(profile, metrics, EvaluationMetrics.timing(times), phaseMs, heap, List.copyOf(results));
    }

    private static EvidenceEngine.Result analyze(EvidenceEngine engine, FixtureSet.Case item, String profile) {
        return engine.analyze(item.id(), item.id(), item.submittedText(), item.sources(), profile);
    }

    private static Observation observe(FixtureSet.Case item, EvidenceEngine.Result result) {
        boolean predicted = !result.matches().isEmpty() && result.totalOverlap() >= THRESHOLD;
        var evidence = result.matches().stream().map(m -> new Evidence(m.sourceId(), m.method(), m.value(),
                m.start(), m.end(), m.sourceStart(), m.sourceEnd(), m.citationObservation())).toList();
        Map<String, Long> citationKinds = new TreeMap<>();
        for (var citation : result.citations()) citationKinds.merge(citation.kind(), 1L, Long::sum);
        return new Observation(item.id(), item.category(), item.cohort(), item.label(), predicted,
                EvaluationMetrics.outcome(item.positive(), predicted), result.wordCount(), item.submittedText().length(),
                item.sources().size(), item.sources().stream().mapToInt(source -> source.text().length()).sum(),
                result.exactOverlap(), result.nearOverlap(), result.totalOverlap(), evidence, citationKinds,
                result.semanticStatus(), result.aiWritingStatus());
    }

    private static void validate(FixtureSet.Case item, EvidenceEngine.Result result) {
        if (!"NOT_IMPLEMENTED".equals(result.semanticStatus()) || !"NOT_IMPLEMENTED".equals(result.aiWritingStatus())) {
            throw new IllegalStateException("Review methodology before evaluating an engine with changed capabilities");
        }
        if (!Double.isFinite(result.totalOverlap()) || result.totalOverlap() < 0 || result.totalOverlap() > 1
                || Math.abs(result.totalOverlap() - result.exactOverlap() - result.nearOverlap()) > 1e-9) {
            throw new IllegalStateException("Invalid union coverage: " + item.id());
        }
        for (var match : result.matches()) {
            var source = item.sources().stream().filter(s -> s.id().equals(match.sourceId())).findFirst().orElseThrow();
            if (match.start() >= match.end() || match.sourceStart() >= match.sourceEnd()
                    || !item.submittedText().substring(match.start(), match.end()).equals(match.submittedPassage())
                    || !source.text().substring(match.sourceStart(), match.sourceEnd()).equals(match.sourcePassage())
                    || !source.title().equals(match.sourceTitle()) || !source.type().equals(match.sourceType())) {
                throw new IllegalStateException("Invalid evidence provenance/offsets: " + item.id());
            }
        }
        for (var citation : result.citations()) {
            if (!citation.uncertain() || !item.submittedText().substring(citation.start(), citation.end()).equals(citation.text())) {
                throw new IllegalStateException("Invalid citation observation: " + item.id());
            }
        }
    }

    private static void writeCases(Path path, List<ProfileResult> reports) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            csv(writer, "profile", "case_id", "category", "cohort", "label", "predicted", "outcome", "words", "exact_overlap", "near_overlap", "total_overlap", "matches", "source_ids", "methods", "median_ms", "p95_ms");
            for (var profile : reports) for (var item : profile.cases()) {
                var o = item.observation();
                csv(writer, profile.profile(), o.caseId(), o.category(), o.cohort(), o.label(), o.predicted(), o.outcome(), o.wordCount(),
                        o.exactOverlap(), o.nearOverlap(), o.totalOverlap(), o.evidence().size(),
                        String.join(";", o.evidence().stream().map(Evidence::sourceId).distinct().sorted().toList()),
                        String.join(";", o.evidence().stream().map(Evidence::method).distinct().sorted().toList()), item.timing().medianMs(), item.timing().p95Ms());
            }
        }
    }

    private static void writeSamples(Path path, List<Sample> samples) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            csv(writer, "profile", "round", "case_id", "elapsed_ns");
            for (var sample : samples) csv(writer, sample.profile(), sample.round(), sample.caseId(), sample.elapsedNs());
        }
    }

    private static void csv(BufferedWriter writer, Object... values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) writer.write(',');
            writer.write('"');
            writer.write(values[i].toString().replace("\"", "\"\""));
            writer.write('"');
        }
        writer.write('\n');
    }
    private static long gcCollections() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0, b.getCollectionCount())).sum();
    }
    private static long gcTime() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0, b.getCollectionTime())).sum();
    }
    private static Map<String, String> options(String[] args) {
        if (args.length % 2 != 0) throw new IllegalArgumentException("Expected named option/value pairs");
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            if (!List.of("--fixtures", "--output", "--warmup", "--iterations").contains(args[i])
                    || options.putIfAbsent(args[i], args[i + 1]) != null) throw new IllegalArgumentException("Invalid/duplicate option: " + args[i]);
        }
        return options;
    }
    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null) throw new IllegalArgumentException("Missing option " + name);
        return value;
    }
    private static int rounds(String value, int minimum, int maximum) {
        int parsed = Integer.parseInt(value);
        if (parsed < minimum || parsed > maximum) throw new IllegalArgumentException("Rounds must be " + minimum + ".." + maximum);
        return parsed;
    }
}
