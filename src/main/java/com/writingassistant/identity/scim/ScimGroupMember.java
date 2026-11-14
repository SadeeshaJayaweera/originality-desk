package com.writingassistant.identity.scim;

import java.io.Serializable;
import java.util.Objects;

/**
 * SCIM 2.0 Group Member representation according to RFC 7643 Section 4.2.
 */
public class ScimGroupMember implements Serializable {
    private String value;
    private String display;
    private String $ref;
    private String type;

    public ScimGroupMember() {}

    public ScimGroupMember(String value, String display) {
        this.value = value;
        this.display = display;
        this.type = "User";
    }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getDisplay() { return display; }
    public void setDisplay(String display) { this.display = display; }

    public String get$ref() { return $ref; }
    public void set$ref(String $ref) { this.$ref = $ref; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScimGroupMember that)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
