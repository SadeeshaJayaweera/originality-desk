package com.writingassistant.server;

import com.google.gson.JsonObject;
import java.util.Map;

/**
 * Human reviewer decisions and notes contract.
 */
public interface ReviewOperations {
    Map<String, Object> review(Service.Actor actor, String analysisId, JsonObject input) throws Exception;
    Map<String, Object> reviews(Service.Actor actor, String analysisId, int page, int size) throws Exception;
}

