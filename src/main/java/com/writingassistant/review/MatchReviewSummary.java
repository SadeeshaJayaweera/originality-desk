package com.writingassistant.review;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Aggregated review status for an individual match index across one or more reviewers.
 */
public record MatchReviewSummary(
        int matchIndex,
        ReviewDecision activeDecision,
        ReviewConflictStatus conflictStatus,
        List<ReviewRecord> history,
        int totalReviews,
        boolean hasConflict,
        boolean requiresAction
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public MatchReviewSummary {
        Objects.requireNonNull(activeDecision, "activeDecision must not be null");
        Objects.requireNonNull(conflictStatus, "conflictStatus must not be null");
        history = history != null ? List.copyOf(history) : List.of();
    }

    public static MatchReviewSummary unreviewed(int matchIndex) {
        return new MatchReviewSummary(
                matchIndex,
                ReviewDecision.PENDING,
                ReviewConflictStatus.NO_REVIEWS,
                List.of(),
                0,
                false,
                false
        );
    }
}
