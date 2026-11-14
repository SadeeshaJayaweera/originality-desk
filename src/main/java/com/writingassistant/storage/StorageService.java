package com.writingassistant.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Pluggable object storage abstraction decoupling document, extract, and report storage
 * from the underlying physical storage mechanism (local filesystem or S3-compatible cloud storage).
 * All operations mandate workspace identification to enforce strict multi-tenant isolation.
 */
public interface StorageService extends AutoCloseable {

    /**
     * Stores an object within the specified workspace namespace.
     *
     * @param workspaceId Tenant/workspace identifier.
     * @param objectKey   Unique object key within the workspace namespace.
     * @param data        Binary content bytes.
     * @param contentType MIME type of the content.
     * @return StorageMetadata record describing the stored object including its SHA-256 hash.
     * @throws IOException on storage failure.
     */
    StorageMetadata put(String workspaceId, String objectKey, byte[] data, String contentType) throws IOException;

    /**
     * Retrieves the binary content bytes of an object.
     *
     * @param workspaceId Tenant/workspace identifier.
     * @param objectKey   Unique object key within the workspace namespace.
     * @return Raw content bytes.
     * @throws IOException if object does not exist or read fails.
     */
    byte[] get(String workspaceId, String objectKey) throws IOException;

    /**
     * Opens an input stream for reading the object's content.
     */
    InputStream openStream(String workspaceId, String objectKey) throws IOException;

    /**
     * Checks if an object exists in the workspace storage.
     */
    boolean exists(String workspaceId, String objectKey) throws IOException;

    /**
     * Deletes an object from workspace storage.
     *
     * @return true if the object was deleted; false if it did not exist.
     */
    boolean delete(String workspaceId, String objectKey) throws IOException;

    /**
     * Returns the size of an object in bytes.
     */
    long size(String workspaceId, String objectKey) throws IOException;

    /**
     * Lists object keys within the workspace matching an optional key prefix.
     */
    List<String> listObjects(String workspaceId, String prefix) throws IOException;

    /**
     * Retrieves metadata for an object without reading its full payload.
     */
    StorageMetadata getMetadata(String workspaceId, String objectKey) throws IOException;

    /**
     * Deletes all objects in the specified workspace (e.g. on workspace decommission).
     *
     * @return Number of objects purged.
     */
    int purgeWorkspace(String workspaceId) throws IOException;

    @Override
    default void close() throws Exception {}
}
