package com.writingassistant.identity.scim;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * SCIM 2.0 ListResponse container according to RFC 7644 Section 3.4.2.
 */
public class ScimListResponse<T> implements Serializable {
    public static final String SCHEMA = "urn:ietf:params:scim:api:messages:2.0:ListResponse";

    private List<String> schemas = Collections.singletonList(SCHEMA);
    private int totalResults;
    private int itemsPerPage;
    private int startIndex;
    private List<T> resources;

    public ScimListResponse() {}

    public ScimListResponse(List<T> resources, int totalResults, int startIndex, int itemsPerPage) {
        this.resources = resources != null ? resources : Collections.emptyList();
        this.totalResults = totalResults;
        this.startIndex = startIndex;
        this.itemsPerPage = itemsPerPage;
    }

    public List<String> getSchemas() { return schemas; }
    public void setSchemas(List<String> schemas) { this.schemas = schemas; }

    public int getTotalResults() { return totalResults; }
    public void setTotalResults(int totalResults) { this.totalResults = totalResults; }

    public int getItemsPerPage() { return itemsPerPage; }
    public void setItemsPerPage(int itemsPerPage) { this.itemsPerPage = itemsPerPage; }

    public int getStartIndex() { return startIndex; }
    public void setStartIndex(int startIndex) { this.startIndex = startIndex; }

    public List<T> getResources() { return resources; }
    public void setResources(List<T> resources) { this.resources = resources; }
}
