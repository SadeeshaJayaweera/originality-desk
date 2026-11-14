package com.writingassistant.review;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * High-level consolidated review summary for an entire document or analysis.
 */
public record DocumentReviewSummary(
        String workspaceId,
        String analysisId,
        String overallStatus,
        int totalMatches,
        int reviewedMatches,
        int approvedCount,
        int flaggedCount,
        int exemptedCount,
        int escalatedCount,
        int conflictCount,
        Map<Integer, MatchReviewSummary> matchSummaries,
        List<ReviewRecord> fullAuditTrail
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_UNREVIEWED = "UNREVIEWED";
    public static final String STATUS_IN_REVIEW = "IN_REVIEW";
    public static final String STATUS_NEEDS_RESOLUTION = "NEEDS_RESOLUTION";
    public static final String STATUS_CLEARED = "CLEARED";
    public static final String STATUS_ACTION_REQUIRED = "ACTION_REQUIRED";

    public DocumentReviewSummary {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(analysisId, "analysisId must not be null");
        Objects.requireNonNull(overallStatus, "overallStatus must not be null");
        matchSummaries = matchSummaries != null ? Collections.unmodifiableMap(matchSummaries) : Map.of();
        fullAuditTrail = fullAuditTrail != null ? List.copyOf(fullAuditTrail) : List.of();
    }
}
