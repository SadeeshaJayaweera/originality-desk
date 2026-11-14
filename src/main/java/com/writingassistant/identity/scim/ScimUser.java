package com.writingassistant.identity.scim;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;

/**
 * SCIM 2.0 User Resource according to RFC 7643 Section 4.1.
 */
public class ScimUser implements Serializable {
    public static final String SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";

    private List<String> schemas = Collections.singletonList(SCHEMA);
    private String id;
    private String externalId;
    private String userName;
    private ScimName name;
    private String displayName;
    private List<ScimEmail> emails = new ArrayList<>();
    private List<String> roles = new ArrayList<>();
    private boolean active = true;
    private Map<String, Object> meta = new HashMap<>();

    public ScimUser() {}

    public ScimUser(String id, String userName, String displayName, String email, String role, boolean active) {
        this.id = id;
        this.userName = userName;
        this.displayName = displayName;
        if (email != null && !email.isBlank()) {
            this.emails.add(new ScimEmail(email, "work", true));
        }
        if (role != null && !role.isBlank()) {
            this.roles.add(role);
        }
        this.active = active;
        initMeta();
    }

    public void initMeta() {
        String now = Instant.now().toString();
        meta.put("resourceType", "User");
        meta.put("created", now);
        meta.put("lastModified", now);
    }

    public List<String> getSchemas() { return schemas; }
    public void setSchemas(List<String> schemas) { this.schemas = schemas; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public ScimName getName() { return name; }
    public void setName(ScimName name) { this.name = name; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public List<ScimEmail> getEmails() { return emails; }
    public void setEmails(List<ScimEmail> emails) { this.emails = emails != null ? emails : new ArrayList<>(); }

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles != null ? roles : new ArrayList<>(); }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Map<String, Object> getMeta() { return meta; }
    public void setMeta(Map<String, Object> meta) { this.meta = meta != null ? meta : new HashMap<>(); }

    public String getPrimaryEmail() {
        if (emails == null || emails.isEmpty()) return null;
        for (ScimEmail em : emails) {
            if (em.isPrimary()) return em.getValue();
        }
        return emails.get(0).getValue();
    }

    public String getPrimaryRole() {
        if (roles == null || roles.isEmpty()) return "MEMBER";
        return roles.get(0);
    }
}
