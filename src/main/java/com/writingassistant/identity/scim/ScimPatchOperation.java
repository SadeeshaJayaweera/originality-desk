package com.writingassistant.identity.scim;

import java.io.Serializable;

/**
 * SCIM 2.0 Patch Operation according to RFC 7644 Section 3.5.2.
 */
public class ScimPatchOperation implements Serializable {
    private String op; // "add", "replace", "remove"
    private String path;
    private Object value;

    public ScimPatchOperation() {}

    public ScimPatchOperation(String op, String path, Object value) {
        this.op = op;
        this.path = path;
        this.value = value;
    }

    public String getOp() { return op; }
    public void setOp(String op) { this.op = op; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public Object getValue() { return value; }
    public void setValue(Object value) { this.value = value; }
}
