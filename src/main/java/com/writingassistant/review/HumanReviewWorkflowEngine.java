package com.writingassistant.review;

import java.util.*;

/**
 * Core workflow orchestration engine for human review evaluation, multi-reviewer consensus,
 * conflict resolution, and institutional governance compliance.
 */
public class HumanReviewWorkflowEngine {

    private final ConflictResolutionStrategy defaultStrategy;

    public HumanReviewWorkflowEngine() {
        this(ConflictResolutionStrategy.SENIORITY);
    }

    public HumanReviewWorkflowEngine(ConflictResolutionStrategy defaultStrategy) {
        this.defaultStrategy = Objects.requireNonNull(defaultStrategy, "defaultStrategy must not be null");
    }

    public ConflictResolutionStrategy getDefaultStrategy() {
        return defaultStrategy;
    }

    /**
     * Consolidates a complete list of review audit records into an aggregated DocumentReviewSummary.
     *
     * @param workspaceId  Workspace identifier.
     * @param analysisId   Analysis identifier.
     * @param totalMatches Total number of similarity matches in the analysis report.
     * @param records      All recorded review actions.
     * @return Consolidated DocumentReviewSummary.
     */
    public DocumentReviewSummary evaluate(
            String workspaceId,
            String analysisId,
            int totalMatches,
            List<ReviewRecord> records
    ) {
        return evaluate(workspaceId, analysisId, totalMatches, records, defaultStrategy);
    }

    /**
     * Consolidates review audit records using a specific conflict resolution strategy.
     */
    public DocumentReviewSummary evaluate(
            String workspaceId,
            String analysisId,
            int totalMatches,
            List<ReviewRecord> records,
            ConflictResolutionStrategy strategy
    ) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(analysisId, "analysisId must not be null");
        Objects.requireNonNull(strategy, "strategy must not be null");

        List<ReviewRecord> safeRecords = records != null ? List.copyOf(records) : List.of();

        // Group records by matchIndex
        Map<Integer, List<ReviewRecord>> byMatch = new HashMap<>();
        for (ReviewRecord record : safeRecords) {
            byMatch.computeIfAbsent(record.matchIndex(), k -> new ArrayList<>()).add(record);
        }

        Map<Integer, MatchReviewSummary> summaries = new TreeMap<>();
        int approvedCount = 0;
        int flaggedCount = 0;
        int exemptedCount = 0;
        int escalatedCount = 0;
        int conflictCount = 0;

        for (int i = 0; i < totalMatches; i++) {
            List<ReviewRecord> matchRecords = byMatch.getOrDefault(i, List.of());
            MatchReviewSummary summary = evaluateMatch(i, matchRecords, strategy);
            summaries.put(i, summary);

            if (summary.hasConflict()) {
                conflictCount++;
            }

            switch (summary.activeDecision()) {
                case APPROVED -> approvedCount++;
                case FLAGGED -> flaggedCount++;
                case EXEMPTED -> exemptedCount++;
                case ESCALATED -> escalatedCount++;
                case PENDING -> { /* unreviewed or pending resolution */ }
            }
        }

        int reviewedMatches = (int) summaries.values().stream()
                .filter(s -> s.activeDecision() != ReviewDecision.PENDING)
                .count();

        // Determine overall document review status
        String overallStatus;
        if (conflictCount > 0) {
            overallStatus = DocumentReviewSummary.STATUS_NEEDS_RESOLUTION;
        } else if (escalatedCount > 0 || flaggedCount > 0) {
            overallStatus = DocumentReviewSummary.STATUS_ACTION_REQUIRED;
        } else if (totalMatches > 0 && reviewedMatches == totalMatches) {
            overallStatus = DocumentReviewSummary.STATUS_CLEARED;
        } else if (reviewedMatches > 0) {
            overallStatus = DocumentReviewSummary.STATUS_IN_REVIEW;
        } else {
            overallStatus = DocumentReviewSummary.STATUS_UNREVIEWED;
        }

        return new DocumentReviewSummary(
                workspaceId,
                analysisId,
                overallStatus,
                totalMatches,
                reviewedMatches,
                approvedCount,
                flaggedCount,
                exemptedCount,
                escalatedCount,
                conflictCount,
                summaries,
                safeRecords
        );
    }

    /**
     * Evaluates a single match index's review records to compute consensus, conflict, and active decision.
     */
    public MatchReviewSummary evaluateMatch(
            int matchIndex,
            List<ReviewRecord> records,
            ConflictResolutionStrategy strategy
    ) {
        if (records == null || records.isEmpty()) {
            return MatchReviewSummary.unreviewed(matchIndex);
        }

        // Sort chronologically
        List<ReviewRecord> sorted = new ArrayList<>(records);
        sorted.sort(Comparator.comparing(ReviewRecord::createdAt).thenComparing(ReviewRecord::id));

        // Check for latest explicit resolution
        Optional<ReviewRecord> latestResolution = sorted.stream()
                .filter(ReviewRecord::isResolution)
                .reduce((first, second) -> second);

        if (latestResolution.isPresent()) {
            ReviewRecord res = latestResolution.get();
            return new MatchReviewSummary(
                    matchIndex,
                    res.decision(),
                    ReviewConflictStatus.RESOLVED,
                    sorted,
                    sorted.size(),
                    false,
                    res.decision().isActionable()
            );
        }

        // Find most recent review per unique reviewer
        Map<String, ReviewRecord> latestPerReviewer = new LinkedHashMap<>();
        for (ReviewRecord record : sorted) {
            latestPerReviewer.put(record.reviewerId(), record);
        }

        Set<ReviewDecision> distinctDecisions = new HashSet<>();
        for (ReviewRecord r : latestPerReviewer.values()) {
            distinctDecisions.add(r.decision());
        }

        ReviewConflictStatus conflictStatus;
        ReviewDecision activeDecision;
        boolean hasConflict = false;

        if (distinctDecisions.size() <= 1) {
            conflictStatus = ReviewConflictStatus.UNANIMOUS;
            activeDecision = distinctDecisions.iterator().next();
        } else {
            hasConflict = true;
            conflictStatus = ReviewConflictStatus.CONFLICT;
            activeDecision = strategy.resolve(new ArrayList<>(latestPerReviewer.values()));
        }

        return new MatchReviewSummary(
                matchIndex,
                activeDecision,
                conflictStatus,
                sorted,
                sorted.size(),
                hasConflict,
                activeDecision.isActionable() || hasConflict
        );
    }

    /**
     * Submits a new review decision after verifying state transition legality.
     */
    public ReviewRecord recordReview(
            String id,
            String workspaceId,
            String analysisId,
            int matchIndex,
            String reviewerId,
            String reviewerName,
            ReviewRole reviewerRole,
            ReviewDecision newDecision,
            String note,
            ReviewDecision currentDecision,
            boolean isResolution
    ) {
        // Enforce transition rules
        ReviewTransitionValidator.validateTransition(currentDecision, newDecision, reviewerRole, note);

        return new ReviewRecord(
                id,
                workspaceId,
                analysisId,
                matchIndex,
                reviewerId,
                reviewerName,
                reviewerRole,
                newDecision,
                note,
                java.time.Instant.now().toString(),
                isResolution
        );
    }
}
