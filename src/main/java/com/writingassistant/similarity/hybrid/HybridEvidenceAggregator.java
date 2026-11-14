package com.writingassistant.similarity.hybrid;

import com.writingassistant.similarity.CompositeSimilarityEngine;
import com.writingassistant.similarity.SimilarityOptions;
import com.writingassistant.similarity.SimilaritySource;
import com.writingassistant.similarity.SimilaritySpan;
import com.writingassistant.similarity.SimilarityTarget;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-precision evidence aggregator producing decomposed, non-inflated hybrid similarity reports.
 * Decomposes exact, near-duplicate, semantic, and quoted text without conflation or double-counting.
 */
public class HybridEvidenceAggregator {

    private static final Pattern QUOTATION_PATTERN = Pattern.compile("\"[^\"]{1,2000}\"|“[^”]{1,2000}”|‘[^’]{1,2000}’");
    private static final Pattern CITATION_PATTERN = Pattern.compile(
            "\\([^()\\r\\n]{1,180}\\b(?:18|19|20)\\d{2}[a-z]?(?:[^()\\r\\n]{0,40})\\)"
            + "|\\b\\p{Lu}[\\p{L}'’\\-]{1,40}(?: et al\\.)? \\((?:18|19|20)\\d{2}[a-z]?\\)"
            + "|\\[\\d{1,4}(?:[\\t ]*[,;–-][\\t ]*\\d{1,4}){0,15}\\]"
    );

    private final CompositeSimilarityEngine similarityEngine;

    public HybridEvidenceAggregator() {
        this(new CompositeSimilarityEngine());
    }

    public HybridEvidenceAggregator(CompositeSimilarityEngine similarityEngine) {
        this.similarityEngine = Objects.requireNonNull(similarityEngine, "similarityEngine must not be null");
    }

    public CompositeSimilarityEngine getSimilarityEngine() {
        return similarityEngine;
    }

    public HybridSimilarityReport aggregate(SimilarityTarget target, List<SimilaritySource> corpus, SimilarityOptions options) {
        Objects.requireNonNull(target, "target must not be null");
        if (target.text().isBlank() || corpus == null || corpus.isEmpty()) {
            DecomposedOverlapMetrics emptyMetrics = new DecomposedOverlapMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0);
            return new HybridSimilarityReport(target.documentId(), target.title(), emptyMetrics, List.of(), List.of(),
                    methodologyNotices(), List.of(), HybridSimilarityReport.DEFAULT_TRANSPARENCY_DISCLAIMER, Map.of("status", "EMPTY"));
        }

        int textLength = target.text().length();
        int wordCount = target.text().strip().split("\\s+").length;

        // 1. Run Composite Similarity Engine across all active strategies
        CompositeSimilarityEngine.CompositeSimilarityResult compositeResult = similarityEngine.analyze(target, corpus, options);

        // 2. Detect Quotations and In-text Citations in Target Text
        List<int[]> quoteIntervals = findMatches(QUOTATION_PATTERN, target.text());
        List<int[]> citationIntervals = findMatches(CITATION_PATTERN, target.text());

        BitSet quoteBits = new BitSet(textLength);
        for (int[] interval : quoteIntervals) {
            quoteBits.set(interval[0], interval[1]);
        }

        BitSet citationBits = new BitSet(textLength);
        for (int[] interval : citationIntervals) {
            citationBits.set(interval[0], interval[1]);
        }

        // 3. Compute Decomposed Bitsets across match methods
        BitSet exactBits = new BitSet(textLength);
        BitSet nearBits = new BitSet(textLength);
        BitSet semanticBits = new BitSet(textLength);

        for (SimilaritySpan span : compositeResult.aggregatedSpans()) {
            int start = Math.max(0, Math.min(textLength, span.targetStart()));
            int end = Math.max(start, Math.min(textLength, span.targetEnd()));
            if (end <= start) continue;

            String method = span.method().toUpperCase();
            if (method.contains("EXACT")) {
                exactBits.set(start, end);
            } else if (method.contains("NEAR")) {
                nearBits.set(start, end);
            } else if (method.contains("SEMANTIC")) {
                semanticBits.set(start, end);
            }
        }

        // 4. Calculate Total Union and Net Unattributed Overlap
        BitSet totalUnionBits = (BitSet) exactBits.clone();
        totalUnionBits.or(nearBits);
        totalUnionBits.or(semanticBits);

        BitSet netUnattributedBits = (BitSet) totalUnionBits.clone();
        netUnattributedBits.andNot(quoteBits); // Exclude legitimately quoted text

        DecomposedOverlapMetrics metrics = new DecomposedOverlapMetrics(
                (double) exactBits.cardinality() / textLength,
                (double) nearBits.cardinality() / textLength,
                (double) semanticBits.cardinality() / textLength,
                (double) quoteBits.cardinality() / textLength,
                (double) citationBits.cardinality() / textLength,
                (double) totalUnionBits.cardinality() / textLength,
                (double) netUnattributedBits.cardinality() / textLength,
                textLength,
                wordCount
        );

        // 5. Enrich Spans into AttributedSpans
        List<AttributedSpan> attributedSpans = new ArrayList<>();
        for (SimilaritySpan span : compositeResult.aggregatedSpans()) {
            int start = Math.max(0, Math.min(textLength, span.targetStart()));
            int end = Math.max(start, Math.min(textLength, span.targetEnd()));

            boolean isQuoted = hasOverlap(quoteBits, start, end);
            boolean isCited = hasNearbyCitation(citationBits, start, end, 120, target.text());

            String category;
            String notes;

            if (isQuoted && isCited) {
                category = AttributedSpan.CATEGORY_QUOTED_AND_CITED;
                notes = "Quotation marks and citation present; proper academic attribution observed.";
            } else if (isQuoted) {
                category = AttributedSpan.CATEGORY_QUOTED_UNASSIGNED;
                notes = "Quotation marks observed without immediately identifiable citation marker.";
            } else if (span.method().toUpperCase().contains("EXACT")) {
                if (isCited) {
                    category = "CITED_VERBATIM_UNQUOTED";
                    notes = "Verbatim text accompanied by citation but lacking quotation marks.";
                } else {
                    category = AttributedSpan.CATEGORY_UNATTRIBUTED_EXACT;
                    notes = "Verbatim text overlap without quotation marks or citation; review recommended.";
                }
            } else if (isCited) {
                category = AttributedSpan.CATEGORY_CITED_PARAPHRASE;
                notes = "Paraphrased or referenced passage accompanied by citation marker.";
            } else {
                category = AttributedSpan.CATEGORY_UNATTRIBUTED_PARAPHRASE;
                notes = "Paraphrased content without nearby citation marker; review recommended.";
            }

            attributedSpans.add(new AttributedSpan(span, isQuoted, isCited, category, notes));
        }

        Collections.sort(attributedSpans);

        // 6. Compute Per-Source Contributions
        Map<String, SimilaritySource> sourceMap = new HashMap<>();
        for (SimilaritySource s : corpus) {
            sourceMap.put(s.sourceId(), s);
        }

        Map<String, List<SimilaritySpan>> spansBySource = new HashMap<>();
        for (SimilaritySpan span : compositeResult.aggregatedSpans()) {
            spansBySource.computeIfAbsent(span.sourceId(), k -> new ArrayList<>()).add(span);
        }

        List<SourceContribution> contributions = new ArrayList<>();
        for (Map.Entry<String, List<SimilaritySpan>> entry : spansBySource.entrySet()) {
            String srcId = entry.getKey();
            List<SimilaritySpan> sSpans = entry.getValue();
            SimilaritySource src = sourceMap.get(srcId);
            String title = src != null ? src.title() : "Source " + srcId;
            String type = src != null ? src.sourceType() : "REFERENCE";

            BitSet sExact = new BitSet(textLength);
            BitSet sNear = new BitSet(textLength);
            BitSet sSemantic = new BitSet(textLength);
            BitSet sUnion = new BitSet(textLength);

            for (SimilaritySpan sp : sSpans) {
                int s = Math.max(0, Math.min(textLength, sp.targetStart()));
                int e = Math.max(s, Math.min(textLength, sp.targetEnd()));
                sUnion.set(s, e);
                String m = sp.method().toUpperCase();
                if (m.contains("EXACT")) sExact.set(s, e);
                else if (m.contains("NEAR")) sNear.set(s, e);
                else if (m.contains("SEMANTIC")) sSemantic.set(s, e);
            }

            contributions.add(new SourceContribution(
                    srcId,
                    title,
                    type,
                    (double) sExact.cardinality() / textLength,
                    (double) sNear.cardinality() / textLength,
                    (double) sSemantic.cardinality() / textLength,
                    (double) sUnion.cardinality() / textLength,
                    sSpans.size()
            ));
        }

        Collections.sort(contributions);

        // 7. Telemetry
        Map<String, Object> telemetry = new HashMap<>();
        telemetry.put("status", "SUCCESS");
        telemetry.put("total_sources", corpus.size());
        telemetry.put("matched_sources", contributions.size());
        telemetry.put("total_spans", attributedSpans.size());
        telemetry.put("exact_ratio", metrics.exactCoverageRatio());
        telemetry.put("near_ratio", metrics.nearDuplicateCoverageRatio());
        telemetry.put("semantic_ratio", metrics.semanticCoverageRatio());
        telemetry.put("total_union_ratio", metrics.totalUnionRatio());
        telemetry.put("net_unattributed_ratio", metrics.netUnattributedRatio());

        return new HybridSimilarityReport(
                target.documentId(),
                target.title(),
                metrics,
                attributedSpans,
                contributions,
                methodologyNotices(),
                compositeResult.limitations(),
                HybridSimilarityReport.DEFAULT_TRANSPARENCY_DISCLAIMER,
                telemetry
        );
    }

    private static List<int[]> findMatches(Pattern pattern, String text) {
        List<int[]> intervals = new ArrayList<>();
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            intervals.add(new int[]{m.start(), m.end()});
        }
        return intervals;
    }

    private static boolean hasOverlap(BitSet bitSet, int start, int end) {
        for (int i = start; i < end; i++) {
            if (bitSet.get(i)) return true;
        }
        return false;
    }

    private static boolean hasNearbyCitation(BitSet citationBits, int start, int end, int window, String text) {
        int rangeStart = Math.max(0, start - window);
        int rangeEnd = Math.min(citationBits.size(), end + window);

        // Bound to current paragraph if paragraph breaks are present
        int lastParagraphBefore = text.lastIndexOf("\n\n", start);
        if (lastParagraphBefore != -1 && lastParagraphBefore >= rangeStart) {
            rangeStart = lastParagraphBefore + 2;
        }
        int nextParagraphAfter = text.indexOf("\n\n", end);
        if (nextParagraphAfter != -1 && nextParagraphAfter <= rangeEnd) {
            rangeEnd = nextParagraphAfter;
        }

        for (int i = rangeStart; i < rangeEnd; i++) {
            if (citationBits.get(i)) return true;
        }
        return false;
    }

    private static List<String> methodologyNotices() {
        return List.of(
                "Decomposed coverage metrics report exact, near-duplicate, and semantic similarities independently.",
                "Total overlap union is computed geometrically (A U B U C) to strictly eliminate double-counting.",
                "Net unattributed overlap excludes text enclosed in recognized quotation marks from the overlap union.",
                "Originality Desk intentionally rejects single 'universal plagiarism scores' to protect students and researchers from unscientific automated accusations.",
                "In-text citations and quotation marks are heuristic indicators and must be verified by academic evaluators."
        );
    }
}
