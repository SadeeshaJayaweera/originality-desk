package com.writingassistant.similarity.hybrid;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Complete academic hybrid similarity report decomposing exact, near-duplicate, semantic,
 * and quoted textual evidence with per-source contributions and ethical disclosures.
 */
public record HybridSimilarityReport(
        String documentId,
        String documentTitle,
        DecomposedOverlapMetrics metrics,
        List<AttributedSpan> attributedSpans,
        List<SourceContribution> sourceContributions,
        List<String> methodologyNotices,
        List<String> limitations,
        String academicTransparencyDisclaimer,
        Map<String, Object> telemetry
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String DEFAULT_TRANSPARENCY_DISCLAIMER =
            "ACADEMIC INTEGRITY NOTICE: Originality Desk reports objective, decomposed textual overlap and semantic "
            + "proximity metrics. Textual overlap does NOT constitute a finding of plagiarism or academic dishonesty. "
            + "Legitimate scholarly work appropriately quotes, cites, and paraphrases established literature. "
            + "No disciplinary or academic decision should ever be based solely on automated numerical scores. "
            + "Comprehensive qualitative review by qualified academic faculty is required.";

    public HybridSimilarityReport {
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(documentTitle, "documentTitle must not be null");
        Objects.requireNonNull(metrics, "metrics must not be null");
        attributedSpans = attributedSpans != null ? List.copyOf(attributedSpans) : List.of();
        sourceContributions = sourceContributions != null ? List.copyOf(sourceContributions) : List.of();
        methodologyNotices = methodologyNotices != null ? List.copyOf(methodologyNotices) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
        if (academicTransparencyDisclaimer == null || academicTransparencyDisclaimer.isBlank()) {
            academicTransparencyDisclaimer = DEFAULT_TRANSPARENCY_DISCLAIMER;
        }
        telemetry = telemetry != null ? Map.copyOf(telemetry) : Map.of();
    }
}
