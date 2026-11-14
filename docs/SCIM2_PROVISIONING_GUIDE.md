# SCIM 2.0 Institutional Provisioning & User Lifecycle Management Guide

Originality Desk implements the **System for Cross-domain Identity Management (SCIM 2.0)** standards specified in **RFC 7643** (Core Schema) and **RFC 7644** (Protocol), enabling automated Just-In-Time (JIT) and scheduled directory synchronization from enterprise Identity Providers such as **Microsoft Entra ID (Azure AD)**, **Okta**, **OneLogin**, **PingFederate**, and **SailPoint**.

---

## 1. Architectural Architecture

```
+-----------------------------------------------------------------------------------+
|               Identity Provider / Enterprise Directory (Entra / Okta)             |
+-----------------------------------------------------------------------------------+
                                         |
             SCIM 2.0 RESTful API Calls  |  Authorization: Bearer <API_TOKEN>
             (POST/GET/PUT/PATCH/DELETE) |  Tenant Context: /scim/v2/{workspaceId}/...
                                         v
+-----------------------------------------------------------------------------------+
|                     Originality Desk SCIM 2.0 Service Engine                      |
|                       (com.writingassistant.identity.scim)                        |
|                                                                                   |
|  +-----------------------------------------------------------------------------+  |
|  |                          ScimProvisioningService                            |  |
|  |  * Enforces RFC 7643 Core Schemas (User, Group, ListResponse, Error)        |  |
|  |  * Enforces RFC 7644 Protocol operations, filter parsing, and error codes  |  |
|  |  * Uniqueness enforcement (HTTP 409 scimType="uniqueness")                  |  |
|  |  * Soft deactivation via PATCH operations (active=false)                    |  |
|  +-----------------------------------------------------------------------------+  |
|                                        |                                          |
|                                        v                                          |
|  +-----------------------------------------------------------------------------+  |
|  |                                 ScimStore                                   |  |
|  |  * Strict workspace tenant key partitioning ({workspaceId})                 |  |
|  |  * Zero cross-tenant entity leakage or duplicate namespace collisions       |  |
|  +-----------------------------------------------------------------------------+  |
+-----------------------------------------------------------------------------------+
```

---

## 2. Supported SCIM 2.0 Endpoints & Operations

All endpoints are scoped by tenant workspace or inherit the default institutional workspace:

### 2.1 Service Provider Configuration
- `GET /scim/v2/ServiceProviderConfig`: Returns supported RFC 7643 capabilities (patch: true, filter: true, bulk: false, changePassword: false).
- `GET /scim/v2/Schemas`: Exposes schemas for `urn:ietf:params:scim:schemas:core:2.0:User` and `urn:ietf:params:scim:schemas:core:2.0:Group`.

### 2.2 User Provisioning (`/scim/v2/Users`)
- `POST /scim/v2/Users`: Provisions a new institutional user.
  - Required fields: `userName`.
  - Optional fields: `name` (formatted, familyName, givenName), `displayName`, `emails`, `roles`, `active`.
  - Duplicate detection: Returns `HTTP 409 Conflict` with `scimType="uniqueness"` if `userName` already exists in the target workspace.
- `GET /scim/v2/Users/{id}`: Retrieves single user profile by ID.
- `GET /scim/v2/Users?filter=userName eq "..."&startIndex=1&count=50`: Queries users with filter matching and RFC 7644 pagination (`totalResults`, `itemsPerPage`, `startIndex`, `Resources`).
- `PUT /scim/v2/Users/{id}`: Full resource replacement preserving ID and creation metadata while updating `lastModified`.
- `PATCH /scim/v2/Users/{id}`: Incremental modifications, including instant account deactivation:
  ```json
  {
    "schemas": ["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
    "Operations": [
      {
        "op": "replace",
        "path": "active",
        "value": false
      }
    ]
  }
  ```
- `DELETE /scim/v2/Users/{id}`: Deprovisions user from the institutional workspace.

### 2.3 Group Provisioning (`/scim/v2/Groups`)
- `POST /scim/v2/Groups`: Provisions cohort or department groups (`displayName`, `members`).
- `GET /scim/v2/Groups/{id}`: Retrieves group membership.
- `GET /scim/v2/Groups`: Lists groups with pagination.
- `DELETE /scim/v2/Groups/{id}`: Removes group.

---

## 3. Multi-Tenant Isolation & Role Alignment

- **Workspace Partitioning**: All user records, queries, and group memberships are strictly isolated by workspace ID. The same username (e.g. `researcher`) can coexist independently in different workspaces without cross-tenant conflict.
- **Role Synchronization**:
  - `INSTITUTION_ADMIN`: Institutional tenant administrators with workspace policy privileges.
  - `REVIEWER`: Faculty, instructors, and reviewers authorized to assess document similarity and adjudicate matches.
  - `MEMBER`: General academic users, researchers, and students.
