package com.writingassistant.server;

import com.google.gson.JsonObject;
import java.util.Map;

/**
 * Human reviewer decisions, conflict resolution, and review workflow contract.
 */
public interface ReviewOperations {
    Map<String, Object> review(Service.Actor actor, String analysisId, JsonObject input) throws Exception;
    Map<String, Object> reviews(Service.Actor actor, String analysisId, int page, int size) throws Exception;
    Map<String, Object> reviewSummary(Service.Actor actor, String analysisId) throws Exception;
    Map<String, Object> resolveConflict(Service.Actor actor, String analysisId, JsonObject input) throws Exception;
}


