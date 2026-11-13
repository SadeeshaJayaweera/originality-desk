package com.writingassistant.server;

import java.util.Map;

/**
 * Administrative metrics, dashboard, and system health operations contract.
 */
public interface SystemOperations {
    Map<String, Object> dashboard(Service.Actor actor) throws Exception;
    Map<String, Object> adminList(Service.Actor actor, String type, int page, int size) throws Exception;
    Map<String, Object> adminHealth(Service.Actor actor, boolean workerAlive) throws Exception;
    boolean ready();
}
