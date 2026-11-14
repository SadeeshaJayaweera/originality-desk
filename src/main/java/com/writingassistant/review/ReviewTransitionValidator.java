package com.writingassistant.review;

import java.util.Objects;

/**
 * Validates state transitions between review decisions according to institutional governance rules.
 */
public final class ReviewTransitionValidator {

    private ReviewTransitionValidator() {
        // Utility class
    }

    /**
     * Validates whether a state transition from currentDecision to newDecision is permitted
     * for the given reviewer role.
     *
     * @param currentDecision The active decision (or null/PENDING if no prior review).
     * @param newDecision     The proposed new decision.
     * @param role            The reviewer role attempting the transition.
     * @param note            The accompanying justification note.
     * @throws IllegalStateException If the transition is prohibited by governance policy.
     */
    public static void validateTransition(
            ReviewDecision currentDecision,
            ReviewDecision newDecision,
            ReviewRole role,
            String note
    ) {
        Objects.requireNonNull(newDecision, "newDecision must not be null");
        role = role != null ? role : ReviewRole.PEER_REVIEWER;
        currentDecision = currentDecision != null ? currentDecision : ReviewDecision.PENDING;

        // Note required when resolving, exempting, or de-escalating
        if (newDecision == ReviewDecision.EXEMPTED && (note == null || note.strip().length() < 5)) {
            throw new IllegalStateException("Exemption decisions require a justification note of at least 5 characters.");
        }

        if (currentDecision == ReviewDecision.FLAGGED && newDecision == ReviewDecision.APPROVED) {
            if (note == null || note.strip().length() < 5) {
                throw new IllegalStateException("Clearing a flagged match to APPROVED requires an explanatory note.");
            }
        }

        // De-escalating from ESCALATED requires elevated authority
        if (currentDecision == ReviewDecision.ESCALATED && newDecision != ReviewDecision.ESCALATED) {
            if (!role.canResolveEscalation()) {
                throw new IllegalStateException(String.format(
                        "Reviewer with role '%s' cannot de-escalate an ESCALATED decision. Requires %s or higher.",
                        role.getTitle(), ReviewRole.SENIOR_REVIEWER.getTitle()
                ));
            }
            if (note == null || note.strip().length() < 10) {
                throw new IllegalStateException("Adjudicating and de-escalating an escalated review requires a detailed formal note (min 10 characters).");
            }
        }
    }
}
