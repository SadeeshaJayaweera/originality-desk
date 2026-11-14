package com.writingassistant.review;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable auditable record of an individual reviewer action.
 */
public record ReviewRecord(
        String id,
        String workspaceId,
        String analysisId,
        int matchIndex,
        String reviewerId,
        String reviewerName,
        ReviewRole reviewerRole,
        ReviewDecision decision,
        String note,
        String createdAt,
        boolean isResolution
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public ReviewRecord {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(analysisId, "analysisId must not be null");
        Objects.requireNonNull(reviewerId, "reviewerId must not be null");
        reviewerName = reviewerName != null && !reviewerName.isBlank() ? reviewerName : reviewerId;
        reviewerRole = reviewerRole != null ? reviewerRole : ReviewRole.PEER_REVIEWER;
        Objects.requireNonNull(decision, "decision must not be null");
        note = note != null ? note : "";
        createdAt = createdAt != null && !createdAt.isBlank() ? createdAt : Instant.now().toString();
    }
}
