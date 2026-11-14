package com.writingassistant.identity.scim;

import java.io.Serializable;
import java.util.Objects;

/**
 * SCIM 2.0 Email representation according to RFC 7643 Section 4.1.2.
 */
public class ScimEmail implements Serializable {
    private String value;
    private String type;
    private boolean primary;

    public ScimEmail() {}

    public ScimEmail(String value, String type, boolean primary) {
        this.value = value;
        this.type = type != null ? type : "work";
        this.primary = primary;
    }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isPrimary() { return primary; }
    public void setPrimary(boolean primary) { this.primary = primary; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScimEmail scimEmail)) return false;
        return primary == scimEmail.primary && Objects.equals(value, scimEmail.value) && Objects.equals(type, scimEmail.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, type, primary);
    }
}
