package com.writingassistant.review;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HumanReviewWorkflowTest {

    @Test
    @DisplayName("ReviewDecision parses standard decisions and handles case insensitivity")
    void reviewDecisionParsing() {
        assertEquals(ReviewDecision.APPROVED, ReviewDecision.parse("APPROVED"));
        assertEquals(ReviewDecision.APPROVED, ReviewDecision.parse("approved"));
        assertEquals(ReviewDecision.FLAGGED, ReviewDecision.parse("FLAGGED"));
        assertEquals(ReviewDecision.EXEMPTED, ReviewDecision.parse("exempted"));
        assertEquals(ReviewDecision.ESCALATED, ReviewDecision.parse("ESCALATED"));
        assertEquals(ReviewDecision.PENDING, ReviewDecision.parse(null));
        assertEquals(ReviewDecision.PENDING, ReviewDecision.parse(""));

        assertThrows(IllegalArgumentException.class, () -> ReviewDecision.parse("INVALID_DECISION"));

        assertTrue(ReviewDecision.FLAGGED.isActionable());
        assertTrue(ReviewDecision.ESCALATED.isActionable());
        assertFalse(ReviewDecision.APPROVED.isActionable());
        assertFalse(ReviewDecision.EXEMPTED.isActionable());
        assertFalse(ReviewDecision.PENDING.isActionable());
    }

    @Test
    @DisplayName("ReviewRole validates hierarchy levels and escalation resolution permissions")
    void reviewRolePermissions() {
        assertFalse(ReviewRole.PEER_REVIEWER.canResolveEscalation());
        assertTrue(ReviewRole.SENIOR_REVIEWER.canResolveEscalation());
        assertTrue(ReviewRole.INTEGRITY_OFFICER.canResolveEscalation());
        assertTrue(ReviewRole.ADMINISTRATOR.canResolveEscalation());

        assertTrue(ReviewRole.ADMINISTRATOR.getHierarchyLevel() > ReviewRole.SENIOR_REVIEWER.getHierarchyLevel());
        assertTrue(ReviewRole.SENIOR_REVIEWER.getHierarchyLevel() > ReviewRole.PEER_REVIEWER.getHierarchyLevel());
    }

    @Test
    @DisplayName("ReviewTransitionValidator rejects unauthorized de-escalation by peer reviewers")
    void rejectsUnauthorizedDeEscalation() {
        // Attempting to de-escalate from ESCALATED to APPROVED as a PEER_REVIEWER should fail
        assertThrows(IllegalStateException.class, () ->
                ReviewTransitionValidator.validateTransition(
                        ReviewDecision.ESCALATED,
                        ReviewDecision.APPROVED,
                        ReviewRole.PEER_REVIEWER,
                        "Looks fine to me."
                )
        );

        // SENIOR_REVIEWER with adequate note should succeed
        assertDoesNotThrow(() ->
                ReviewTransitionValidator.validateTransition(
                        ReviewDecision.ESCALATED,
                        ReviewDecision.APPROVED,
                        ReviewRole.SENIOR_REVIEWER,
                        "Formally investigated and confirmed as authorized institutional quotation."
                )
        );
    }

    @Test
    @DisplayName("ReviewTransitionValidator enforces required justification notes for exemptions and de-escalation")
    void enforcesJustificationNotes() {
        // Exemption without explanation must fail
        assertThrows(IllegalStateException.class, () ->
                ReviewTransitionValidator.validateTransition(
                        ReviewDecision.PENDING,
                        ReviewDecision.EXEMPTED,
                        ReviewRole.PEER_REVIEWER,
                        " "
                )
        );

        // Clearing flagged to approved without explanation must fail
        assertThrows(IllegalStateException.class, () ->
                ReviewTransitionValidator.validateTransition(
                        ReviewDecision.FLAGGED,
                        ReviewDecision.APPROVED,
                        ReviewRole.PEER_REVIEWER,
                        "ok"
                )
        );
    }

    @Test
    @DisplayName("HumanReviewWorkflowEngine identifies unanimous reviews correctly")
    void unanimousReviewsEvaluated() {
        HumanReviewWorkflowEngine engine = new HumanReviewWorkflowEngine();

        ReviewRecord r1 = new ReviewRecord(
                "rev-1", "ws-1", "ana-1", 0, "user-1", "Alice",
                ReviewRole.PEER_REVIEWER, ReviewDecision.APPROVED,
                "Properly cited block quotation", "2026-11-14T10:00:00Z", false
        );
        ReviewRecord r2 = new ReviewRecord(
                "rev-2", "ws-1", "ana-1", 0, "user-2", "Bob",
                ReviewRole.PEER_REVIEWER, ReviewDecision.APPROVED,
                "Agreed, standard APA citation format", "2026-11-14T10:05:00Z", false
        );

        DocumentReviewSummary summary = engine.evaluate("ws-1", "ana-1", 1, List.of(r1, r2));

        assertEquals(DocumentReviewSummary.STATUS_CLEARED, summary.overallStatus());
        assertEquals(1, summary.totalMatches());
        assertEquals(1, summary.reviewedMatches());
        assertEquals(1, summary.approvedCount());
        assertEquals(0, summary.conflictCount());

        MatchReviewSummary match0 = summary.matchSummaries().get(0);
        assertNotNull(match0);
        assertEquals(ReviewDecision.APPROVED, match0.activeDecision());
        assertEquals(ReviewConflictStatus.UNANIMOUS, match0.conflictStatus());
        assertFalse(match0.hasConflict());
        assertFalse(match0.requiresAction());
        assertEquals(2, match0.totalReviews());
    }

    @Test
    @DisplayName("HumanReviewWorkflowEngine detects conflicting multi-reviewer decisions and resolves by seniority")
    void conflictingReviewsResolvedBySeniority() {
        HumanReviewWorkflowEngine engine = new HumanReviewWorkflowEngine(ConflictResolutionStrategy.SENIORITY);

        ReviewRecord peerReview = new ReviewRecord(
                "rev-1", "ws-1", "ana-1", 0, "user-1", "TA Alice",
                ReviewRole.PEER_REVIEWER, ReviewDecision.APPROVED,
                "Looks acceptable to me", "2026-11-14T10:00:00Z", false
        );
        ReviewRecord seniorReview = new ReviewRecord(
                "rev-2", "ws-1", "ana-1", 0, "user-2", "Prof. Bob",
                ReviewRole.SENIOR_REVIEWER, ReviewDecision.FLAGGED,
                "Unattributed verbatim excerpt from published thesis", "2026-11-14T10:15:00Z", false
        );

        DocumentReviewSummary summary = engine.evaluate("ws-1", "ana-1", 1, List.of(peerReview, seniorReview));

        assertEquals(1, summary.conflictCount());
        assertEquals(DocumentReviewSummary.STATUS_NEEDS_RESOLUTION, summary.overallStatus());

        MatchReviewSummary match0 = summary.matchSummaries().get(0);
        assertNotNull(match0);
        assertTrue(match0.hasConflict());
        assertEquals(ReviewConflictStatus.CONFLICT, match0.conflictStatus());
        // Seniority strategy selected senior reviewer's FLAGGED decision
        assertEquals(ReviewDecision.FLAGGED, match0.activeDecision());
        assertTrue(match0.requiresAction());
    }

    @Test
    @DisplayName("HumanReviewWorkflowEngine resolves conflict when formal resolution record is present")
    void formalResolutionAdjudicatesConflict() {
        HumanReviewWorkflowEngine engine = new HumanReviewWorkflowEngine();

        ReviewRecord peer1 = new ReviewRecord(
                "rev-1", "ws-1", "ana-1", 0, "user-1", "Reviewer 1",
                ReviewRole.PEER_REVIEWER, ReviewDecision.APPROVED,
                "Approved", "2026-11-14T10:00:00Z", false
        );
        ReviewRecord peer2 = new ReviewRecord(
                "rev-2", "ws-1", "ana-1", 0, "user-2", "Reviewer 2",
                ReviewRole.PEER_REVIEWER, ReviewDecision.FLAGGED,
                "Questionable phrasing", "2026-11-14T10:05:00Z", false
        );
        ReviewRecord adjudication = new ReviewRecord(
                "rev-3", "ws-1", "ana-1", 0, "admin-1", "Academic Integrity Dean",
                ReviewRole.INTEGRITY_OFFICER, ReviewDecision.EXEMPTED,
                "Formally investigated. Phrase is standard discipline assignment question.", "2026-11-14T10:30:00Z", true
        );

        DocumentReviewSummary summary = engine.evaluate("ws-1", "ana-1", 1, List.of(peer1, peer2, adjudication));

        assertEquals(0, summary.conflictCount());
        assertEquals(DocumentReviewSummary.STATUS_CLEARED, summary.overallStatus());

        MatchReviewSummary match0 = summary.matchSummaries().get(0);
        assertNotNull(match0);
        assertFalse(match0.hasConflict());
        assertEquals(ReviewConflictStatus.RESOLVED, match0.conflictStatus());
        assertEquals(ReviewDecision.EXEMPTED, match0.activeDecision());
        assertFalse(match0.requiresAction());
    }

    @Test
    @DisplayName("HumanReviewWorkflowEngine overall status reflects unreviewed, in_review, and action_required states")
    void overallDocumentStatusTransitions() {
        HumanReviewWorkflowEngine engine = new HumanReviewWorkflowEngine();

        // 1. Unreviewed
        DocumentReviewSummary unreviewed = engine.evaluate("ws-1", "ana-1", 3, List.of());
        assertEquals(DocumentReviewSummary.STATUS_UNREVIEWED, unreviewed.overallStatus());
        assertEquals(0, unreviewed.reviewedMatches());

        // 2. In Review (1 of 3 matches reviewed and approved)
        ReviewRecord r1 = new ReviewRecord(
                "rev-1", "ws-1", "ana-1", 0, "u1", "A",
                ReviewRole.PEER_REVIEWER, ReviewDecision.APPROVED,
                "ok", "2026-11-14T10:00:00Z", false
        );
        DocumentReviewSummary inReview = engine.evaluate("ws-1", "ana-1", 3, List.of(r1));
        assertEquals(DocumentReviewSummary.STATUS_IN_REVIEW, inReview.overallStatus());
        assertEquals(1, inReview.reviewedMatches());

        // 3. Action Required (match 1 flagged)
        ReviewRecord r2 = new ReviewRecord(
                "rev-2", "ws-1", "ana-1", 1, "u1", "A",
                ReviewRole.PEER_REVIEWER, ReviewDecision.FLAGGED,
                "plagiarism suspected", "2026-11-14T10:01:00Z", false
        );
        DocumentReviewSummary actionRequired = engine.evaluate("ws-1", "ana-1", 3, List.of(r1, r2));
        assertEquals(DocumentReviewSummary.STATUS_ACTION_REQUIRED, actionRequired.overallStatus());
        assertEquals(1, actionRequired.flaggedCount());
    }
}
