package com.writingassistant.similarity.semantic;

import com.writingassistant.persistence.vector.VectorStore;
import com.writingassistant.similarity.SimilarityOptions;
import com.writingassistant.similarity.SimilaritySource;
import com.writingassistant.similarity.SimilaritySpan;
import com.writingassistant.similarity.SimilarityTarget;
import com.writingassistant.similarity.chunking.ChunkingOptions;
import com.writingassistant.similarity.chunking.DocumentEmbeddingPipeline;
import com.writingassistant.similarity.chunking.EmbeddedChunk;
import com.writingassistant.similarity.onnx.EmbeddingEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * High-precision semantic similarity analyzer that decomposes target and source documents into
 * sentence-aware chunks, executes dense vector similarity matching, consolidates passages,
 * and generates highlight spans and interval-union coverage metrics.
 */
public class SemanticSimilarityAnalyzer {

    public static final double DEFAULT_MIN_SCORE_THRESHOLD = 0.55;

    private final DocumentEmbeddingPipeline pipeline;
    private final VectorStore vectorStore;

    public SemanticSimilarityAnalyzer(EmbeddingEngine embeddingEngine) {
        this(new DocumentEmbeddingPipeline(embeddingEngine), null);
    }

    public SemanticSimilarityAnalyzer(DocumentEmbeddingPipeline pipeline) {
        this(pipeline, null);
    }

    public SemanticSimilarityAnalyzer(DocumentEmbeddingPipeline pipeline, VectorStore vectorStore) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline must not be null");
        this.vectorStore = vectorStore;
    }

    public DocumentEmbeddingPipeline getPipeline() {
        return pipeline;
    }

    public VectorStore getVectorStore() {
        return vectorStore;
    }

    public SemanticAnalysisResult analyze(SimilarityTarget target, List<SimilaritySource> corpus, SimilarityOptions options) {
        Objects.requireNonNull(target, "target must not be null");
        if (corpus == null || corpus.isEmpty() || target.text().isBlank()) {
            return SemanticAnalysisResult.empty();
        }

        double minThreshold = (options != null && options.minScoreThreshold() > 0)
                ? options.minScoreThreshold()
                : DEFAULT_MIN_SCORE_THRESHOLD;

        // 1. Chunk and embed target document
        List<EmbeddedChunk> targetChunks = pipeline.embedDocument(target.documentId(), target.text(), ChunkingOptions.defaults());
        if (targetChunks.isEmpty()) {
            return SemanticAnalysisResult.empty();
        }

        // 2. Chunk and embed corpus documents
        List<EmbeddedChunk> corpusChunks = new ArrayList<>();
        for (SimilaritySource source : corpus) {
            if (source.text() == null || source.text().isBlank()) continue;
            List<EmbeddedChunk> sChunks = pipeline.embedDocument(source.sourceId(), source.text(), ChunkingOptions.defaults());
            corpusChunks.addAll(sChunks);
        }

        if (corpusChunks.isEmpty()) {
            return SemanticAnalysisResult.empty();
        }

        // 3. Dense chunk-to-chunk cosine scoring
        List<SemanticMatch> matches = new ArrayList<>();
        double maxSimilarity = 0.0;

        for (EmbeddedChunk tChunk : targetChunks) {
            for (EmbeddedChunk sChunk : corpusChunks) {
                double cosine = tChunk.cosineSimilarity(sChunk);
                if (cosine > maxSimilarity) {
                    maxSimilarity = cosine;
                }

                if (cosine >= minThreshold) {
                    String matchType = cosine >= 0.80 ? "HIGH_CONFIDENCE_PARAPHRASE" : "CONCEPTUAL_PARAPHRASE";
                    matches.add(new SemanticMatch(
                            sChunk.documentId(),
                            tChunk.startOffset(),
                            tChunk.endOffset(),
                            sChunk.startOffset(),
                            sChunk.endOffset(),
                            tChunk.text(),
                            sChunk.text(),
                            cosine,
                            matchType
                    ));
                }
            }
        }

        Collections.sort(matches);

        // 4. Generate consolidated SimilaritySpans and SemanticHighlights
        List<SimilaritySpan> spans = new ArrayList<>();
        List<SemanticHighlight> highlights = new ArrayList<>();

        for (SemanticMatch m : matches) {
            spans.add(new SimilaritySpan(
                    m.sourceId(),
                    m.targetStart(),
                    m.targetEnd(),
                    m.sourceStart(),
                    m.sourceEnd(),
                    "SEMANTIC",
                    m.score(),
                    m.targetText(),
                    m.sourceText()
            ));

            String label = String.format(Locale.ROOT, "Semantic Paraphrase (%.0f%%)", m.score() * 100);
            highlights.add(new SemanticHighlight(
                    m.targetStart(),
                    m.targetEnd(),
                    m.score(),
                    m.sourceId(),
                    label,
                    m.targetText()
            ));
        }

        // 5. Calculate Interval-Union Coverage on Target Document
        double coverageRatio = calculateIntervalUnionCoverage(target.text().length(), matches);

        // 6. Assemble Telemetry
        Map<String, Object> telemetry = new HashMap<>();
        telemetry.put("status", "SUCCESS");
        telemetry.put("model", pipeline.getEmbeddingEngine().getModelName());
        telemetry.put("target_chunks", targetChunks.size());
        telemetry.put("corpus_chunks", corpusChunks.size());
        telemetry.put("matches_count", matches.size());
        telemetry.put("max_similarity", maxSimilarity);
        telemetry.put("coverage_ratio", coverageRatio);

        return new SemanticAnalysisResult(matches, highlights, spans, coverageRatio, maxSimilarity, telemetry);
    }

    private static double calculateIntervalUnionCoverage(int totalLength, List<SemanticMatch> matches) {
        if (totalLength <= 0 || matches.isEmpty()) return 0.0;

        // Sort intervals by start offset
        List<int[]> intervals = new ArrayList<>();
        for (SemanticMatch m : matches) {
            intervals.add(new int[]{m.targetStart(), m.targetEnd()});
        }
        intervals.sort(Comparator.comparingInt(a -> a[0]));

        // Merge overlapping intervals
        List<int[]> merged = new ArrayList<>();
        int[] current = intervals.get(0);

        for (int i = 1; i < intervals.size(); i++) {
            int[] next = intervals.get(i);
            if (next[0] <= current[1]) {
                current[1] = Math.max(current[1], next[1]);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);

        int coveredChars = 0;
        for (int[] interval : merged) {
            coveredChars += (interval[1] - interval[0]);
        }

        return Math.min(1.0, (double) coveredChars / totalLength);
    }
}
