package com.writingassistant.storage;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable metadata attributes for an object stored in the storage service.
 */
public record StorageMetadata(
        String workspaceId,
        String objectKey,
        long sizeBytes,
        String sha256,
        String contentType,
        String lastModified
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public StorageMetadata {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(objectKey, "objectKey must not be null");
        contentType = contentType != null && !contentType.isBlank() ? contentType : "application/octet-stream";
    }
}
