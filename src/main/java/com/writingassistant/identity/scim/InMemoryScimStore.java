package com.writingassistant.identity.scim;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory store for SCIM 2.0 resources with strict workspace tenant boundaries.
 */
public class InMemoryScimStore implements ScimStore {

    private final ConcurrentHashMap<String, Map<String, ScimUser>> usersByWorkspace = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, ScimGroup>> groupsByWorkspace = new ConcurrentHashMap<>();

    private Map<String, ScimUser> getWorkspaceUsers(String workspaceId) {
        return usersByWorkspace.computeIfAbsent(workspaceId, k -> new ConcurrentHashMap<>());
    }

    private Map<String, ScimGroup> getWorkspaceGroups(String workspaceId) {
        return groupsByWorkspace.computeIfAbsent(workspaceId, k -> new ConcurrentHashMap<>());
    }

    @Override
    public ScimUser saveUser(String workspaceId, ScimUser user) {
        getWorkspaceUsers(workspaceId).put(user.getId(), user);
        return user;
    }

    @Override
    public Optional<ScimUser> getUserById(String workspaceId, String userId) {
        return Optional.ofNullable(getWorkspaceUsers(workspaceId).get(userId));
    }

    @Override
    public Optional<ScimUser> getUserByUserName(String workspaceId, String userName) {
        if (userName == null) return Optional.empty();
        for (ScimUser u : getWorkspaceUsers(workspaceId).values()) {
            if (userName.equalsIgnoreCase(u.getUserName())) {
                return Optional.of(u);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<ScimUser> listUsers(String workspaceId) {
        return new ArrayList<>(getWorkspaceUsers(workspaceId).values());
    }

    @Override
    public boolean deleteUser(String workspaceId, String userId) {
        return getWorkspaceUsers(workspaceId).remove(userId) != null;
    }

    @Override
    public ScimGroup saveGroup(String workspaceId, ScimGroup group) {
        getWorkspaceGroups(workspaceId).put(group.getId(), group);
        return group;
    }

    @Override
    public Optional<ScimGroup> getGroupById(String workspaceId, String groupId) {
        return Optional.ofNullable(getWorkspaceGroups(workspaceId).get(groupId));
    }

    @Override
    public Optional<ScimGroup> getGroupByName(String workspaceId, String displayName) {
        if (displayName == null) return Optional.empty();
        for (ScimGroup g : getWorkspaceGroups(workspaceId).values()) {
            if (displayName.equalsIgnoreCase(g.getDisplayName())) {
                return Optional.of(g);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<ScimGroup> listGroups(String workspaceId) {
        return new ArrayList<>(getWorkspaceGroups(workspaceId).values());
    }

    @Override
    public boolean deleteGroup(String workspaceId, String groupId) {
        return getWorkspaceGroups(workspaceId).remove(groupId) != null;
    }
}
