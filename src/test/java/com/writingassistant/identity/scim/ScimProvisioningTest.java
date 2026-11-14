package com.writingassistant.identity.scim;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScimProvisioningTest {

    private ScimProvisioningService service;
    private final String workspaceId = "ws_oxford_univ";

    @BeforeEach
    void setUp() {
        service = new ScimProvisioningService(new InMemoryScimStore());
    }

    @Test
    @DisplayName("Create SCIM user with valid schema, generated ID, and initialized metadata")
    void testCreateUser() throws Exception {
        ScimUser user = new ScimUser(null, "turing", "Alan Turing", "alan.turing@oxford.edu", "REVIEWER", true);
        user.setName(new ScimName("Alan Turing", "Turing", "Alan"));

        ScimUser created = service.createUser(workspaceId, user);

        assertNotNull(created);
        assertNotNull(created.getId());
        assertTrue(created.getId().startsWith("usr_"));
        assertEquals("turing", created.getUserName());
        assertEquals("Alan Turing", created.getDisplayName());
        assertEquals("alan.turing@oxford.edu", created.getPrimaryEmail());
        assertEquals("REVIEWER", created.getPrimaryRole());
        assertTrue(created.isActive());
        assertTrue(created.getSchemas().contains(ScimUser.SCHEMA));
        assertNotNull(created.getMeta().get("created"));
        assertNotNull(created.getMeta().get("lastModified"));
    }

    @Test
    @DisplayName("Reject user creation when userName is missing or blank (HTTP 400 invalidValue)")
    void testMissingUserNameRejection() {
        ScimUser user = new ScimUser(null, "", "Blank User", "user@oxford.edu", "MEMBER", true);
        ScimException ex = assertThrows(ScimException.class, () -> service.createUser(workspaceId, user));
        assertEquals(400, ex.getStatus());
        assertEquals("invalidValue", ex.getScimType());
    }

    @Test
    @DisplayName("Reject duplicate userName in the same workspace (HTTP 409 uniqueness)")
    void testDuplicateUserNameRejection() throws Exception {
        ScimUser user1 = new ScimUser(null, "berners_lee", "Tim Berners-Lee", "tim@oxford.edu", "REVIEWER", true);
        service.createUser(workspaceId, user1);

        ScimUser user2 = new ScimUser(null, "berners_lee", "Duplicate User", "tim2@oxford.edu", "MEMBER", true);
        ScimException ex = assertThrows(ScimException.class, () -> service.createUser(workspaceId, user2));
        assertEquals(409, ex.getStatus());
        assertEquals("uniqueness", ex.getScimType());
    }

    @Test
    @DisplayName("Retrieve user by ID or throw HTTP 404 when not found")
    void testGetUserById() throws Exception {
        ScimUser user = new ScimUser(null, "lovelace", "Ada Lovelace", "ada@oxford.edu", "INSTITUTION_ADMIN", true);
        ScimUser created = service.createUser(workspaceId, user);

        ScimUser fetched = service.getUser(workspaceId, created.getId());
        assertNotNull(fetched);
        assertEquals(created.getId(), fetched.getId());
        assertEquals("lovelace", fetched.getUserName());

        ScimException ex = assertThrows(ScimException.class, () -> service.getUser(workspaceId, "usr_nonexistent"));
        assertEquals(404, ex.getStatus());
    }

    @Test
    @DisplayName("Query users with filtering and pagination")
    void testQueryUsersWithFilter() throws Exception {
        service.createUser(workspaceId, new ScimUser(null, "newton", "Isaac Newton", "newton@oxford.edu", "REVIEWER", true));
        service.createUser(workspaceId, new ScimUser(null, "hawking", "Stephen Hawking", "hawking@oxford.edu", "REVIEWER", true));
        service.createUser(workspaceId, new ScimUser(null, "darwin", "Charles Darwin", "darwin@oxford.edu", "MEMBER", true));

        // 1. Query all with pagination
        ScimListResponse<ScimUser> allRes = service.queryUsers(workspaceId, null, 1, 2);
        assertEquals(3, allRes.getTotalResults());
        assertEquals(2, allRes.getItemsPerPage());
        assertEquals(1, allRes.getStartIndex());
        assertEquals(2, allRes.getResources().size());

        // 2. Query with filter by userName
        ScimListResponse<ScimUser> filterRes = service.queryUsers(workspaceId, "userName eq \"hawking\"", 1, 10);
        assertEquals(1, filterRes.getTotalResults());
        assertEquals("hawking", filterRes.getResources().get(0).getUserName());

        // 3. Query with filter by email
        ScimListResponse<ScimUser> emailRes = service.queryUsers(workspaceId, "email eq \"newton@oxford.edu\"", 1, 10);
        assertEquals(1, emailRes.getTotalResults());
        assertEquals("newton", emailRes.getResources().get(0).getUserName());
    }

    @Test
    @DisplayName("Replace user attributes preserving original ID and creation timestamp")
    void testReplaceUser() throws Exception {
        ScimUser user = service.createUser(workspaceId, new ScimUser(null, "boole", "George Boole", "boole@oxford.edu", "MEMBER", true));
        String originalCreated = user.getMeta().get("created").toString();

        ScimUser updated = new ScimUser(null, "boole", "Sir George Boole", "george.boole@oxford.edu", "REVIEWER", true);
        ScimUser replaced = service.replaceUser(workspaceId, user.getId(), updated);

        assertEquals(user.getId(), replaced.getId());
        assertEquals("Sir George Boole", replaced.getDisplayName());
        assertEquals("george.boole@oxford.edu", replaced.getPrimaryEmail());
        assertEquals("REVIEWER", replaced.getPrimaryRole());
        assertEquals(originalCreated, replaced.getMeta().get("created"));
        assertNotNull(replaced.getMeta().get("lastModified"));
    }

    @Test
    @DisplayName("Apply PATCH operation to deactivate user account (active = false)")
    void testPatchUserDeactivation() throws Exception {
        ScimUser user = service.createUser(workspaceId, new ScimUser(null, "pascal", "Blaise Pascal", "pascal@oxford.edu", "MEMBER", true));
        assertTrue(user.isActive());

        ScimPatchOperation patchOp = new ScimPatchOperation("replace", "active", false);
        ScimUser patched = service.patchUser(workspaceId, user.getId(), List.of(patchOp));

        assertFalse(patched.isActive());

        // Fetch again to ensure persistence
        ScimUser fetched = service.getUser(workspaceId, user.getId());
        assertFalse(fetched.isActive());
    }

    @Test
    @DisplayName("Delete user account by ID or throw HTTP 404 when absent")
    void testDeleteUser() throws Exception {
        ScimUser user = service.createUser(workspaceId, new ScimUser(null, "euclid", "Euclid of Alexandria", "euclid@oxford.edu", "MEMBER", true));

        service.deleteUser(workspaceId, user.getId());

        ScimException ex = assertThrows(ScimException.class, () -> service.getUser(workspaceId, user.getId()));
        assertEquals(404, ex.getStatus());

        // Second deletion fails with 404
        assertThrows(ScimException.class, () -> service.deleteUser(workspaceId, user.getId()));
    }

    @Test
    @DisplayName("Manage SCIM Groups: create, retrieve, and query group membership")
    void testGroupManagement() throws Exception {
        ScimUser student1 = service.createUser(workspaceId, new ScimUser(null, "s1", "Student One", "s1@oxford.edu", "MEMBER", true));
        ScimUser student2 = service.createUser(workspaceId, new ScimUser(null, "s2", "Student Two", "s2@oxford.edu", "MEMBER", true));

        ScimGroup group = new ScimGroup(null, "CS101-Cohort-2026");
        group.getMembers().add(new ScimGroupMember(student1.getId(), "Student One"));
        group.getMembers().add(new ScimGroupMember(student2.getId(), "Student Two"));

        ScimGroup createdGroup = service.createGroup(workspaceId, group);

        assertNotNull(createdGroup.getId());
        assertTrue(createdGroup.getId().startsWith("grp_"));
        assertEquals("CS101-Cohort-2026", createdGroup.getDisplayName());
        assertEquals(2, createdGroup.getMembers().size());

        ScimGroup fetchedGroup = service.getGroup(workspaceId, createdGroup.getId());
        assertEquals(2, fetchedGroup.getMembers().size());

        ScimListResponse<ScimGroup> groupList = service.queryGroups(workspaceId, null, 1, 10);
        assertEquals(1, groupList.getTotalResults());
    }

    @Test
    @DisplayName("Enforce strict multi-tenant workspace isolation for SCIM provisioning")
    void testTenantIsolation() throws Exception {
        String workspaceA = "ws_oxford";
        String workspaceB = "ws_cambridge";

        ScimUser userA = service.createUser(workspaceA, new ScimUser(null, "researcher", "Oxford Researcher", "res@oxford.edu", "REVIEWER", true));

        // Researcher must NOT exist in Cambridge
        assertThrows(ScimException.class, () -> service.getUser(workspaceB, userA.getId()));

        ScimListResponse<ScimUser> cambridgeList = service.queryUsers(workspaceB, null, 1, 10);
        assertEquals(0, cambridgeList.getTotalResults());

        // Same username can exist in another tenant without conflict
        ScimUser userB = service.createUser(workspaceB, new ScimUser(null, "researcher", "Cambridge Researcher", "res@cambridge.edu", "REVIEWER", true));
        assertNotNull(userB.getId());
        assertNotEquals(userA.getId(), userB.getId());
    }

    @Test
    @DisplayName("Provide RFC 7643 ServiceProviderConfig metadata")
    void testServiceProviderConfig() {
        Map<String, Object> config = service.getServiceProviderConfig();
        assertNotNull(config);
        assertTrue(config.containsKey("schemas"));
        assertTrue(config.containsKey("patch"));
        assertTrue(config.containsKey("filter"));

        @SuppressWarnings("unchecked")
        Map<String, Object> patch = (Map<String, Object>) config.get("patch");
        assertEquals(Boolean.TRUE, patch.get("supported"));
    }
}
