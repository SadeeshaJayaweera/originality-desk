package com.writingassistant.server;

import java.time.Instant;
import java.util.Map;

/**
 * Document upload, ingestion, retrieval, lifecycle, and deletion operations contract.
 */
public interface DocumentOperations {
    Map<String, Object> upload(Service.Actor actor, String filename, String kind, String mime, byte[] bytes) throws Exception;
    Map<String, Object> documents(Service.Actor actor, String kind, int page, int size) throws Exception;
    Map<String, Object> document(Service.Actor actor, String id) throws Exception;
    void deleteDocument(Service.Actor actor, String documentId) throws Exception;
    int expireDocuments(int retentionDays, Instant currentTime) throws Exception;
}
