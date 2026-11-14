package com.writingassistant.storage;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/**
 * High-performance, filesystem-backed implementation of StorageService.
 * Enforces strict tenant separation via directory sandboxing and prevents path traversal attacks.
 */
public class LocalStorageService implements StorageService {

    private final Path rootDir;

    public LocalStorageService(Path rootDir) throws IOException {
        this.rootDir = Objects.requireNonNull(rootDir, "rootDir must not be null").toAbsolutePath().normalize();
        Files.createDirectories(this.rootDir);
        if (Files.isSymbolicLink(this.rootDir)) {
            throw new IOException("Storage root directory must not be a symbolic link: " + rootDir);
        }
    }

    public Path getRootDir() {
        return rootDir;
    }

    @Override
    public StorageMetadata put(String workspaceId, String objectKey, byte[] data, String contentType) throws IOException {
        Objects.requireNonNull(data, "data must not be null");
        Path targetPath = resolvePath(workspaceId, objectKey);

        Files.createDirectories(targetPath.getParent());

        // Atomic write via temporary file
        Path tempPath = targetPath.getParent().resolve(targetPath.getFileName().toString() + ".tmp." + UUID.randomUUID());
        Files.write(tempPath, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        String sha256 = calculateSha256(data);
        String lastModified = Instant.now().toString();

        return new StorageMetadata(
                workspaceId,
                objectKey,
                data.length,
                sha256,
                contentType != null ? contentType : "application/octet-stream",
                lastModified
        );
    }

    @Override
    public byte[] get(String workspaceId, String objectKey) throws IOException {
        Path path = resolvePath(workspaceId, objectKey);
        if (!Files.exists(path) || Files.isDirectory(path)) {
            throw new FileNotFoundException("Object not found: " + objectKey + " in workspace: " + workspaceId);
        }
        return Files.readAllBytes(path);
    }

    @Override
    public InputStream openStream(String workspaceId, String objectKey) throws IOException {
        Path path = resolvePath(workspaceId, objectKey);
        if (!Files.exists(path) || Files.isDirectory(path)) {
            throw new FileNotFoundException("Object not found: " + objectKey + " in workspace: " + workspaceId);
        }
        return Files.newInputStream(path, StandardOpenOption.READ);
    }

    @Override
    public boolean exists(String workspaceId, String objectKey) throws IOException {
        Path path = resolvePath(workspaceId, objectKey);
        return Files.exists(path) && !Files.isDirectory(path);
    }

    @Override
    public boolean delete(String workspaceId, String objectKey) throws IOException {
        Path path = resolvePath(workspaceId, objectKey);
        return Files.deleteIfExists(path);
    }

    @Override
    public long size(String workspaceId, String objectKey) throws IOException {
        Path path = resolvePath(workspaceId, objectKey);
        if (!Files.exists(path) || Files.isDirectory(path)) {
            throw new FileNotFoundException("Object not found: " + objectKey + " in workspace: " + workspaceId);
        }
        return Files.size(path);
    }

    @Override
    public List<String> listObjects(String workspaceId, String prefix) throws IOException {
        Path wsDir = getWorkspaceDir(workspaceId);
        if (!Files.exists(wsDir)) {
            return List.of();
        }

        List<String> keys = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(wsDir)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                String relativeKey = wsDir.relativize(path).toString().replace('\\', '/');
                if (prefix == null || prefix.isBlank() || relativeKey.startsWith(prefix)) {
                    keys.add(relativeKey);
                }
            }
        }
        Collections.sort(keys);
        return keys;
    }

    @Override
    public StorageMetadata getMetadata(String workspaceId, String objectKey) throws IOException {
        Path path = resolvePath(workspaceId, objectKey);
        if (!Files.exists(path) || Files.isDirectory(path)) {
            throw new FileNotFoundException("Object not found: " + objectKey + " in workspace: " + workspaceId);
        }

        byte[] bytes = Files.readAllBytes(path);
        String sha256 = calculateSha256(bytes);
        String lastModified = Files.getLastModifiedTime(path).toInstant().toString();

        return new StorageMetadata(
                workspaceId,
                objectKey,
                bytes.length,
                sha256,
                "application/octet-stream",
                lastModified
        );
    }

    @Override
    public int purgeWorkspace(String workspaceId) throws IOException {
        Path wsDir = getWorkspaceDir(workspaceId);
        if (!Files.exists(wsDir)) {
            return 0;
        }

        int count = 0;
        try (Stream<Path> stream = Files.walk(wsDir)) {
            List<Path> all = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path p : all) {
                if (Files.isRegularFile(p)) {
                    count++;
                }
                Files.deleteIfExists(p);
            }
        }
        return count;
    }

    private Path getWorkspaceDir(String workspaceId) throws IOException {
        validateIdentifier(workspaceId, "workspaceId");
        Path wsDir = rootDir.resolve(workspaceId).normalize();
        if (!wsDir.startsWith(rootDir)) {
            throw new SecurityException("Path traversal attempt in workspaceId: " + workspaceId);
        }
        return wsDir;
    }

    private Path resolvePath(String workspaceId, String objectKey) throws IOException {
        validateIdentifier(workspaceId, "workspaceId");
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey must not be null or blank");
        }

        Path wsDir = getWorkspaceDir(workspaceId);
        Path resolved = wsDir.resolve(objectKey).normalize();

        // Enforce boundary check to prevent ../ escaping workspace directory
        if (!resolved.startsWith(wsDir)) {
            throw new SecurityException("Path traversal attempt detected in objectKey: " + objectKey);
        }
        if (Files.isSymbolicLink(resolved)) {
            throw new SecurityException("Symbolic links are prohibited in object storage: " + resolved);
        }

        return resolved;
    }

    private void validateIdentifier(String id, String fieldName) {
        if (id == null || id.isBlank() || !id.matches("^[A-Za-z0-9_-]{1,64}$")) {
            throw new IllegalArgumentException("Invalid " + fieldName + ": must be 1-64 alphanumeric characters, dashes, or underscores");
        }
    }

    static String calculateSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
