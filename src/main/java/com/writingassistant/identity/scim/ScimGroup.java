package com.writingassistant.identity.scim;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;

/**
 * SCIM 2.0 Group Resource according to RFC 7643 Section 4.2.
 */
public class ScimGroup implements Serializable {
    public static final String SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Group";

    private List<String> schemas = Collections.singletonList(SCHEMA);
    private String id;
    private String displayName;
    private List<ScimGroupMember> members = new ArrayList<>();
    private Map<String, Object> meta = new HashMap<>();

    public ScimGroup() {}

    public ScimGroup(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
        initMeta();
    }

    public void initMeta() {
        String now = Instant.now().toString();
        meta.put("resourceType", "Group");
        meta.put("created", now);
        meta.put("lastModified", now);
    }

    public List<String> getSchemas() { return schemas; }
    public void setSchemas(List<String> schemas) { this.schemas = schemas; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public List<ScimGroupMember> getMembers() { return members; }
    public void setMembers(List<ScimGroupMember> members) { this.members = members != null ? members : new ArrayList<>(); }

    public Map<String, Object> getMeta() { return meta; }
    public void setMeta(Map<String, Object> meta) { this.meta = meta != null ? meta : new HashMap<>(); }
}
