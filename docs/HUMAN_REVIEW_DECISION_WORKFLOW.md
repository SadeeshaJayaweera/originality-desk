# Human Review and Decision Workflow Architecture

## 1. Overview and Academic Integrity Principles

Automated text matching, vector embeddings, and statistical stylometry provide **diagnostic evidence**, never final disciplinary verdicts. Academic judgment is required to evaluate:
- Contextual legitimacy (e.g., standard discipline formulas, common methodologies, shared assignment questions).
- Author intent and pedagogical stage (first-year drafting vs. post-graduate dissertation).
- Disciplinary conventions (law and literature require extensive verbatim quotation; mathematics and natural sciences rely on established phrasing).

Originality Desk introduces an auditable, multi-reviewer human decision workflow that tracks reviewer evaluations, arbitrates disagreements, and maintains an immutable chain of custody for academic integrity committees.

---

## 2. Review Decision Taxonomy

Review actions operate on individual similarity matches as well as complete submissions:

| Decision | Severity Rank | Description | Actionability |
|---|---|---|---|
| `PENDING` | 0 | Awaiting initial reviewer evaluation | No action required |
| `APPROVED` | 1 | Confirmed legitimate academic use (proper quotation, acceptable paraphrasing, common knowledge) | Non-actionable |
| `EXEMPTED` | 1 | Excluded from similarity calculations (assignment prompt, boilerplate, institutional template, bibliography) | Non-actionable |
| `FLAGGED` | 2 | Identified as suspicious or unattributed overlap requiring formal scrutiny | Actionable |
| `ESCALATED` | 3 | Referred to senior integrity officer, academic integrity board, or department head for hearing | Actionable |

---

## 3. Reviewer Hierarchy and State Transition Rules

Reviewer roles represent institutional authority levels:
1. `PEER_REVIEWER` (Level 1): Graduate teaching assistants, peer tutors, standard graders.
2. `SENIOR_REVIEWER` (Level 2): Course instructor, lead faculty, department reviewers.
3. `INTEGRITY_OFFICER` (Level 3): Academic integrity panel members, academic deans.
4. `ADMINISTRATOR` (Level 4): System administrators and institutional governance leads.

### Transition Governance Rules

Enforced by `ReviewTransitionValidator`:
- **Exemptions require justification**: Changing any state to `EXEMPTED` requires an explanatory note of at least 5 characters.
- **Clearing flagged matches requires justification**: Changing from `FLAGGED` to `APPROVED` requires an explanatory rationale.
- **De-escalation protection**: Once a match is `ESCALATED`, standard peer reviewers (`PEER_REVIEWER`) are **prohibited** from de-escalating to `APPROVED`, `FLAGGED`, or `EXEMPTED`. De-escalation strictly requires a `SENIOR_REVIEWER` or higher, accompanied by a formal adjudication note ($\ge 10$ characters).

---

## 4. Multi-Reviewer Consensus and Conflict Resolution

When multiple reviewers independently examine a document, individual assessments may diverge. The engine classifies match status into:
- `NO_REVIEWS`: No reviewer evaluations submitted.
- `UNANIMOUS`: Single reviewer or multiple reviewers with identical decisions.
- `CONFLICT`: Divergent decisions (e.g., Reviewer A marked `APPROVED` while Reviewer B marked `FLAGGED`).
- `RESOLVED`: A previous conflict has been adjudicated by an authorized senior reviewer or administrator.

### Conflict Resolution Strategies

Configurable via `ConflictResolutionStrategy`:
1. `SENIORITY`: Automatically selects the decision submitted by the reviewer with the highest institutional authority rank.
2. `MOST_CONSERVATIVE`: Adopts the most cautious decision (highest severity rank: `ESCALATED` > `FLAGGED` > `EXEMPTED`/`APPROVED`).
3. `MANUAL_ADJUDICATION`: Maintains `CONFLICT` state until an authorized administrator or integrity officer submits a definitive resolution record (`POST /api/analyses/:id/review-resolve`).

---

## 5. Consolidated Document Review Statuses

The `DocumentReviewSummary` aggregates all match evaluations into a document-level lifecycle status:
- `UNREVIEWED`: No matches have been evaluated.
- `IN_REVIEW`: Some matches reviewed, but unreviewed matches remain without conflicts or flags.
- `NEEDS_RESOLUTION`: One or more matches contain conflicting reviewer decisions requiring adjudication.
- `ACTION_REQUIRED`: One or more matches are currently `FLAGGED` or `ESCALATED`.
- `CLEARED`: All matches have been reviewed and approved or exempted; zero unresolved conflicts.

---

## 6. API Endpoints

| Endpoint | Method | Role Required | Description |
|---|---|---|---|
| `/api/analyses/:id/reviews` | `POST` | `REVIEWER` / `ADMIN` | Record an individual match review decision and note. |
| `/api/analyses/:id/reviews` | `GET` | `REVIEWER` / `ADMIN` | Paginated listing of review actions on an analysis. |
| `/api/analyses/:id/review-summary` | `GET` | `REVIEWER` / `ADMIN` | Consolidated summary (counts, conflict states, overall document status). |
| `/api/analyses/:id/review-resolve` | `POST` | `ADMIN` | Formal administrative adjudication of a conflicting match decision. |

---

## 7. Verification and Test Suite

Verified via:
1. `HumanReviewWorkflowTest`: Unit test suite covering decision parsing, role hierarchies, transition validations, conflict strategies, and overall document status calculations (8 tests).
2. `ReviewWorkflowIntegrationTest`: End-to-end integration test against database, testing review submission, multi-reviewer conflict detection, role-based 403 authorization checks, administrator resolution, and workspace tenant isolation (1 test).
