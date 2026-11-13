package com.writingassistant.server;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.Map;

/**
 * Document upload, ingestion, retrieval, lifecycle, external scholarly reference search/import,
 * and deletion operations contract.
 */
public interface DocumentOperations {
    Map<String, Object> upload(Service.Actor actor, String filename, String kind, String mime, byte[] bytes) throws Exception;
    Map<String, Object> documents(Service.Actor actor, String kind, int page, int size) throws Exception;
    Map<String, Object> document(Service.Actor actor, String id) throws Exception;
    void deleteDocument(Service.Actor actor, String documentId) throws Exception;
    int expireDocuments(int retentionDays, Instant currentTime) throws Exception;

    Map<String, Object> searchExternalReferences(String query, int limit) throws Exception;
    Map<String, Object> resolveExternalReferenceByDoi(String doi) throws Exception;
    Map<String, Object> importExternalReference(Service.Actor actor, JsonObject workJson) throws Exception;
    Map<String, Object> getExternalReferenceEvidence(Service.Actor actor, String documentId) throws Exception;
}
