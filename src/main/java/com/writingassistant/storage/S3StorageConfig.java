package com.writingassistant.storage;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable configuration for S3-compatible cloud object storage (SeaweedFS, MinIO, LocalStack, AWS S3).
 */
public record S3StorageConfig(
        String endpoint,
        String bucket,
        String region,
        String accessKey,
        String secretKey,
        boolean pathStyle
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public S3StorageConfig {
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        Objects.requireNonNull(bucket, "bucket must not be null");
        region = region != null && !region.isBlank() ? region : "us-east-1";
        accessKey = accessKey != null ? accessKey : "";
        secretKey = secretKey != null ? secretKey : "";
    }

    public static S3StorageConfig forLocalMinio(String endpoint, String bucket) {
        return new S3StorageConfig(endpoint, bucket, "us-east-1", "minioadmin", "minioadmin", true);
    }

    public static S3StorageConfig forSeaweedFS(String endpoint, String bucket) {
        return new S3StorageConfig(endpoint, bucket, "us-east-1", "", "", true);
    }
}
