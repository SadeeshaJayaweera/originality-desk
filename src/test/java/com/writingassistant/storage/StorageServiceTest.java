package com.writingassistant.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

class StorageServiceTest {

    @Test
    @DisplayName("LocalStorageService puts, retrieves, checks size, and computes SHA-256 correctly")
    void localStorageBasicCrud(@TempDir Path tempDir) throws Exception {
        LocalStorageService storage = new LocalStorageService(tempDir);

        byte[] content = "Hello Academic Integrity!".getBytes(StandardCharsets.UTF_8);
        StorageMetadata meta = storage.put("ws-100", "doc-1.txt", content, "text/plain");

        assertNotNull(meta);
        assertEquals("ws-100", meta.workspaceId());
        assertEquals("doc-1.txt", meta.objectKey());
        assertEquals(content.length, meta.sizeBytes());
        assertEquals("text/plain", meta.contentType());
        assertNotNull(meta.sha256());

        assertTrue(storage.exists("ws-100", "doc-1.txt"));
        assertEquals(content.length, storage.size("ws-100", "doc-1.txt"));

        byte[] retrieved = storage.get("ws-100", "doc-1.txt");
        assertArrayEquals(content, retrieved);

        try (InputStream is = storage.openStream("ws-100", "doc-1.txt")) {
            byte[] streamBytes = is.readAllBytes();
            assertArrayEquals(content, streamBytes);
        }

        assertTrue(storage.delete("ws-100", "doc-1.txt"));
        assertFalse(storage.exists("ws-100", "doc-1.txt"));
        assertFalse(storage.delete("ws-100", "doc-1.txt"));
    }

    @Test
    @DisplayName("LocalStorageService enforces strict workspace boundary and prevents path traversal")
    void localStorageSecurityAndIsolation(@TempDir Path tempDir) throws Exception {
        LocalStorageService storage = new LocalStorageService(tempDir);

        byte[] dataA = "Secret Data A".getBytes(StandardCharsets.UTF_8);
        storage.put("ws-alpha", "file.txt", dataA, "text/plain");

        // Workspace Beta cannot read Workspace Alpha's file
        assertFalse(storage.exists("ws-beta", "file.txt"));
        assertThrows(FileNotFoundException.class, () -> storage.get("ws-beta", "file.txt"));

        // Path traversal attempts must be blocked
        assertThrows(SecurityException.class, () -> storage.get("ws-alpha", "../other.txt"));
        assertThrows(SecurityException.class, () -> storage.put("ws-alpha", "../../root.txt", dataA, "text/plain"));
        assertThrows(IllegalArgumentException.class, () -> storage.get("../etc", "passwd"));
    }

    @Test
    @DisplayName("LocalStorageService lists objects by prefix and purges workspace cleanly")
    void localStorageListingAndPurge(@TempDir Path tempDir) throws Exception {
        LocalStorageService storage = new LocalStorageService(tempDir);

        storage.put("ws-list", "submissions/sub-1.pdf", "Sub 1".getBytes(StandardCharsets.UTF_8), "application/pdf");
        storage.put("ws-list", "submissions/sub-2.pdf", "Sub 2".getBytes(StandardCharsets.UTF_8), "application/pdf");
        storage.put("ws-list", "references/ref-1.txt", "Ref 1".getBytes(StandardCharsets.UTF_8), "text/plain");

        List<String> all = storage.listObjects("ws-list", "");
        assertEquals(3, all.size());

        List<String> subs = storage.listObjects("ws-list", "submissions/");
        assertEquals(2, subs.size());
        assertTrue(subs.contains("submissions/sub-1.pdf"));
        assertTrue(subs.contains("submissions/sub-2.pdf"));

        int purged = storage.purgeWorkspace("ws-list");
        assertEquals(3, purged);
        assertTrue(storage.listObjects("ws-list", "").isEmpty());
    }

    @Test
    @DisplayName("S3CompatibleStorageService builds tenant-isolated S3 keys and executes S3 REST requests")
    void s3StorageMockExecution() throws Exception {
        Map<String, byte[]> s3MockStore = new HashMap<>();

        // Test HttpClient that mocks S3 REST endpoint without network sockets
        HttpClient mockClient = new HttpClient() {
            @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
            @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
            @Override public Redirect followRedirects() { return Redirect.NEVER; }
            @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
            @Override public SSLContext sslContext() { return null; }
            @Override public SSLParameters sslParameters() { return null; }
            @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
            @Override public Version version() { return Version.HTTP_1_1; }
            @Override public Optional<Executor> executor() { return Optional.empty(); }

            @SuppressWarnings("unchecked")
            @Override
            public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseHandler) {
                String path = request.uri().getPath(); // e.g. /my-bucket/ws-1/doc.txt
                String method = request.method();
                int status = 200;
                byte[] body = new byte[0];

                if ("PUT".equals(method)) {
                    s3MockStore.put(path, "mock-stored-content".getBytes(StandardCharsets.UTF_8));
                    status = 200;
                } else if ("GET".equals(method)) {
                    if (s3MockStore.containsKey(path)) {
                        body = s3MockStore.get(path);
                        status = 200;
                    } else {
                        status = 404;
                    }
                } else if ("HEAD".equals(method)) {
                    status = s3MockStore.containsKey(path) ? 200 : 404;
                } else if ("DELETE".equals(method)) {
                    s3MockStore.remove(path);
                    status = 204;
                }

                int finalStatus = status;
                byte[] finalBody = body;
                return new HttpResponse<>() {
                    @Override public int statusCode() { return finalStatus; }
                    @Override public HttpRequest request() { return request; }
                    @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
                    @Override public HttpHeaders headers() {
                        return HttpHeaders.of(Map.of(
                                "Content-Length", List.of(String.valueOf(finalBody.length)),
                                "Content-Type", List.of("text/plain")
                        ), (k, v) -> true);
                    }
                    @Override public T body() {
                        if (responseHandler == HttpResponse.BodyHandlers.ofByteArray()) {
                            return (T) finalBody;
                        } else if (responseHandler == HttpResponse.BodyHandlers.ofString()) {
                            return (T) new String(finalBody, StandardCharsets.UTF_8);
                        }
                        return null;
                    }
                    @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
                    @Override public URI uri() { return request.uri(); }
                    @Override public Version version() { return Version.HTTP_1_1; }
                };
            }

            @Override
            public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseHandler) {
                return CompletableFuture.completedFuture(send(request, responseHandler));
            }
            @Override
            public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
                return sendAsync(request, responseHandler);
            }
        };

        S3StorageConfig config = S3StorageConfig.forLocalMinio("http://localhost:9000", "test-bucket");
        S3CompatibleStorageService s3 = new S3CompatibleStorageService(config, mockClient);

        // 1. Put
        byte[] payload = "S3 Cloud Stored Essay".getBytes(StandardCharsets.UTF_8);
        StorageMetadata meta = s3.put("tenant-99", "essay.pdf", payload, "application/pdf");
        assertNotNull(meta);
        assertEquals("tenant-99", meta.workspaceId());
        assertEquals("essay.pdf", meta.objectKey());

        // 2. Exists & Size
        assertTrue(s3.exists("tenant-99", "essay.pdf"));
        assertFalse(s3.exists("tenant-99", "nonexistent.pdf"));

        // 3. Get
        byte[] retrieved = s3.get("tenant-99", "essay.pdf");
        assertNotNull(retrieved);

        // 4. Delete
        assertTrue(s3.delete("tenant-99", "essay.pdf"));
        assertFalse(s3.exists("tenant-99", "essay.pdf"));
    }

    @Test
    @DisplayName("StorageServiceFactory instantiates correct backend from properties")
    void storageServiceFactoryCreation(@TempDir Path tempDir) throws Exception {
        // Local backend
        Properties localProps = new Properties();
        localProps.setProperty("storage.backend", "local");
        localProps.setProperty("storage.local.dir", tempDir.toAbsolutePath().toString());

        StorageService localService = StorageServiceFactory.create(localProps);
        assertInstanceOf(LocalStorageService.class, localService);

        // S3 backend
        Properties s3Props = new Properties();
        s3Props.setProperty("storage.backend", "s3");
        s3Props.setProperty("storage.s3.endpoint", "http://seaweedfs:8333");
        s3Props.setProperty("storage.s3.bucket", "orig-desk");

        StorageService s3Service = StorageServiceFactory.create(s3Props);
        assertInstanceOf(S3CompatibleStorageService.class, s3Service);
    }
}
