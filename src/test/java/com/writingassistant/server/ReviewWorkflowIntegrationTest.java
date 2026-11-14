package com.writingassistant.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReviewWorkflowIntegrationTest {

    @Test
    @DisplayName("Review workflow tracks decisions, detects multi-reviewer conflicts, and enforces resolution access controls")
    void endToEndReviewWorkflowAndConflictResolution(@TempDir Path tempDir) throws Exception {
        try (ServerDatabase db = new ServerDatabase(tempDir.resolve("review_test_data"))) {
            Service service = new Service(db);

            // 1. Create Workspace A with Admin
            JsonObject regA = new JsonObject();
            regA.addProperty("name", "Dr. Jane Smith");
            regA.addProperty("email", "jane.smith@institution.edu");
            regA.addProperty("password", "AdminSecure123!");
            regA.addProperty("workspaceName", "Department of Computer Science");
            Service.Authentication authA = service.register(regA);
            Service.Actor adminA = service.authenticate(authA.token());

            // 2. Create standard REVIEWER in Workspace A
            String reviewerUserId = db.transaction(sql -> {
                String uId = Service.id();
                sql.execute("INSERT INTO users(id,workspace_id,email,password_hash,name,role,created_at) VALUES(?,?,?,?,?,?,?)",
                        uId, adminA.workspaceId(), "ta@institution.edu", "hash_placeholder", "TA Reviewer", "REVIEWER", Instant.now().toString());
                return uId;
            });
            Service.Actor reviewerA = new Service.Actor(reviewerUserId, adminA.workspaceId(), "REVIEWER");

            // 3. Upload a submission document
            byte[] docBytes = "Academic research paper discussing machine learning models and semantic similarity.".getBytes(StandardCharsets.UTF_8);
            Map<String, Object> doc = service.upload(adminA, "submission.txt", "SUBMISSION", "text/plain", docBytes);
            String docId = (String) doc.get("id");

            // 4. Create a completed analysis with 2 matches
            String analysisId = Service.id();
            JsonObject reportJson = new JsonObject();
            JsonObject resultObj = new JsonObject();
            JsonArray matchesArray = new JsonArray();

            JsonObject match0 = new JsonObject();
            match0.addProperty("matchIndex", 0);
            match0.addProperty("sourceTitle", "Paper A");
            matchesArray.add(match0);

            JsonObject match1 = new JsonObject();
            match1.addProperty("matchIndex", 1);
            match1.addProperty("sourceTitle", "Paper B");
            matchesArray.add(match1);

            resultObj.add("matches", matchesArray);
            reportJson.add("result", resultObj);

            db.transaction(sql -> {
                sql.execute("INSERT INTO analyses(id,workspace_id,document_id,title,status,profile,created_at,completed_at,source_count,result_json,request_hash) "
                                + "VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                        analysisId, adminA.workspaceId(), docId, "submission.txt Analysis", "COMPLETED", "STANDARD",
                        Instant.now().toString(), Instant.now().toString(), 2, reportJson.toString(), "hash_analysis_1");
                return null;
            });

            // 5. Submit initial reviews by reviewerA
            JsonObject reviewInput0 = new JsonObject();
            reviewInput0.addProperty("matchIndex", 0);
            reviewInput0.addProperty("decision", "APPROVED");
            reviewInput0.addProperty("note", "Proper APA citation present in bibliography.");
            service.review(reviewerA, analysisId, reviewInput0);

            JsonObject reviewInput1 = new JsonObject();
            reviewInput1.addProperty("matchIndex", 1);
            reviewInput1.addProperty("decision", "FLAGGED");
            reviewInput1.addProperty("note", "Unattributed verbatim passage spanning three sentences.");
            service.review(reviewerA, analysisId, reviewInput1);

            // 6. Verify summary after initial reviewer
            Map<String, Object> summary1 = service.reviewSummary(reviewerA, analysisId);
            assertEquals("ACTION_REQUIRED", summary1.get("overallStatus"));
            assertEquals(2, summary1.get("totalMatches"));
            assertEquals(2, summary1.get("reviewedMatches"));
            assertEquals(1, summary1.get("approvedCount"));
            assertEquals(1, summary1.get("flaggedCount"));
            assertEquals(0, summary1.get("conflictCount"));

            // 7. Second reviewer submits conflicting review on match 1
            String reviewer2Id = db.transaction(sql -> {
                String uId = Service.id();
                sql.execute("INSERT INTO users(id,workspace_id,email,password_hash,name,role,created_at) VALUES(?,?,?,?,?,?,?)",
                        uId, adminA.workspaceId(), "ta2@institution.edu", "hash_placeholder", "TA 2", "REVIEWER", Instant.now().toString());
                return uId;
            });
            Service.Actor reviewerA2 = new Service.Actor(reviewer2Id, adminA.workspaceId(), "REVIEWER");

            JsonObject reviewInput1Conflict = new JsonObject();
            reviewInput1Conflict.addProperty("matchIndex", 1);
            reviewInput1Conflict.addProperty("decision", "APPROVED");
            reviewInput1Conflict.addProperty("note", "Common domain terminology and mathematical equations.");
            service.review(reviewerA2, analysisId, reviewInput1Conflict);

            // 8. Verify conflict detection
            Map<String, Object> summary2 = service.reviewSummary(reviewerA, analysisId);
            assertEquals("NEEDS_RESOLUTION", summary2.get("overallStatus"));
            assertEquals(1, summary2.get("conflictCount"));

            // 9. Standard reviewer attempting resolveConflict should be rejected (403 Forbidden)
            JsonObject resolveInput = new JsonObject();
            resolveInput.addProperty("matchIndex", 1);
            resolveInput.addProperty("decision", "EXEMPTED");
            resolveInput.addProperty("note", "Faculty integrity panel formal ruling: standard course assignment boilerplate.");

            ApiException ex = assertThrows(ApiException.class, () ->
                    service.resolveConflict(reviewerA, analysisId, resolveInput));
            assertEquals(403, ex.status);

            // 10. Administrator resolves conflict successfully
            Map<String, Object> resolved = service.resolveConflict(adminA, analysisId, resolveInput);
            assertNotNull(resolved.get("id"));

            // 11. Verify cleared status after resolution
            Map<String, Object> summary3 = service.reviewSummary(adminA, analysisId);
            assertEquals("CLEARED", summary3.get("overallStatus"));
            assertEquals(0, summary3.get("conflictCount"));
            assertEquals(1, summary3.get("exemptedCount"));
            assertEquals(1, summary3.get("approvedCount"));

            // 12. Verify cross-tenant isolation
            JsonObject regB = new JsonObject();
            regB.addProperty("name", "Mallory");
            regB.addProperty("email", "mallory@other-uni.edu");
            regB.addProperty("password", "HackerPwd123!");
            regB.addProperty("workspaceName", "Other University");
            Service.Authentication authB = service.register(regB);
            Service.Actor actorB = service.authenticate(authB.token());

            assertThrows(ApiException.class, () -> service.reviewSummary(actorB, analysisId));
            assertThrows(ApiException.class, () -> service.review(actorB, analysisId, reviewInput0));
        }
    }
}
