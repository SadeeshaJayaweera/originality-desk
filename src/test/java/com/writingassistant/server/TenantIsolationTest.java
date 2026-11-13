package com.writingassistant.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TenantIsolationTest {

    @Test
    void testStrictCrossTenantIsolation(@TempDir Path tempDir) throws Exception {
        try (ServerDatabase db = new ServerDatabase(tempDir.resolve("tenant_test_data"))) {
            Service service = new Service(db);

            // 1. Create Workspace A with Admin A
            JsonObject regA = new JsonObject();
            regA.addProperty("name", "Alice");
            regA.addProperty("email", "alice@institution-a.edu");
            regA.addProperty("password", "SecurePassword123!");
            regA.addProperty("workspaceName", "Institution A");
            Service.Authentication authA = service.register(regA);
            Service.Actor actorA = service.authenticate(authA.token());

            // 2. Create Workspace B with Admin B
            JsonObject regB = new JsonObject();
            regB.addProperty("name", "Bob");
            regB.addProperty("email", "bob@institution-b.edu");
            regB.addProperty("password", "SecurePassword456!");
            regB.addProperty("workspaceName", "Institution B");
            Service.Authentication authB = service.register(regB);
            Service.Actor actorB = service.authenticate(authB.token());

            assertNotEquals(actorA.workspaceId(), actorB.workspaceId(), "Workspaces must have distinct identifiers");

            // 3. User A uploads Document A in Workspace A
            byte[] docBytesA = "Academic submission text belonging exclusively to Institution A.".getBytes();
            Map<String, Object> docA = service.upload(actorA, "thesis_a.txt", "SUBMISSION", "text/plain", docBytesA);
            String docIdA = (String) docA.get("id");
            assertNotNull(docIdA);

            // 4. Verify Document Isolation (Read IDOR)
            // User B must NOT be able to view Document A
            ApiException exRead = assertThrows(ApiException.class, () -> service.document(actorB, docIdA),
                    "User B must not be allowed to inspect document belonging to Workspace A");
            assertEquals(404, exRead.status);

            // User B's document list must be empty
            Map<String, Object> docsB = service.documents(actorB, null, 1, 10);
            @SuppressWarnings("unchecked")
            List<?> itemsB = (List<?>) docsB.get("items");
            assertEquals(0, itemsB.size(), "Workspace B document list must not contain documents from Workspace A");

            // 5. Verify Document Isolation (Delete IDOR)
            // User B must NOT be able to delete Document A
            ApiException exDelete = assertThrows(ApiException.class, () -> service.deleteDocument(actorB, docIdA),
                    "User B must not be allowed to delete document belonging to Workspace A");
            assertEquals(404, exDelete.status);

            // Confirm Document A still exists in Workspace A
            assertNotNull(service.document(actorA, docIdA));

            // 6. User A uploads a reference document in Workspace A
            byte[] refBytesA = "Reference document text for analysis in Institution A.".getBytes();
            Map<String, Object> refA = service.upload(actorA, "ref_a.txt", "REFERENCE", "text/plain", refBytesA);
            String refIdA = (String) refA.get("id");

            // Mark documents ready for analysis
            db.transaction(sql -> {
                sql.execute("UPDATE documents SET status='READY', text='Sample extracted text content A' WHERE id IN (?, ?)", docIdA, refIdA);
                return null;
            });

            // 7. Cross-Workspace Analysis Creation (Subject Document IDOR)
            // User B attempts to analyze Document A
            JsonObject analysisInput = new JsonObject();
            analysisInput.addProperty("documentId", docIdA);
            analysisInput.addProperty("profile", "STANDARD");
            ApiException exCreateAnalysis = assertThrows(ApiException.class,
                    () -> service.createAnalysis(actorB, analysisInput, null),
                    "User B must not be allowed to create analysis targeting Workspace A document");
            assertEquals(404, exCreateAnalysis.status);

            // 8. Cross-Workspace Reference Source IDOR
            // User B uploads their own document in Workspace B
            byte[] docBytesB = "Submission by Bob in Institution B.".getBytes();
            Map<String, Object> docB = service.upload(actorB, "thesis_b.txt", "SUBMISSION", "text/plain", docBytesB);
            String docIdB = (String) docB.get("id");
            db.transaction(sql -> {
                sql.execute("UPDATE documents SET status='READY', text='Sample extracted text content B' WHERE id=?", docIdB);
                return null;
            });

            // User B attempts to reference Document A from Workspace A
            JsonObject invalidSourcesInput = new JsonObject();
            invalidSourcesInput.addProperty("documentId", docIdB);
            invalidSourcesInput.addProperty("profile", "STANDARD");
            JsonArray sourceArray = new JsonArray();
            sourceArray.add(refIdA);
            invalidSourcesInput.add("sourceIds", sourceArray);

            ApiException exCrossSource = assertThrows(ApiException.class,
                    () -> service.createAnalysis(actorB, invalidSourcesInput, null),
                    "User B must not be allowed to include reference sources from Workspace A");
            assertEquals(404, exCrossSource.status);

            // 9. User A creates analysis in Workspace A
            JsonObject validAnalysisInput = new JsonObject();
            validAnalysisInput.addProperty("documentId", docIdA);
            validAnalysisInput.addProperty("profile", "STANDARD");
            JsonArray validSources = new JsonArray();
            validSources.add(refIdA);
            validAnalysisInput.add("sourceIds", validSources);
            Map<String, Object> analysisA = service.createAnalysis(actorA, validAnalysisInput, null);
            String analysisIdA = (String) analysisA.get("id");

            // 10. Verify Analysis Read & Cancel IDOR
            ApiException exAnalysisRead = assertThrows(ApiException.class,
                    () -> service.analysis(actorB, analysisIdA, false),
                    "User B must not be allowed to read analysis from Workspace A");
            assertEquals(404, exAnalysisRead.status);

            ApiException exCancel = assertThrows(ApiException.class,
                    () -> service.cancel(actorB, analysisIdA),
                    "User B must not be allowed to cancel analysis from Workspace A");
            assertEquals(404, exCancel.status);

            // 11. Verify Review & Notes IDOR
            JsonObject reviewInput = new JsonObject();
            reviewInput.addProperty("matchIndex", 0);
            reviewInput.addProperty("decision", "QUOTATION");
            reviewInput.addProperty("note", "Cross-tenant intrusion attempt");

            ApiException exReview = assertThrows(ApiException.class,
                    () -> service.review(actorB, analysisIdA, reviewInput),
                    "User B must not be allowed to attach reviews to Workspace A analysis");
            assertEquals(404, exReview.status);

            // 12. Verify Audit Logs & Admin Job Queue Isolation
            Map<String, Object> logsB = service.adminList(actorB, "audit-logs", 1, 50);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> auditItemsB = (List<Map<String, Object>>) logsB.get("items");
            for (Map<String, Object> item : auditItemsB) {
                // Assert no audit entry references Actor A
                assertNotEquals(actorA.userId(), item.get("userId"), "Audit logs must not leak user IDs from other workspaces");
            }

            Map<String, Object> jobsB = service.adminList(actorB, "jobs", 1, 50);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> jobItemsB = (List<Map<String, Object>>) jobsB.get("items");
            for (Map<String, Object> job : jobItemsB) {
                assertNotEquals(docIdA, job.get("resourceId"), "Workspace B jobs must not contain document A");
                assertNotEquals(refIdA, job.get("resourceId"), "Workspace B jobs must not contain reference A");
                assertNotEquals(analysisIdA, job.get("resourceId"), "Workspace B jobs must not contain analysis A");
            }
        }
    }
}
