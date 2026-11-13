# Originality Desk browser interface

Dependency-free, accessible HTML/CSS/JavaScript. Runtime assets are **only** `index.html`, `styles.css`, and `app.js`, matching the standalone Java server's static allowlist. No desktop files, Maven configuration, or backend code are changed by this frontend.

## Integration

Serve the assets from the same origin as `/api/v1/`. Hash routes (`#/overview`, `#/reports/{id}`, etc.) do not need server-side fallback routing. The server must allow external self-hosted scripts/styles and same-origin connections; there are no inline handlers/styles, external fonts, CDNs, or HTML-injection sinks.

All fetches use `credentials: 'same-origin'`. JSON mutations have `Content-Type: application/json`. Uploads are raw `XMLHttpRequest` bodies with `application/octet-stream`, with actual upload progress, abort, and a real resend on retry. The client checks file extensions, non-empty files, and 10 MiB before sending; content validation remains the server's responsibility. A persistent upload notification provides abort even after changing views. An interrupted transfer may already have been accepted, so the UI warns users to check Documents before resending.

### Backend details verified and accommodated

- Current `/reports/{id}` returns a **flattened snapshot**, rather than the initial contract's nested `result`. Both shapes are supported. The UI also reads `/analyses/{id}/status` for report dates, status, and source count.
- Overlap values are **fractions in `[0,1]`**, displayed as percentages. Exact coverage is counted first; near coverage counts additional normalized words. Raw match values are shown separately without treating them as misconduct probabilities.
- Original match offsets are **UTF-16 half-open ranges**; JS string slicing matches this convention. Overlapping spans are segmented without duplicating original text. Invalid offsets are not highlighted; excerpts are still available.
- Administrative role: `INSTITUTION_ADMIN` (also accepts `ADMIN` for contract compatibility). Review saving requires `REVIEWER` or an administrator. Unauthorized users get an explanatory read-only state, not a nonfunctional save button.
- Reviews are paginated. All review pages are read, including after a successful POST, to display server-persisted records. Notes are limited to the server's 4,000 characters. Current decision strings: `NEEDS_REVIEW`, `ACCEPTED`, `FLAGGED`, `DISMISSED`; the backend accepts uppercase/underscore decisions.
- Profiles reflect the actual engine: QUICK = 12-word exact shingles only; STANDARD = 8-word exact shingles + lexical near matching; REFERENCE_AWARE = 6-word exact shingles + lexical near matching, with heuristically detected trailing reference lists excluded. Up to 50 sources can be selected.
- Deleting a document deletes dependent analyses/report snapshots/reviews in the current backend; the confirmation calls this out.
- Document/history search and status/profile filters are explicitly **page-local**. Document-kind filtering and pagination are server-backed. Source selections persist across source pages and are explicitly sent as `sourceIds`.
- Admin health, jobs, and audit logs use real endpoints with pagination for lists; original response fields are also inspectable.

No backend changes are required for extra assets. Review auth/password limits and these shape details if the standalone API changes.

## Honest boundaries

The public sample is explicitly labeled **static and synthetic**. It has invented text, no calculated scores or dates, and no fake exports/reviewer saves. It is never inserted into workspace records.

AI-authorship detection, semantic analysis, OCR, email password reset, MFA, invitations, profile editing, and server-preference editing are marked unavailable. The UI makes no web-wide coverage, retention, encryption, compliance, accuracy, or plagiarism-verdict claim. Dashboard figures come directly from the server; no trends are fabricated.

Appearance is saved in `localStorage` under `originality-desk-theme`; system/light/dark are supported, with an honest fallback when storage is blocked. Account data and reports are not saved to localStorage.

## Verification

Dependency-free unit/static checks:

```sh
node --check src/main/resources/web/app.js
node --test src/main/resources/web/frontend.test.cjs
```

Optional browser smoke test, using an **already installed** Playwright module/browser (not an application dependency):

```sh
node src/main/resources/web/browser-smoke.cjs /absolute/path/to/playwright
```

The smoke test starts/stops a temporary loopback fixture server with a strict self-only CSP. Its clearly synthetic records exercise registration/login/logout, real XHR sends and abort/retry, local validation and server errors, explicit comparison IDs/profiles, status polling, hostile-text rendering, paginated saved reviews, four export requests/downloads, history filtering, workspace/admin views, local theme persistence, delete confirmation, and 320/375/768/1440 px layouts. It captures browser script errors and CSP violations, and creates no screenshots or production records.

These fixture checks are not a claim that the Java backend has been end-to-end tested. Integration should still verify PDF/DOCX/TXT extraction, real server sessions/authorization, report file contents, cancellation, restart persistence, and deployment behavior against the assembled Java application.
