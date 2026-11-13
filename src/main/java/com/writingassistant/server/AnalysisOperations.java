package com.writingassistant.server;

import com.google.gson.JsonObject;
import java.util.Map;

/**
 * Originality analysis submission, polling, cancellation, and report snapshot contract.
 */
public interface AnalysisOperations {
    Map<String, Object> createAnalysis(Service.Actor actor, JsonObject input, String idempotencyKey) throws Exception;
    Map<String, Object> analyses(Service.Actor actor, int page, int size) throws Exception;
    Map<String, Object> analysis(Service.Actor actor, String id, boolean includeMatches) throws Exception;
    Map<String, Object> cancel(Service.Actor actor, String id) throws Exception;
    JsonObject report(Service.Actor actor, String id) throws Exception;
}
