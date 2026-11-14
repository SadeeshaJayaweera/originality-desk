package com.writingassistant.storage;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * StorageService implementation for S3-compatible object storage (SeaweedFS, MinIO, LocalStack, AWS S3)
 * implemented using standard Java HttpClient with zero external SDK dependencies.
 */
public class S3CompatibleStorageService implements StorageService {

    private final S3StorageConfig config;
    private final HttpClient httpClient;
    private final String baseUri;

    public S3CompatibleStorageService(S3StorageConfig config) {
        this(config, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    public S3CompatibleStorageService(S3StorageConfig config, HttpClient httpClient) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");

        String endpoint = config.endpoint().strip();
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        this.baseUri = endpoint + "/" + config.bucket();
    }

    public S3StorageConfig getConfig() {
        return config;
    }

    @Override
    public StorageMetadata put(String workspaceId, String objectKey, byte[] data, String contentType) throws IOException {
        String fullKey = buildFullKey(workspaceId, objectKey);
        URI uri = URI.create(baseUri + "/" + fullKey);

        String mime = contentType != null && !contentType.isBlank() ? contentType : "application/octet-stream";
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", mime)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(data));

        applyAuthHeaders(builder, "PUT", uri);

        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IOException("S3 PUT failed with status " + response.statusCode() + ": " + response.body());
            }

            String sha256 = LocalStorageService.calculateSha256(data);
            return new StorageMetadata(
                    workspaceId,
                    objectKey,
                    data.length,
                    sha256,
                    mime,
                    Instant.now().toString()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("S3 PUT interrupted", e);
        }
    }

    @Override
    public byte[] get(String workspaceId, String objectKey) throws IOException {
        String fullKey = buildFullKey(workspaceId, objectKey);
        URI uri = URI.create(baseUri + "/" + fullKey);

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .GET();

        applyAuthHeaders(builder, "GET", uri);

        try {
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 404) {
                throw new FileNotFoundException("S3 object not found: " + fullKey);
            }
            if (response.statusCode() >= 300) {
                throw new IOException("S3 GET failed with status " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("S3 GET interrupted", e);
        }
    }

    @Override
    public InputStream openStream(String workspaceId, String objectKey) throws IOException {
        byte[] bytes = get(workspaceId, objectKey);
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public boolean exists(String workspaceId, String objectKey) throws IOException {
        String fullKey = buildFullKey(workspaceId, objectKey);
        URI uri = URI.create(baseUri + "/" + fullKey);

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .method("HEAD", HttpRequest.BodyPublishers.noBody());

        applyAuthHeaders(builder, "HEAD", uri);

        try {
            HttpResponse<Void> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("S3 HEAD interrupted", e);
        }
    }

    @Override
    public boolean delete(String workspaceId, String objectKey) throws IOException {
        String fullKey = buildFullKey(workspaceId, objectKey);
        URI uri = URI.create(baseUri + "/" + fullKey);

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .DELETE();

        applyAuthHeaders(builder, "DELETE", uri);

        try {
            HttpResponse<Void> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200 || response.statusCode() == 204;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("S3 DELETE interrupted", e);
        }
    }

    @Override
    public long size(String workspaceId, String objectKey) throws IOException {
        String fullKey = buildFullKey(workspaceId, objectKey);
        URI uri = URI.create(baseUri + "/" + fullKey);

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .method("HEAD", HttpRequest.BodyPublishers.noBody());

        applyAuthHeaders(builder, "HEAD", uri);

        try {
            HttpResponse<Void> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 404) {
                throw new FileNotFoundException("S3 object not found: " + fullKey);
            }
            return response.headers().firstValueAsLong("Content-Length").orElse(0L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("S3 HEAD interrupted", e);
        }
    }

    @Override
    public List<String> listObjects(String workspaceId, String prefix) throws IOException {
        String wsPrefix = workspaceId + "/";
        String searchPrefix = prefix != null ? wsPrefix + prefix : wsPrefix;
        URI uri = URI.create(baseUri + "?prefix=" + searchPrefix);

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .GET();

        applyAuthHeaders(builder, "GET", uri);

        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IOException("S3 list failed with status " + response.statusCode());
            }

            List<String> results = new ArrayList<>();
            Pattern keyPattern = Pattern.compile("<Key>(.*?)</Key>");
            Matcher matcher = keyPattern.matcher(response.body());
            while (matcher.find()) {
                String full = matcher.group(1);
                if (full.startsWith(wsPrefix)) {
                    results.add(full.substring(wsPrefix.length()));
                }
            }
            Collections.sort(results);
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("S3 list interrupted", e);
        }
    }

    @Override
    public StorageMetadata getMetadata(String workspaceId, String objectKey) throws IOException {
        String fullKey = buildFullKey(workspaceId, objectKey);
        URI uri = URI.create(baseUri + "/" + fullKey);

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .method("HEAD", HttpRequest.BodyPublishers.noBody());

        applyAuthHeaders(builder, "HEAD", uri);

        try {
            HttpResponse<Void> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 404) {
                throw new FileNotFoundException("S3 object not found: " + fullKey);
            }

            long size = response.headers().firstValueAsLong("Content-Length").orElse(0L);
            String mime = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
            String lastModified = response.headers().firstValue("Last-Modified").orElse(Instant.now().toString());
            String etag = response.headers().firstValue("ETag").orElse("").replace("\"", "");

            return new StorageMetadata(
                    workspaceId,
                    objectKey,
                    size,
                    etag,
                    mime,
                    lastModified
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("S3 HEAD interrupted", e);
        }
    }

    @Override
    public int purgeWorkspace(String workspaceId) throws IOException {
        List<String> objects = listObjects(workspaceId, "");
        int deleted = 0;
        for (String key : objects) {
            if (delete(workspaceId, key)) {
                deleted++;
            }
        }
        return deleted;
    }

    private String buildFullKey(String workspaceId, String objectKey) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId must not be null or blank");
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey must not be null or blank");
        }
        String cleanKey = objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;
        return workspaceId + "/" + cleanKey;
    }

    private void applyAuthHeaders(HttpRequest.Builder builder, String method, URI uri) {
        if (!config.accessKey().isBlank() && !config.secretKey().isBlank()) {
            // Basic authentication or standard token header for S3 gateway
            String auth = Base64.getEncoder().encodeToString((config.accessKey() + ":" + config.secretKey()).getBytes());
            builder.header("Authorization", "Basic " + auth);
        }
    }
}
