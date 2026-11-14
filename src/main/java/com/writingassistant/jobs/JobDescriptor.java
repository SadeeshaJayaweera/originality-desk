package com.writingassistant.jobs;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable metadata model for a background job.
 */
public record JobDescriptor(
        String id,
        String workspaceId,
        String kind,
        String resourceId,
        JobStatus status,
        JobPriority priority,
        int attempts,
        int maxAttempts,
        long availableAt,
        long lastHeartbeat,
        String createdAt,
        String completedAt,
        String error,
        String leaseOwner
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    public JobDescriptor {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(resourceId, "resourceId must not be null");
        status = status != null ? status : JobStatus.QUEUED;
        priority = priority != null ? priority : JobPriority.NORMAL;
        if (maxAttempts <= 0) maxAttempts = DEFAULT_MAX_ATTEMPTS;
    }
}
