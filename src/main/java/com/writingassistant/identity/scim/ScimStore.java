package com.writingassistant.identity.scim;

import java.util.List;
import java.util.Optional;

/**
 * Storage contract for SCIM 2.0 institutional user and group resources
 * strictly partitioned by workspace tenant.
 */
public interface ScimStore {
    ScimUser saveUser(String workspaceId, ScimUser user);
    Optional<ScimUser> getUserById(String workspaceId, String userId);
    Optional<ScimUser> getUserByUserName(String workspaceId, String userName);
    List<ScimUser> listUsers(String workspaceId);
    boolean deleteUser(String workspaceId, String userId);

    ScimGroup saveGroup(String workspaceId, ScimGroup group);
    Optional<ScimGroup> getGroupById(String workspaceId, String groupId);
    Optional<ScimGroup> getGroupByName(String workspaceId, String displayName);
    List<ScimGroup> listGroups(String workspaceId);
    boolean deleteGroup(String workspaceId, String groupId);
}
