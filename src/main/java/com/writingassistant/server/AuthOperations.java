package com.writingassistant.server;

import com.google.gson.JsonObject;
import java.util.Map;

/**
 * Authentication and identity service contract.
 */
public interface AuthOperations {
    Service.Authentication register(JsonObject input) throws Exception;
    Service.Authentication login(JsonObject input) throws Exception;
    Service.Actor authenticate(String token) throws Exception;
    void logout(Service.Actor actor, String token) throws Exception;
    Map<String, Object> me(Service.Actor actor) throws Exception;
}
