# Real Java + Playwright browser verification

This runner launches the **built application jar**, not a fixture API. It makes no Playwright route interceptions, installs nothing, and does not change the desktop entry point. It uses the standalone server class explicitly with `java -cp`.

## Requirements

- Node.js 20+ and Java 21+ available on `PATH` (or Java via `JAVA_HOME`/`PATH`).
- A completed shaded jar at `target/writing-assistant-1.0.0-SNAPSHOT.jar`, or an explicit jar path.
- An **already installed** Playwright Node module and its Chromium browser. Pass the absolute module directory; this script never invokes npm/npx or downloads a browser.
- Permission to launch Java/Chromium and bind a temporary loopback port.

From the repository root:

```sh
node --test scripts/e2e/fixtures.test.cjs scripts/e2e/markup.test.cjs
node scripts/e2e/real-browser.cjs /absolute/path/to/node_modules/playwright
```

The optional second argument is the jar path, resolved relative to the repository root:

```sh
node scripts/e2e/real-browser.cjs /absolute/path/to/node_modules/playwright target/writing-assistant-1.0.0-SNAPSHOT.jar
```

Default workflow deadline: 180 seconds. `E2E_TIMEOUT_MS` can be set to 30000–300000. Browser actions, startup, HTTP requests, extraction waits, downloads, and shutdown have additional bounds. The script exits nonzero on failure and publishes screenshots only after all checks and cleanup succeed. If the jar's HTML/CSS/JS differ from the checkout, it fails with a stale-asset message; rebuild rather than capturing outdated UI.

## Real checks

1. Start Java with `DESK_HOST=127.0.0.1`, `DESK_PORT=0`, `DESK_PUBLIC_ORIGIN=http://127.0.0.1:0`. Read the actual resolved origin from startup output; the backend validates that same host/origin for requests. There is no preselected-port race or connection to an existing deployment.
2. Read actual readiness and verify the jar serves this checkout's assets.
3. Check the landing structure/navigation targets, theme-button markup, and highlighted sample; capture the unauthenticated landing page. Static balanced-tag regression tests catch malformed tags and tag fragments inserted into attributes.
4. Register, log out, and sign in through browser forms; verify real HttpOnly/SameSite session cookies and an empty workspace.
5. Upload explicitly synthetic reference/submission TXT files through real browser XHR; wait for READY extraction in the API and the browser.
6. Select those exact documents, run STANDARD, check the submitted `sourceIds`/profile/idempotency key, and let the browser poll to completion.
7. Check actual engine matches, UTF-16 offsets, original highlighted text, and side-by-side source/submission passages.
8. Save a reviewer decision/note; verify it by API and after a full browser reload.
9. Download a real authenticated PDF; check content type, nontrivial bytes, `%PDF-` header, and `%%EOF` marker. No claim is made that these byte checks validate every PDF rendering detail.
10. Register a second account in an isolated browser context. Verify its workspace cannot list/read/delete the first account's documents or access its analysis/report/PDF/reviews.
11. Re-upload the actual generated PDF through the API and wait for text extraction. Generate a deterministic, minimal, text-only DOCX with Node's standard library (stored ZIP + CRC32), upload it through the API, and verify READY with a positive word count.
12. Capture the real desktop report; persist dark theme and test keyboard-operated mobile navigation; capture the same report at a 390 × 844 viewport. Check for horizontal overflow and optional `null`/`undefined` or raw-markup rendering artifacts. Successful toasts are dismissed using their real buttons; error notices cause capture to fail, rather than being concealed.
13. Delete the submission through the UI and verify dependent analysis/report/reviews are gone. Delete other test files and assert zero documents/analyses/storage; sign out. Check no browser runtime errors or observed CSP violations occurred.

## Isolation and artifacts

A fresh OS temporary directory holds the server database, blobs, Java user home/temp, downloads, and staging screenshots. Java receives only the environment needed for this isolated process, not ambient application/API secrets. Passwords are generated in memory, never printed or placed in command arguments/artifacts. Both account names and email addresses are unmistakably synthetic (`example.test`). The browser and Java are stopped in `finally`; the directory is recursively removed on success, failure, deadline, SIGINT, and SIGTERM. Uncatchable process termination (for example SIGKILL or a host crash) cannot guarantee cleanup.

Only these successful-run artifacts are copied into the repository:

- `docs/screenshots/landing.png`
- `docs/screenshots/report.png`
- `docs/screenshots/mobile.png`
- `docs/screenshots/metadata.json`: observed check timings, browser/application hashes, engine values, file sizes, capture dimensions/hashes, and cleanup results. No cookies, passwords, database, or downloaded PDF are retained.
- `docs/screenshots/README.md`: generated captions and measured results for exactly the same run, refreshed automatically with the images and metadata.

Screenshots are actual browser pixels, not mocked or retouched. The report contains synthetic test material analyzed by the real engine, not a claim about a real publication, student, institution, or production workload. See `docs/screenshots/README.md` for captions and the limits of the observed run.

These targeted checks do not establish production deployment security, exhaustive browser/format support, restart persistence, concurrency behavior, or a complete test of every route.
