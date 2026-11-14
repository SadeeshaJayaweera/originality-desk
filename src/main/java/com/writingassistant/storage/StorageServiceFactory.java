package com.writingassistant.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Factory for instantiating the active StorageService backend based on configuration.
 */
public final class StorageServiceFactory {

    private StorageServiceFactory() {}

    public static StorageService create(Properties props) throws IOException {
        String backend = props.getProperty("storage.backend", "local").trim().toLowerCase();

        if ("s3".equals(backend)) {
            String endpoint = props.getProperty("storage.s3.endpoint", "http://localhost:9000");
            String bucket = props.getProperty("storage.s3.bucket", "originality-desk");
            String region = props.getProperty("storage.s3.region", "us-east-1");
            String accessKey = props.getProperty("storage.s3.accessKey", "");
            String secretKey = props.getProperty("storage.s3.secretKey", "");
            boolean pathStyle = Boolean.parseBoolean(props.getProperty("storage.s3.pathStyle", "true"));

            S3StorageConfig config = new S3StorageConfig(endpoint, bucket, region, accessKey, secretKey, pathStyle);
            return new S3CompatibleStorageService(config);
        } else {
            String dirStr = props.getProperty("storage.local.dir", "data/documents");
            Path rootPath = Paths.get(dirStr);
            return new LocalStorageService(rootPath);
        }
    }
}
