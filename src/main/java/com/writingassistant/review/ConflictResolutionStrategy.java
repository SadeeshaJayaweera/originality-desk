package com.writingassistant.review;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;

/**
 * Strategy for reconciling conflicting evaluations submitted by multiple reviewers.
 */
public enum ConflictResolutionStrategy implements Serializable {
    /**
     * Resolves conflict by taking the decision submitted by the reviewer with the highest role authority.
     */
    SENIORITY {
        @Override
        public ReviewDecision resolve(List<ReviewRecord> records) {
            if (records == null || records.isEmpty()) return ReviewDecision.PENDING;
            return records.stream()
                    .max(Comparator.comparingInt((ReviewRecord r) -> r.reviewerRole().getHierarchyLevel())
                            .thenComparing(ReviewRecord::createdAt))
                    .map(ReviewRecord::decision)
                    .orElse(ReviewDecision.PENDING);
        }
    },

    /**
     * Resolves conflict by adopting the most restrictive/cautious decision (highest severity rank).
     */
    MOST_CONSERVATIVE {
        @Override
        public ReviewDecision resolve(List<ReviewRecord> records) {
            if (records == null || records.isEmpty()) return ReviewDecision.PENDING;
            return records.stream()
                    .max(Comparator.comparingInt((ReviewRecord r) -> r.decision().getSeverityRank())
                            .thenComparing(ReviewRecord::createdAt))
                    .map(ReviewRecord::decision)
                    .orElse(ReviewDecision.PENDING);
        }
    },

    /**
     * Requires explicit manual adjudication by an authorized administrator or senior reviewer.
     * Retains CONFLICT status until an explicit resolution record is recorded.
     */
    MANUAL_ADJUDICATION {
        @Override
        public ReviewDecision resolve(List<ReviewRecord> records) {
            if (records == null || records.isEmpty()) return ReviewDecision.PENDING;
            // Check for explicit resolution record
            return records.stream()
                    .filter(ReviewRecord::isResolution)
                    .reduce((first, second) -> second) // latest resolution
                    .map(ReviewRecord::decision)
                    .orElse(ReviewDecision.PENDING);
        }
    };

    public abstract ReviewDecision resolve(List<ReviewRecord> records);
}
