# Object Storage Abstraction Architecture

## 1. Executive Summary and Storage Strategy

Originality Desk decouples document uploads, extracted text representations, and generated evaluation reports from local filesystem dependencies through a unified object storage abstraction.

### Architectural Principles
- **Multi-Tenant Namespace Isolation**: All storage operations mandate a `workspaceId` parameter. Storage keys are automatically partitioned by tenant (`{workspaceId}/{objectKey}`), preventing cross-tenant data leakage.
- **Pluggable Backends**: Switching between embedded local storage and cloud object storage requires only a configuration property change (`storage.backend=local` vs. `storage.backend=s3`), requiring zero code modifications.
- **Zero Heavyweight SDK Dependencies**: The S3-compatible backend is built on pure Java `java.net.http.HttpClient`, eliminating bloated AWS SDK jars and vendor lock-in.
- **Path Traversal Defense**: The local filesystem backend normalizes all paths and strictly verifies that resolved files remain within the authorized workspace jail. Symbolic links are strictly prohibited.
- **Atomic Writes and Content-Addressability**: Binary uploads are written via atomic file moves with automatic SHA-256 cryptographic digest computation.

---

## 2. Storage Service Contract (`StorageService`)

Located in package `com.writingassistant.storage`:

```java
public interface StorageService extends AutoCloseable {
    StorageMetadata put(String workspaceId, String objectKey, byte[] data, String contentType) throws IOException;
    byte[] get(String workspaceId, String objectKey) throws IOException;
    InputStream openStream(String workspaceId, String objectKey) throws IOException;
    boolean exists(String workspaceId, String objectKey) throws IOException;
    boolean delete(String workspaceId, String objectKey) throws IOException;
    long size(String workspaceId, String objectKey) throws IOException;
    List<String> listObjects(String workspaceId, String prefix) throws IOException;
    StorageMetadata getMetadata(String workspaceId, String objectKey) throws IOException;
    int purgeWorkspace(String workspaceId) throws IOException;
}
```

---

## 3. Supported Storage Backends

### 3.1 Local Filesystem Backend (`LocalStorageService`)
- **Use Case**: Single-node server deployments, desktop application, and offline development.
- **Directory Layout**:
  ```
  data/documents/
  ├── {workspace_id_1}/
  │   ├── submissions/
  │   └── references/
  └── {workspace_id_2}/
      └── ...
  ```
- **Security Sandboxing**:
  ```java
  Path wsDir = rootDir.resolve(workspaceId).normalize();
  Path resolved = wsDir.resolve(objectKey).normalize();
  if (!resolved.startsWith(wsDir)) {
      throw new SecurityException("Path traversal attempt detected");
  }
  ```

### 3.2 S3-Compatible Cloud Backend (`S3CompatibleStorageService`)
- **Use Case**: Clustered, multi-server institutional production environments.
- **Target Providers**: SeaweedFS, MinIO, Ceph, LocalStack, and AWS S3.
- **S3 Object Key Layout**: `{bucket}/{workspaceId}/{objectKey}`
- **Authentication**: Supports standard S3 gateway headers and Basic / Bearer token authentication.

---

## 4. Configuration Reference

```properties
# Backend selection: 'local' (default) or 's3'
storage.backend=local

# Local storage configuration
storage.local.dir=data/documents

# S3-compatible cloud storage configuration
storage.s3.endpoint=http://seaweedfs.internal:8333
storage.s3.bucket=originality-desk
storage.s3.region=us-east-1
storage.s3.accessKey=minioadmin
storage.s3.secretKey=minioadmin
storage.s3.pathStyle=true
```

---

## 5. Verification and Test Suite

Verified via `StorageServiceTest`:
1. `localStorageBasicCrud`: Tests put, get, openStream, size, metadata, and deletion on local storage.
2. `localStorageSecurityAndIsolation`: Verifies cross-workspace isolation and rejection of path traversal (`../`) attacks.
3. `localStorageListingAndPurge`: Verifies prefix-filtered listing and clean workspace purging.
4. `s3StorageMockExecution`: Verifies S3 REST calls (PUT, GET, HEAD, DELETE) and tenant key isolation.
5. `storageServiceFactoryCreation`: Verifies dynamic backend instantiation from configuration properties.
