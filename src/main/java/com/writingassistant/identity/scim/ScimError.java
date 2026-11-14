package com.writingassistant.identity.scim;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * SCIM 2.0 Error message container according to RFC 7644 Section 3.12.
 */
public class ScimError implements Serializable {
    public static final String SCHEMA = "urn:ietf:params:scim:api:messages:2.0:Error";

    private List<String> schemas = Collections.singletonList(SCHEMA);
    private int status;
    private String scimType;
    private String detail;

    public ScimError() {}

    public ScimError(int status, String scimType, String detail) {
        this.status = status;
        this.scimType = scimType;
        this.detail = detail;
    }

    public List<String> getSchemas() { return schemas; }
    public void setSchemas(List<String> schemas) { this.schemas = schemas; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getScimType() { return scimType; }
    public void setScimType(String scimType) { this.scimType = scimType; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
