package com.writingassistant.similarity.embedding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Multi-criteria decision engine for evaluating and selecting embedding models
 * based on operational hardware constraints, latency targets, and academic accuracy.
 */
public class EmbeddingModelSelector {

    public record OperationalConstraints(
            long maxMemoryBytes,
            double maxCpuLatencyMs,
            int maxPgvectorDimensions,
            int minSequenceLength,
            boolean requireMultilingual,
            boolean requirePermissiveLicense
    ) {
        public static OperationalConstraints localDesktopDefault() {
            return new OperationalConstraints(
                    256_000_000L, // 256 MB RAM limit for model
                    35.0,         // < 35 ms CPU inference per passage
                    512,          // max 512 dimensions for rapid IVFFlat/HNSW indexing
                    256,          // standard chunk sequence length
                    false,
                    true
            );
        }

        public static OperationalConstraints multilingualServer() {
            return new OperationalConstraints(
                    1_500_000_000L, // 1.5 GB RAM
                    120.0,          // < 120 ms
                    768,            // 768 dimensions
                    512,            // 512 tokens
                    true,
                    true
            );
        }

        public static OperationalConstraints highPerformanceServer() {
            return new OperationalConstraints(
                    4_000_000_000L, // 4 GB RAM
                    500.0,
                    1536,
                    4096,           // requires long-context support (>= 4k tokens)
                    true,
                    true
            );
        }
    }

    public record ScoredCandidate(
            EmbeddingModelSpec spec,
            double overallScore,
            double accuracyScore,
            double latencyScore,
            double memoryScore,
            double vectorIndexScore,
            boolean satisfiesConstraints,
            String selectionRationale
    ) {}

    private final List<EmbeddingModelSpec> candidates;

    public EmbeddingModelSelector() {
        this(List.of(
                EmbeddingModelSpec.allMiniLmL6V2(),
                EmbeddingModelSpec.multilingualE5Base(),
                EmbeddingModelSpec.bgeM3()
        ));
    }

    public EmbeddingModelSelector(List<EmbeddingModelSpec> candidates) {
        this.candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public List<EmbeddingModelSpec> getCandidates() {
        return candidates;
    }

    /**
     * Evaluates all candidate models against operational constraints and returns scored rankings.
     */
    public List<ScoredCandidate> evaluate(OperationalConstraints constraints) {
        Objects.requireNonNull(constraints, "constraints must not be null");
        List<ScoredCandidate> results = new ArrayList<>();

        for (EmbeddingModelSpec spec : candidates) {
            boolean fitsMemory = spec.int8SizeBytes() <= constraints.maxMemoryBytes();
            boolean fitsLatency = spec.averageCpuLatencyMs() <= constraints.maxCpuLatencyMs();
            boolean fitsDimensions = spec.dimensions() <= constraints.maxPgvectorDimensions();
            boolean fitsSequenceLength = spec.maxSequenceLength() >= constraints.minSequenceLength();
            boolean fitsMultilingual = !constraints.requireMultilingual() || spec.isMultilingual();
            boolean fitsLicense = !constraints.requirePermissiveLicense() || isPermissive(spec.license());

            boolean satisfies = fitsMemory && fitsLatency && fitsDimensions && fitsSequenceLength && fitsMultilingual && fitsLicense;

            // Metric normalizations [0..1]
            double accuracyScore = spec.mtebRetrievalScore() / 60.0;
            double latencyScore = Math.max(0.0, 1.0 - (spec.averageCpuLatencyMs() / 300.0));
            double memoryScore = Math.max(0.0, 1.0 - ((double) spec.int8SizeBytes() / 600_000_000L));
            double vectorIndexScore = Math.max(0.0, 1.0 - ((double) spec.dimensions() / 1536.0));

            // Weighted multi-criteria composite score
            // 35% Accuracy, 25% Latency, 20% Memory Efficiency, 20% Vector Indexing Efficiency
            double composite = (accuracyScore * 0.35) + (latencyScore * 0.25)
                    + (memoryScore * 0.20) + (vectorIndexScore * 0.20);

            if (!satisfies) {
                composite *= 0.5; // Penalty for constraint violations
            }

            StringBuilder rationale = new StringBuilder();
            if (satisfies) {
                rationale.append("Satisfies all operational constraints. ");
                if (spec.dimensions() <= 384) rationale.append("Optimal for lightweight local CPU vector search.");
                else if (spec.isMultilingual()) rationale.append("Recommended for internationalized academic corpora.");
            } else {
                rationale.append("Failed constraints: ");
                List<String> failed = new ArrayList<>();
                if (!fitsMemory) failed.add("Exceeds memory ceiling (" + (spec.int8SizeBytes() / (1024 * 1024)) + "MB)");
                if (!fitsLatency) failed.add("Exceeds latency target (" + spec.averageCpuLatencyMs() + "ms)");
                if (!fitsDimensions) failed.add("Exceeds max vector dimensions (" + spec.dimensions() + ")");
                if (!fitsSequenceLength) failed.add("Insufficient context length (" + spec.maxSequenceLength() + " < " + constraints.minSequenceLength() + ")");
                if (!fitsMultilingual) failed.add("Missing multilingual support");
                if (!fitsLicense) failed.add("Non-permissive license");
                rationale.append(String.join(", ", failed));
            }

            results.add(new ScoredCandidate(
                    spec,
                    composite,
                    accuracyScore,
                    latencyScore,
                    memoryScore,
                    vectorIndexScore,
                    satisfies,
                    rationale.toString()
            ));
        }

        results.sort(Comparator.comparingDouble(ScoredCandidate::overallScore).reversed());
        return results;
    }

    /**
     * Selects the single optimal model for the specified operational environment.
     */
    public EmbeddingModelSpec selectOptimalModel(OperationalConstraints constraints) {
        List<ScoredCandidate> scored = evaluate(constraints);
        for (ScoredCandidate candidate : scored) {
            if (candidate.satisfiesConstraints()) {
                return candidate.spec();
            }
        }
        // Fallback to highest overall score if no candidate strictly satisfies all constraints
        return scored.isEmpty() ? EmbeddingModelSpec.allMiniLmL6V2() : scored.get(0).spec();
    }

    private static boolean isPermissive(String license) {
        if (license == null) return false;
        String clean = license.toUpperCase().replace("-", "").replace(".", "");
        return clean.contains("APACHE2") || clean.contains("APACHE") || clean.contains("MIT") || clean.contains("BSD");
    }
}
