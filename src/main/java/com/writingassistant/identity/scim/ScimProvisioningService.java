package com.writingassistant.identity.scim;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Implements SCIM 2.0 RESTful Provisioning Protocol operations (RFC 7643 / RFC 7644)
 * with strict multi-tenant workspace isolation.
 */
public class ScimProvisioningService {

    private final ScimStore store;
    private static final Pattern FILTER_EQ_PATTERN = Pattern.compile("([\\w.]+)\\s+eq\\s+\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    public ScimProvisioningService(ScimStore store) {
        this.store = Objects.requireNonNull(store, "ScimStore cannot be null");
    }

    public ScimProvisioningService() {
        this(new InMemoryScimStore());
    }

    /**
     * Creates a new SCIM User within the given workspace tenant.
     */
    public ScimUser createUser(String workspaceId, ScimUser user) throws ScimException {
        Objects.requireNonNull(workspaceId, "workspaceId cannot be null");
        Objects.requireNonNull(user, "user cannot be null");

        if (user.getUserName() == null || user.getUserName().isBlank()) {
            throw new ScimException(400, "invalidValue", "Missing required field 'userName'");
        }

        if (store.getUserByUserName(workspaceId, user.getUserName()).isPresent()) {
            throw new ScimException(409, "uniqueness", "User already exists with userName: " + user.getUserName());
        }

        if (user.getId() == null || user.getId().isBlank()) {
            user.setId("usr_" + UUID.randomUUID().toString().replace("-", ""));
        }

        user.initMeta();
        return store.saveUser(workspaceId, user);
    }

    /**
     * Retrieves a user by ID within the workspace tenant.
     */
    public ScimUser getUser(String workspaceId, String userId) throws ScimException {
        return store.getUserById(workspaceId, userId)
                .orElseThrow(() -> new ScimException(404, "Resource " + userId + " not found"));
    }

    /**
     * Queries users with filtering and pagination within the workspace tenant.
     */
    public ScimListResponse<ScimUser> queryUsers(String workspaceId, String filter, int startIndex, int count) {
        List<ScimUser> all = store.listUsers(workspaceId);

        List<ScimUser> filtered;
        if (filter != null && !filter.isBlank()) {
            filtered = all.stream().filter(u -> matchesFilter(u, filter)).collect(Collectors.toList());
        } else {
            filtered = all;
        }

        int total = filtered.size();
        int pageStartIndex = Math.max(1, startIndex);
        int pageSize = count > 0 ? count : 50;

        int fromIndex = Math.min(pageStartIndex - 1, total);
        int toIndex = Math.min(fromIndex + pageSize, total);

        List<ScimUser> page = (fromIndex < total) ? filtered.subList(fromIndex, toIndex) : Collections.emptyList();
        return new ScimListResponse<>(page, total, pageStartIndex, page.size());
    }

    /**
     * Replaces all attributes of an existing SCIM User.
     */
    public ScimUser replaceUser(String workspaceId, String userId, ScimUser incoming) throws ScimException {
        ScimUser existing = getUser(workspaceId, userId);

        if (incoming.getUserName() != null && !incoming.getUserName().equalsIgnoreCase(existing.getUserName())) {
            if (store.getUserByUserName(workspaceId, incoming.getUserName()).isPresent()) {
                throw new ScimException(409, "uniqueness", "User already exists with userName: " + incoming.getUserName());
            }
        }

        incoming.setId(existing.getId());
        Map<String, Object> meta = new HashMap<>(existing.getMeta());
        meta.put("lastModified", Instant.now().toString());
        incoming.setMeta(meta);

        return store.saveUser(workspaceId, incoming);
    }

    /**
     * Applies partial patch operations (e.g., deactivation/reactivation, role updates) to an existing user.
     */
    public ScimUser patchUser(String workspaceId, String userId, List<ScimPatchOperation> operations) throws ScimException {
        ScimUser user = getUser(workspaceId, userId);

        if (operations != null) {
            for (ScimPatchOperation op : operations) {
                applyPatchOperation(user, op);
            }
        }

        user.getMeta().put("lastModified", Instant.now().toString());
        return store.saveUser(workspaceId, user);
    }

    private void applyPatchOperation(ScimUser user, ScimPatchOperation op) throws ScimException {
        String path = op.getPath() != null ? op.getPath().trim().toLowerCase() : "";
        Object val = op.getValue();

        if ("active".equals(path)) {
            if (val instanceof Boolean b) {
                user.setActive(b);
            } else if (val instanceof String s) {
                user.setActive(Boolean.parseBoolean(s));
            }
        } else if ("displayname".equals(path)) {
            if (val != null) user.setDisplayName(val.toString());
        } else if ("roles".equals(path)) {
            if (val instanceof List<?> list) {
                List<String> newRoles = new ArrayList<>();
                for (Object item : list) {
                    if (item != null) newRoles.add(item.toString());
                }
                user.setRoles(newRoles);
            } else if (val instanceof String s) {
                user.setRoles(Collections.singletonList(s));
            }
        } else if (val instanceof Map<?, ?> map && (path.isEmpty() || "replace".equalsIgnoreCase(op.getOp()))) {
            // Complex patch without path attribute
            if (map.containsKey("active")) {
                Object a = map.get("active");
                user.setActive(a instanceof Boolean ? (Boolean) a : Boolean.parseBoolean(a.toString()));
            }
            if (map.containsKey("displayName")) {
                user.setDisplayName(map.get("displayName").toString());
            }
        }
    }

    /**
     * Deletes a user by ID within the workspace tenant.
     */
    public void deleteUser(String workspaceId, String userId) throws ScimException {
        if (!store.deleteUser(workspaceId, userId)) {
            throw new ScimException(404, "User " + userId + " not found");
        }
    }

    /**
     * Creates a new SCIM Group.
     */
    public ScimGroup createGroup(String workspaceId, ScimGroup group) throws ScimException {
        if (group.getDisplayName() == null || group.getDisplayName().isBlank()) {
            throw new ScimException(400, "invalidValue", "Missing required field 'displayName'");
        }

        if (store.getGroupByName(workspaceId, group.getDisplayName()).isPresent()) {
            throw new ScimException(409, "uniqueness", "Group already exists with displayName: " + group.getDisplayName());
        }

        if (group.getId() == null || group.getId().isBlank()) {
            group.setId("grp_" + UUID.randomUUID().toString().replace("-", ""));
        }

        group.initMeta();
        return store.saveGroup(workspaceId, group);
    }

    /**
     * Retrieves a group by ID.
     */
    public ScimGroup getGroup(String workspaceId, String groupId) throws ScimException {
        return store.getGroupById(workspaceId, groupId)
                .orElseThrow(() -> new ScimException(404, "Group " + groupId + " not found"));
    }

    /**
     * Lists groups in the workspace tenant.
     */
    public ScimListResponse<ScimGroup> queryGroups(String workspaceId, String filter, int startIndex, int count) {
        List<ScimGroup> all = store.listGroups(workspaceId);
        int total = all.size();
        int pageStartIndex = Math.max(1, startIndex);
        int pageSize = count > 0 ? count : 50;

        int fromIndex = Math.min(pageStartIndex - 1, total);
        int toIndex = Math.min(fromIndex + pageSize, total);

        List<ScimGroup> page = (fromIndex < total) ? all.subList(fromIndex, toIndex) : Collections.emptyList();
        return new ScimListResponse<>(page, total, pageStartIndex, page.size());
    }

    /**
     * Deletes a group by ID.
     */
    public void deleteGroup(String workspaceId, String groupId) throws ScimException {
        if (!store.deleteGroup(workspaceId, groupId)) {
            throw new ScimException(404, "Group " + groupId + " not found");
        }
    }

    /**
     * Returns compliant RFC 7643 ServiceProviderConfig metadata.
     */
    public Map<String, Object> getServiceProviderConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("schemas", Collections.singletonList("urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"));
        config.put("documentationUri", "https://originalitydesk.example.edu/docs/scim");

        Map<String, Object> patch = Map.of("supported", true);
        Map<String, Object> bulk = Map.of("supported", false, "maxOperations", 0, "maxPayloadSize", 0);
        Map<String, Object> filter = Map.of("supported", true, "maxResults", 100);
        Map<String, Object> changePassword = Map.of("supported", false);
        Map<String, Object> sort = Map.of("supported", false);
        Map<String, Object> etag = Map.of("supported", false);

        config.put("patch", patch);
        config.put("bulk", bulk);
        config.put("filter", filter);
        config.put("changePassword", changePassword);
        config.put("sort", sort);
        config.put("etag", etag);

        Map<String, Object> authScheme = Map.of(
                "name", "OAuth Bearer Token",
                "description", "Authentication scheme using the OAuth Bearer Token Standard",
                "specUri", "http://www.rfc-editor.org/info/rfc6750",
                "type", "oauthbearertoken",
                "primary", true
        );
        config.put("authenticationSchemes", Collections.singletonList(authScheme));

        return config;
    }

    private boolean matchesFilter(ScimUser u, String filter) {
        Matcher m = FILTER_EQ_PATTERN.matcher(filter);
        if (m.find()) {
            String field = m.group(1).trim().toLowerCase();
            String val = m.group(2).trim();

            if ("username".equals(field)) {
                return val.equalsIgnoreCase(u.getUserName());
            } else if ("emails.value".equals(field) || "email".equals(field)) {
                return u.getEmails().stream().anyMatch(e -> val.equalsIgnoreCase(e.getValue()));
            } else if ("displayname".equals(field)) {
                return val.equalsIgnoreCase(u.getDisplayName());
            }
        }
        return true;
    }
}
