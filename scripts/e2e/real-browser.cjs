#!/usr/bin/env node
/* Real Java/browser integration: no mocked routes, fixture server, or npm installation. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');
const { spawn } = require('node:child_process');
const { once } = require('node:events');
const { performance } = require('node:perf_hooks');
const { referenceText, submissionText, reviewerNote, syntheticDocx } = require('./fixtures.cjs');

const root = path.resolve(__dirname, '../..');
const outputDir = path.join(root, 'docs/screenshots');
const timeoutMs = Number(process.env.E2E_TIMEOUT_MS || 180000);
const sha256 = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const elapsed = start => Math.round((performance.now() - start) * 10) / 10;

async function bounded(promise, ms, message) {
  let timer;
  try { return await Promise.race([promise, new Promise((_, reject) => { timer = setTimeout(() => reject(new Error(message)), ms); })]); }
  finally { clearTimeout(timer); }
}

async function stopJava(child) {
  if (!child || child.exitCode !== null || child.signalCode !== null) return { stopped: true, forced: false };
  const exited = once(child, 'exit'); child.kill('SIGTERM');
  try { await bounded(exited, 8000, 'Java graceful shutdown exceeded 8 seconds'); return { stopped: true, forced: false }; }
  catch (_) { child.kill('SIGKILL'); await bounded(exited, 3000, 'Java process did not stop after SIGKILL'); return { stopped: true, forced: true }; }
}

async function main() {
  const playwrightPath = process.argv[2];
  if (!playwrightPath || ['--help', '-h'].includes(playwrightPath)) {
    console.log('Usage: node scripts/e2e/real-browser.cjs /absolute/path/to/playwright [path/to/application.jar]');
    console.log('Requires an already installed Playwright module + Chromium, Java 21+, and the built shaded jar.');
    console.log('Optional E2E_TIMEOUT_MS: 30000..300000 (default 180000). Nothing is downloaded or installed.');
    if (!playwrightPath) process.exitCode = 2;
    return;
  }
  assert.ok(Number.isFinite(timeoutMs) && timeoutMs >= 30000 && timeoutMs <= 300000, 'E2E_TIMEOUT_MS must be 30000..300000');
  const { chromium } = require(path.resolve(playwrightPath));
  assert.ok(chromium?.launch, 'The supplied module does not expose Playwright Chromium');
  const jar = path.resolve(root, process.argv[3] || 'target/writing-assistant-1.0.0-SNAPSHOT.jar');
  const jarStat = await fs.stat(jar); assert.ok(jarStat.isFile() && jarStat.size > 0, 'The jar is missing or empty; build it first');
  const jarHash = sha256(await fs.readFile(jar));
  const temp = await fs.mkdtemp(path.join(os.tmpdir(), 'originality-desk-e2e-'));
  const downloads = path.join(temp, 'downloads'); await fs.mkdir(downloads);
  const captureDir = path.join(temp, 'screenshots'); await fs.mkdir(captureDir);
  const started = performance.now();
  const evidence = {
    status: 'running', startedAt: new Date().toISOString(),
    application: { jar: path.basename(jar), bytes: jarStat.size, sha256: jarHash, mainClass: 'com.writingassistant.server.OriginalityDeskServer' },
    isolation: { bindHost: '127.0.0.1', requestedPort: 0, temporaryDataDirectory: true, javaHomeAndTmpIsolated: true, ambientJavaSecretsNotInherited: true },
    syntheticData: true, mockedRequests: false, installedDependencies: false,
    environment: { node: process.version, platform: process.platform, architecture: process.arch },
    checks: [], screenshots: [], observedAnalysisStatuses: [], apiResponses: 0, browserApiResponses: 0,
    notCovered: ['Production HTTPS/reverse proxy behavior', 'OCR or scanned PDFs (unavailable)', 'Concurrent-load/performance guarantees', 'Persistence across a Java server restart', 'Every possible file format or failure mode']
  };
  let child, browser, failure, stdout = '', stderr = '', origin;
  let deadlineTimer;
  const cancellation = new AbortController();
  const cancel = signal => cancellation.abort(new Error(`Interrupted by ${signal}`));
  const onInt = () => cancel('SIGINT'), onTerm = () => cancel('SIGTERM');
  process.once('SIGINT', onInt); process.once('SIGTERM', onTerm);
  const abortPromise = new Promise((_, reject) => cancellation.signal.addEventListener('abort', () => reject(cancellation.signal.reason), { once: true }));
  deadlineTimer = setTimeout(() => cancellation.abort(new Error(`Real browser workflow exceeded ${timeoutMs} ms`)), timeoutMs);
  async function step(name, run) {
    const began = performance.now(); const result = await run(); const ms = elapsed(began);
    evidence.checks.push({ name, status: 'passed', durationMs: ms }); console.log(`PASS ${name} (${ms} ms)`); return result;
  }
  const runtimeErrors = [], cspErrors = [];
  try {
    await Promise.race([workflow(), abortPromise]);
    evidence.status = 'passed';
  } catch (error) { failure = error; evidence.status = 'failed'; }
  finally {
    clearTimeout(deadlineTimer); process.removeListener('SIGINT', onInt); process.removeListener('SIGTERM', onTerm);
    try { if (browser) await bounded(browser.close(), 10000, 'Browser shutdown exceeded 10 seconds'); evidence.isolation.browserClosed = true; }
    catch (error) { failure ||= error; evidence.status = 'failed'; }
    try { evidence.isolation.javaShutdown = await stopJava(child); }
    catch (error) { failure ||= error; evidence.status = 'failed'; }
    // Stage only non-secret screenshots. Database, blobs, downloaded PDF, passwords and cookies are never published.
    let captures;
    if (!failure) {
      try {
        captures = await Promise.all(evidence.screenshots.map(async capture => ({ ...capture, bytes: await fs.readFile(path.join(captureDir, capture.file)) })));
      } catch (error) { failure = error; evidence.status = 'failed'; }
    }
    try { await fs.rm(temp, { recursive: true, force: true, maxRetries: 3, retryDelay: 100 }); evidence.isolation.temporaryDirectoryRemoved = true; }
    catch (error) { failure ||= error; evidence.status = 'failed'; }
    evidence.finishedAt = new Date().toISOString(); evidence.durationMs = elapsed(started);
    if (!failure) {
      await fs.mkdir(outputDir, { recursive: true });
      for (const capture of captures) await fs.writeFile(path.join(outputDir, capture.file), capture.bytes);
      await fs.writeFile(path.join(outputDir, 'metadata.json'), JSON.stringify(evidence, null, 2) + '\n');
      await fs.writeFile(path.join(outputDir, 'README.md'), captureReadme(evidence));
      console.log(`PASS cleanup: browser closed, Java stopped${evidence.isolation.javaShutdown.forced ? ' (forced)' : ' gracefully'}, temporary data removed`);
      console.log(`REAL JAVA E2E PASSED: ${evidence.checks.length} checks in ${evidence.durationMs} ms; PDF ${evidence.pdf.bytes} bytes; ${evidence.result.matches} evidence matches.`);
      console.log('Published docs/screenshots/{landing.png,report.png,mobile.png,metadata.json,README.md}. No credentials or persistent test database were retained.');
    }
  }
  if (failure) {
    console.error(`REAL JAVA E2E FAILED after ${evidence.checks.length} passed checks: ${failure.stack || failure}`);
    if (runtimeErrors.length) console.error('Browser runtime errors:', JSON.stringify(runtimeErrors));
    if (cspErrors.length) console.error('Browser CSP violations:', JSON.stringify(cspErrors));
    // Server logging contains no authentication request bodies; retain only the bounded diagnostic tail in console.
    if (stderr) console.error('Java stderr (last 6000 characters):\n' + stderr.slice(-6000));
    if (!origin && stdout) console.error('Java startup stdout (last 2000 characters):\n' + stdout.slice(-2000));
    console.error('No new screenshot artifacts were published.');
    throw failure;
  }

  async function workflow() {
    await step('Start isolated Java server on an OS-assigned loopback port', async () => {
      const env = { PATH: process.env.PATH, HOME: temp, TMPDIR: temp, LANG: 'en_US.UTF-8', DESK_DATA_DIR: path.join(temp, 'server-data'), DESK_HOST: '127.0.0.1', DESK_PORT: '0', DESK_PUBLIC_ORIGIN: 'http://127.0.0.1:0', DESK_SECURE_COOKIES: 'false' };
      if (process.env.JAVA_HOME) env.JAVA_HOME = process.env.JAVA_HOME;
      const args = ['-Djava.awt.headless=true', `-Duser.home=${temp}`, `-Djava.io.tmpdir=${temp}`, '-cp', jar, 'com.writingassistant.server.OriginalityDeskServer'];
      child = spawn('java', args, { cwd: temp, env, stdio: ['ignore', 'pipe', 'pipe'] });
      child.stderr.on('data', data => { stderr = (stderr + data).slice(-64000); });
      origin = await bounded(new Promise((resolve, reject) => {
        child.once('error', reject);
        child.once('exit', (code, signal) => { if (!origin) reject(new Error(`Java exited before readiness: code=${code}, signal=${signal}`)); });
        child.stdout.on('data', data => { stdout = (stdout + data).slice(-64000); const match = stdout.match(/Originality Desk server listening at (http:\/\/127\.0\.0\.1:([0-9]+))/); if (match && Number(match[2]) > 0) resolve(match[1]); });
      }), 20000, 'Java startup did not announce its origin within 20 seconds');
      evidence.isolation.resolvedOrigin = origin;
      const health = await fetch(`${origin}/health/ready`, { signal: AbortSignal.timeout(5000) }); assert.equal(health.status, 200); assert.equal((await health.json()).status, 'UP');
    });
    await step('Launch installed Chromium and verify served assets match this checkout', async () => {
      browser = await chromium.launch({ headless: true, timeout: 15000 }); evidence.environment.browser = browser.version();
      for (const file of ['app.js', 'styles.css', 'index.html']) {
        const response = await fetch(`${origin}/${file}`, { signal: AbortSignal.timeout(5000) }); assert.equal(response.status, 200);
        const served = Buffer.from(await response.arrayBuffer()), local = await fs.readFile(path.join(root, 'src/main/resources/web', file));
        assert.equal(sha256(served), sha256(local), `Jar has stale ${file}; rebuild it before running E2E`);
        evidence.application[file] = sha256(served);
      }
    });
    const context = await browser.newContext({ acceptDownloads: true, viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1, colorScheme: 'light', reducedMotion: 'reduce', locale: 'en-US', timezoneId: 'UTC' });
    const page = await context.newPage(); watch(page);
    page.setDefaultTimeout(12000); page.setDefaultNavigationTimeout(15000);
    const password = crypto.randomBytes(30).toString('base64url');
    const primary = { name: 'Synthetic Reviewer', email: 'reviewer-e2e@example.test', workspace: 'Synthetic Research Lab' };
    await step('Validate landing markup and capture the actual public page', async () => {
      await go(page, ''); await page.getByRole('heading', { level: 1 }).waitFor();
      for (const id of ['capabilities', 'process', 'privacy']) assert.equal(await page.locator(`main > section#${id}`).count(), 1, `Missing or malformed ${id} section`);
      assert.equal(await page.locator('.principles-strip > span').count(), 4); assert.equal(await page.locator('.paper-preview mark').count(), 1);
      assert.equal(await page.locator('.header-actions > button.icon-button.theme-toggle').textContent(), '◐');
      assert.equal(await page.locator('.process-list > li').count(), 4); assert.equal(await page.locator('.closing-section').count(), 1); assert.equal(await page.locator('.faq-list details').count(), 5);
      await capture(page, 'landing.png', 'Public landing, light theme, before any account is created.');
    });
    await step('Register a primary account through the browser', async () => { await register(page, primary, password); const me = await json(context, '/auth/me'); assert.equal(me.user.email, primary.email); });
    await step('Log out and sign in through the real session API', async () => {
      await page.getByRole('button', { name: 'Sign out', exact: true }).click(); await page.waitForURL(`${origin}/#/`);
      assert.equal((await request(context, '/auth/me')).status(), 401);
      await go(page, 'login'); await page.getByLabel('Email address', { exact: true }).fill(primary.email); await page.getByLabel('Password', { exact: true }).fill(password);
      const response = page.waitForResponse(r => r.url() === `${origin}/api/v1/auth/login` && r.request().method() === 'POST');
      await page.getByRole('button', { name: 'Sign in →', exact: true }).click(); assert.equal((await response).status(), 200); await page.waitForURL('**/#/overview');
      const cookies = await context.cookies(origin); const session = cookies.find(cookie => cookie.name === 'desk_session'); assert.ok(session?.httpOnly); assert.equal(session.sameSite, 'Strict');
      const dashboard = await json(context, '/dashboard'); assert.equal(dashboard.totalDocuments, 0); assert.equal(dashboard.completedAnalyses, 0);
    });
    let reference, submission, analysisId, report, selectedMatchIndex;
    await step('Upload synthetic reference TXT using the real browser XHR', async () => { reference = await upload(page, 'references', 'Synthetic reference.txt', 'REFERENCE', referenceText); });
    await step('Upload synthetic submission TXT using the real browser XHR', async () => { submission = await upload(page, 'documents', 'Synthetic submission.txt', 'SUBMISSION', submissionText); });
    evidence.documents = { reference: docMetrics(reference), submission: docMetrics(submission) };
    await step('Run STANDARD analysis with explicitly selected source IDs and poll to completion', async () => {
      await go(page, 'new'); await page.locator(`input[name="submission"][value="${submission.id}"]`).check(); await page.locator(`input[name="sources"][value="${reference.id}"]`).check(); await page.locator('input[name="profile"][value="STANDARD"]').check();
      const pending = page.waitForResponse(r => r.url() === `${origin}/api/v1/analyses` && r.request().method() === 'POST');
      await page.getByRole('button', { name: 'Start analysis →', exact: true }).click(); const response = await pending; assert.equal(response.status(), 202);
      assert.deepEqual(response.request().postDataJSON(), { documentId: submission.id, sourceIds: [reference.id], profile: 'STANDARD' }); assert.ok(response.request().headers()['idempotency-key']);
      const created = await response.json(); analysisId = created.id; assert.ok(analysisId); await page.waitForURL(`**/#/analysis/${analysisId}`);
      await page.getByRole('link', { name: 'Open evidence report →', exact: true }).waitFor({ timeout: 45000 });
      await assertNoRenderingArtifacts(page);
      const analysis = await json(context, `/analyses/${analysisId}/status`); assert.equal(analysis.status, 'COMPLETED'); assert.equal(analysis.sourceCount, 1); assert.equal(analysis.profile, 'STANDARD');
      const completed = await context.request.get(`${origin}/api/v1/reports/${analysisId}`); evidence.apiResponses += 1; assert.equal(completed.status(), 200); report = await completed.json();
      const result = report.result || report; assert.ok(result.matches.length > 0, 'The intentional shared passage produced no evidence');
      assert.equal(report.documentText, submissionText); assert.ok(result.matches.every(match => match.sourceId === reference.id));
      selectedMatchIndex = result.matches.reduce((best, match, index) => (String(match.method).includes('EXACT') && match.end - match.start > (result.matches[best]?.end - result.matches[best]?.start || 0)) ? index : best, 0);
      assert.ok(String(result.matches[selectedMatchIndex].method).includes('EXACT'));
      evidence.result = { engineVersion: result.engineVersion, profile: result.profile, wordCount: result.wordCount, matches: result.matches.length, exactOverlap: result.exactOverlap, nearOverlap: result.nearOverlap, totalOverlap: result.totalOverlap, citations: result.citations.length, sourceCount: analysis.sourceCount };
    });
    await step('Inspect actual highlighted text and side-by-side source evidence', async () => {
      await page.getByRole('link', { name: 'Open evidence report →', exact: true }).click(); await page.locator('.match-card').first().waitFor();
      assert.equal(await page.locator('.document-text').textContent(), report.documentText); assert.ok(await page.locator('.passage-mark').count() > 0);
      await page.locator('.match-card').nth(selectedMatchIndex).click();
      const result = report.result || report, match = result.matches[selectedMatchIndex];
      assert.equal(await page.locator('.evidence-excerpt blockquote').nth(0).textContent(), match.submittedPassage); assert.equal(await page.locator('.evidence-excerpt blockquote').nth(1).textContent(), match.sourcePassage);
      assert.equal(submissionText.slice(match.start, match.end), match.submittedPassage); assert.equal(referenceText.slice(match.sourceStart, match.sourceEnd), match.sourcePassage);
      await contains(page, '.evidence-meta', match.method); await contains(page, '.match-inspector', match.citationObservation);
    });
    await step('Save a reviewer decision and verify persistence after a full reload', async () => {
      await page.getByLabel('Reviewer decision', { exact: true }).selectOption('FLAGGED'); await page.getByLabel('Reviewer note', { exact: true }).fill(reviewerNote);
      const pending = page.waitForResponse(r => r.url().startsWith(`${origin}/api/v1/analyses/${analysisId}/reviews`) && r.request().method() === 'POST');
      await page.getByRole('button', { name: 'Save reviewer decision', exact: true }).click(); const saved = await pending; assert.equal(saved.status(), 201);
      const reviews = await json(context, `/analyses/${analysisId}/reviews?page=1&size=100`); const record = reviews.items.find(item => item.note === reviewerNote); assert.equal(record?.decision, 'FLAGGED'); assert.equal(record.matchIndex, selectedMatchIndex);
      await page.reload(); await page.locator('.match-card').nth(selectedMatchIndex).click(); assert.equal(await page.getByLabel('Reviewer note', { exact: true }).inputValue(), reviewerNote); assert.equal(await page.getByLabel('Reviewer decision', { exact: true }).inputValue(), 'FLAGGED');
    });
    let pdfBytes;
    await step('Download the authenticated PDF and assert its real bytes', async () => {
      const pendingDownload = page.waitForEvent('download'); const pendingResponse = page.waitForResponse(r => r.url() === `${origin}/api/v1/reports/${analysisId}/pdf`);
      await page.getByRole('button', { name: 'PDF ↓', exact: true }).click(); const response = await pendingResponse; assert.equal(response.status(), 200); assert.match(response.headers()['content-type'], /application\/pdf/);
      const download = await pendingDownload; assert.equal(await download.failure(), null); const file = path.join(downloads, 'actual-report.pdf'); await download.saveAs(file); pdfBytes = await fs.readFile(file);
      assert.equal(pdfBytes.subarray(0, 5).toString('ascii'), '%PDF-'); assert.ok(pdfBytes.includes(Buffer.from('%%EOF'))); assert.ok(pdfBytes.length > 1000);
      evidence.pdf = { bytes: pdfBytes.length, sha256: sha256(pdfBytes), contentType: response.headers()['content-type'], header: '%PDF-', eofPresent: true };
    });
    await step('Verify workspace isolation with a second real browser account', async () => {
      const second = await browser.newContext({ viewport: { width: 1280, height: 900 }, reducedMotion: 'reduce', locale: 'en-US' });
      try {
        const otherPage = await second.newPage(); watch(otherPage); otherPage.setDefaultTimeout(12000);
        await register(otherPage, { name: 'Synthetic Isolation Reviewer', email: 'isolation-e2e@example.test', workspace: 'Separate Synthetic Workspace' }, crypto.randomBytes(30).toString('base64url'));
        const me = await json(second, '/auth/me'), primaryMe = await json(context, '/auth/me'); assert.notEqual(me.workspace.id, primaryMe.workspace.id); assert.notEqual(me.user.id, primaryMe.user.id);
        const list = await json(second, '/documents?page=1&size=20'); assert.equal(list.total, 0);
        for (const route of [`/documents/${submission.id}`, `/analyses/${analysisId}`, `/reports/${analysisId}`, `/reports/${analysisId}/pdf`, `/analyses/${analysisId}/reviews`]) assert.equal((await request(second, route)).status(), 404, `Cross-workspace request leaked ${route}`);
        assert.equal((await request(second, `/documents/${submission.id}`, { method: 'DELETE' })).status(), 404); assert.equal((await request(context, `/documents/${submission.id}`)).status(), 200);
        assert.equal((await request(second, '/auth/logout', { method: 'POST', data: {} })).status(), 204); assert.equal((await request(second, '/auth/me')).status(), 401);
      } finally { await second.close(); }
    });
    let pdfDocument, docxDocument;
    await step('Upload the real downloaded PDF through the API and extract its text', async () => {
      const response = await request(context, '/documents?filename=Synthetic%20report%20roundtrip.pdf&kind=REFERENCE', { method: 'POST', data: pdfBytes, headers: { 'Content-Type': 'application/pdf' } }); assert.equal(response.status(), 201); const doc = await response.json(); pdfDocument = await readyDocument(context, doc.id); assert.ok(pdfDocument.wordCount > 0); assert.ok(pdfDocument.pageCount >= 1); evidence.documents.pdf = docMetrics(pdfDocument);
    });
    await step('Upload a generated synthetic DOCX through the API and extract its text', async () => {
      const bytes = syntheticDocx(); const response = await request(context, '/documents?filename=Synthetic%20extraction.docx&kind=REFERENCE', { method: 'POST', data: bytes, headers: { 'Content-Type': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' } }); assert.equal(response.status(), 201);
      const doc = await response.json(); docxDocument = await readyDocument(context, doc.id); assert.ok(docxDocument.wordCount > 0); evidence.documents.docx = docMetrics(docxDocument);
    });
    await step('Capture the actual desktop evidence report with its saved review', async () => {
      await go(page, `reports/${analysisId}`); await page.locator('.match-card').nth(selectedMatchIndex).click(); await contains(page, '.review-history', reviewerNote); await capture(page, 'report.png', 'Actual STANDARD analysis of two synthetic TXT documents, light desktop theme, with server-persisted reviewer note.');
    });
    await step('Persist dark theme, exercise mobile navigation, and capture the actual mobile report', async () => {
      await go(page, 'settings'); await page.getByLabel('Dark', { exact: true }).check(); assert.equal(await page.evaluate(() => localStorage.getItem('originality-desk-theme')), 'dark');
      await page.setViewportSize({ width: 390, height: 844 }); await page.getByRole('button', { name: 'Open navigation', exact: true }).click(); assert.equal(await page.locator('.sidebar').getAttribute('aria-modal'), 'true'); await page.locator('.sidebar .mobile-close').press('Escape'); assert.equal(await page.getByRole('button', { name: 'Open navigation', exact: true }).getAttribute('aria-expanded'), 'false');
      await go(page, `reports/${analysisId}`); await page.locator('.match-card').nth(selectedMatchIndex).click(); assert.equal(await page.locator('html').getAttribute('data-theme'), 'dark'); await contains(page, '.review-history', reviewerNote); await capture(page, 'mobile.png', 'The same real synthetic-data report at 390 × 844 CSS pixels, persisted dark theme; navigation opens and closes by keyboard.');
    });
    await step('Delete the submission in the UI and verify dependent reports/reviews are removed', async () => {
      await deleteViaUi(page, submission.id);
      for (const route of [`/documents/${submission.id}`, `/analyses/${analysisId}`, `/reports/${analysisId}`, `/analyses/${analysisId}/reviews`]) assert.equal((await request(context, route)).status(), 404);
      assert.equal((await request(context, `/documents/${reference.id}`)).status(), 200);
    });
    await step('Delete remaining synthetic documents and verify an empty workspace', async () => {
      await deleteViaUi(page, reference.id);
      for (const doc of [pdfDocument, docxDocument]) assert.equal((await request(context, `/documents/${doc.id}`, { method: 'DELETE' })).status(), 204);
      assert.equal((await json(context, '/documents?page=1&size=20')).total, 0); assert.equal((await json(context, '/analyses?page=1&size=20')).total, 0);
      const dashboard = await json(context, '/dashboard'); assert.equal(dashboard.totalDocuments, 0); assert.equal(dashboard.storageBytes, 0); assert.equal(dashboard.completedAnalyses, 0);
      assert.equal((await json(context, '/admin/health')).status, 'UP');
      assert.equal((await request(context, '/auth/logout', { method: 'POST', data: {} })).status(), 204); assert.equal((await request(context, '/documents')).status(), 401);
    });
    await step('Check browser script errors, CSP violations, and the final artifact identity', async () => {
      assert.deepEqual(runtimeErrors, []); assert.deepEqual(cspErrors, []); assert.equal(sha256(await fs.readFile(jar)), jarHash, 'The jar changed during this run');
      evidence.browserRuntimeErrors = 0; evidence.cspViolations = 0;
      evidence.observedAnalysisStatuses = [...new Set(evidence.observedAnalysisStatuses)];
      await context.close();
    });
  }

  function watch(page) {
    page.on('pageerror', error => runtimeErrors.push(error.message));
    page.on('console', message => { if (/Content Security Policy|violates.*(?:script-src|style-src|connect-src)/i.test(message.text())) cspErrors.push(message.text()); });
    page.on('response', async response => {
      const url = response.url(); if (!url.startsWith(`${origin}/api/v1/`)) return;
      evidence.apiResponses += 1; evidence.browserApiResponses += 1;
      if (/\/analyses\/[^/]+\/status$/.test(url) && response.status() === 200) { try { evidence.observedAnalysisStatuses.push((await response.json()).status); } catch (_) { /* Browser shutdown may interrupt a final response. */ } }
    });
  }
  async function request(context, route, options = {}) {
    evidence.apiResponses += 1;
    return context.request.fetch(`${origin}/api/v1${route}`, { ...options, timeout: 15000, headers: { Origin: origin, ...(options.headers || {}) } });
  }
  async function json(context, route) { const response = await request(context, route); assert.equal(response.status(), 200, `${route}: ${await response.text()}`); return response.json(); }
  async function go(page, route) { await page.goto(`${origin}/#/${route}`, { waitUntil: 'domcontentloaded' }); }
  async function contains(page, selector, value) { await page.waitForFunction(({ selector, value }) => document.querySelector(selector)?.textContent.includes(value), { selector, value }); }
  async function register(page, account, password) {
    await go(page, 'register'); await page.getByLabel('Full name', { exact: true }).fill(account.name); await page.getByLabel('Workspace name', { exact: true }).fill(account.workspace); await page.getByLabel('Email address', { exact: true }).fill(account.email); await page.getByLabel('Password', { exact: true }).fill(password);
    const response = page.waitForResponse(r => r.url() === `${origin}/api/v1/auth/register` && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Create account →', exact: true }).click(); assert.equal((await response).status(), 201); await page.waitForURL('**/#/overview'); await page.locator('.metrics-grid').waitFor();
  }
  async function readyDocument(context, id) {
    const started = performance.now(), states = [];
    while (performance.now() - started < 45000) {
      const doc = await json(context, `/documents/${id}`); states.push(doc.status);
      if (doc.status === 'READY') { assert.ok(doc.wordCount > 0); return { ...doc, observedExtractionStatuses: [...new Set(states)], readyWaitMs: elapsed(started) }; }
      assert.notEqual(doc.status, 'FAILED', `Document extraction failed: ${doc.error}`); await sleep(500);
    }
    throw new Error('Document extraction did not reach READY within 45 seconds');
  }
  async function upload(page, route, filename, kind, contents) {
    await go(page, route); await page.locator('input[type="file"]').setInputFiles({ name: filename, mimeType: 'text/plain', buffer: Buffer.from(contents, 'utf8') });
    const pending = page.waitForResponse(r => r.url().startsWith(`${origin}/api/v1/documents?`) && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Upload document ↑', exact: true }).click(); const response = await pending; assert.equal(response.status(), 201, `TXT upload: ${await response.text()}`);
    const uploaded = await response.json(); assert.equal(uploaded.kind, kind); assert.equal(uploaded.bytes, Buffer.byteLength(contents)); assert.equal(response.request().headers()['content-type'], 'application/octet-stream');
    const ready = await readyDocument(page.context(), uploaded.id); await contains(page, 'tbody', filename);
    await page.waitForFunction(id => { const row = [...document.querySelectorAll('tbody tr')].find(row => row.querySelector('a')?.getAttribute('href') === `#/documents/${id}`); return row?.querySelector('.badge')?.textContent === 'ready'; }, uploaded.id, { timeout: 12000 });
    return { ...ready, observedExtractionStatuses: [...new Set([uploaded.status, ...ready.observedExtractionStatuses])] };
  }
  function docMetrics(doc) { return { kind: doc.kind, status: doc.status, bytes: doc.bytes, wordCount: doc.wordCount, pageCount: doc.pageCount, observedExtractionStatuses: doc.observedExtractionStatuses, readyWaitMs: doc.readyWaitMs }; }
  async function assertNoRenderingArtifacts(page) {
    const artifacts = await page.locator('body').evaluate(body => {
      const walker = document.createTreeWalker(body, NodeFilter.SHOW_TEXT), found = [];
      while (walker.nextNode()) {
        const value = walker.currentNode.textContent.trim();
        if (/^(null|undefined)$/.test(value) || /<\/?(?:section|span|h[1-3]|p|mark)(?:[\s/>]|$)/i.test(value)) found.push(value.slice(0, 160));
      }
      return found;
    });
    assert.deepEqual(artifacts, [], 'Optional children or malformed markup leaked into rendered text');
  }
  async function capture(page, filename, caption) {
    await assertNoRenderingArtifacts(page);
    assert.equal(await page.locator('.toast.error, .notice.error').count(), 0, 'Do not conceal an error in a screenshot');
    const dismiss = page.getByRole('button', { name: 'Dismiss notification', exact: true });
    while (await dismiss.count()) await dismiss.first().click();
    // A real pointer interaction removes programmatic route-focus outlines from a reading capture.
    await page.getByRole('heading', { level: 1 }).click();
    await page.evaluate(async () => { await document.fonts.ready; window.scrollTo(0, 0); });
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true, `${filename}: horizontal viewport overflow`);
    const file = path.join(captureDir, filename); await page.screenshot({ path: file, fullPage: true, animations: 'disabled', timeout: 15000 }); const bytes = await fs.readFile(file);
    evidence.screenshots.push({ file: filename, caption, capturedAt: new Date().toISOString(), viewport: page.viewportSize(), fullPage: true, imageWidth: bytes.readUInt32BE(16), imageHeight: bytes.readUInt32BE(20), pngBytes: bytes.length, sha256: sha256(bytes) });
  }
  async function deleteViaUi(page, id) {
    await go(page, `documents/${id}`); await page.getByRole('button', { name: 'Delete document', exact: true }).waitFor(); await assertNoRenderingArtifacts(page); await page.getByRole('button', { name: 'Delete document', exact: true }).click();
    const dialog = page.getByRole('dialog'); await dialog.waitFor(); await contains(page, '#confirm-message', 'dependent analyses');
    const response = page.waitForResponse(r => r.url() === `${origin}/api/v1/documents/${id}` && r.request().method() === 'DELETE'); await dialog.getByRole('button', { name: 'Delete document', exact: true }).click(); assert.equal((await response).status(), 204); await page.waitForURL('**/#/documents');
  }
}

function captureReadme(run) {
  const pct = value => (value * 100).toFixed(1) + '%';
  const lines = [
    '# Actual Java-backed browser captures',
    '',
    'Generated by `scripts/e2e/real-browser.cjs` after all checks and cleanup passed. Rerunning the script refreshes these captions, measurements, screenshots, and `metadata.json` together.',
    '',
    '**All account names and document contents are synthetic.** These are actual Chromium screenshots of the Java-served application. No API responses, scores, sources, or screenshot pixels were mocked or retouched. The report values were calculated by the real engine on this deliberately overlapping invented corpus; they are not performance, accuracy, or misconduct claims.',
    '',
    '## Captures',
    ''
  ];
  for (const capture of run.screenshots) {
    lines.push(`### ${capture.file}`, '', capture.caption, '', `Viewport: ${capture.viewport.width} × ${capture.viewport.height} CSS pixels. Full-page PNG: ${capture.imageWidth} × ${capture.imageHeight} pixels. Captured ${capture.capturedAt}.`, '', `![${capture.caption}](${capture.file})`, '');
  }
  lines.push(
    '## Measured successful run', '',
    `- Started: ${run.startedAt}; finished: ${run.finishedAt}.`,
    `- ${run.checks.length} targeted checks passed in ${(run.durationMs / 1000).toFixed(2)} seconds, including startup, captures, and cleanup. Timings describe this one local run, not a benchmark.`,
    `- ${run.apiResponses} observed API responses (${run.browserApiResponses} browser-generated; others are direct assertions using the same authenticated browser contexts).`,
    `- Chromium ${run.environment.browser}; Node ${run.environment.node}; ${run.environment.platform}/${run.environment.architecture}.`,
    `- Engine: ${run.result.engineVersion}; profile: ${run.result.profile}; selected sources: ${run.result.sourceCount}; submitted words: ${run.result.wordCount}.`,
    `- Actual evidence: ${run.result.matches} matches and ${run.result.citations} citation observations. Exact coverage ${pct(run.result.exactOverlap)}; additional near coverage ${pct(run.result.nearOverlap)}; union ${pct(run.result.totalOverlap)}.`,
    `- Observed analysis states: ${run.observedAnalysisStatuses.join(' → ')}. Intermediate states may complete between polls.`,
    `- Real authenticated PDF download: ${run.pdf.bytes} bytes; Content-Type ${run.pdf.contentType}; verified %PDF- header and %%EOF marker. SHA-256: ${run.pdf.sha256}.`,
    `- PDF re-upload reached READY: ${run.documents.pdf.wordCount} extracted words, ${run.documents.pdf.pageCount} pages. DOCX upload reached READY: ${run.documents.docx.wordCount} words; pageCount ${run.documents.docx.pageCount} (DOCX is not rendered/paginated by this extractor).`,
    '- Two accounts used separate real sessions/workspaces. Cross-workspace reads, report/PDF/review access, and document deletion returned 404 without changing the first workspace.',
    '- Reviewer decision/note was saved to the server and checked after a full page reload. UI deletion removed the submission and its dependent analysis/report/reviews; remaining documents were deleted and final document/analysis/storage counts were zero.',
    `- Browser runtime errors: ${run.browserRuntimeErrors}; observed CSP violations: ${run.cspViolations}.`,
    '- Frontend regression checks verify intact landing markup/navigation targets and no literal null/undefined or raw markup fragments in rendered pages. The verified fixes are in src/main/resources/web/index.html and app.js.',
    `- Cleanup: browser closed; Java stopped ${run.isolation.javaShutdown.forced ? 'using the bounded forced-shutdown fallback' : 'gracefully'}; temporary database, blobs, downloads, Java home/temp, and screenshot staging removed. No credentials or persistent test data are retained.`,
    '', '## Reproduce', '',
    'Use an already installed Playwright module/browser; the runner does not install dependencies:', '',
    '```sh',
    'node --test scripts/e2e/fixtures.test.cjs scripts/e2e/markup.test.cjs',
    'node scripts/e2e/real-browser.cjs /absolute/path/to/node_modules/playwright',
    '```', '',
    'See `scripts/e2e/README.md` for requirements, isolation, timeouts, and the optional jar argument. `metadata.json` records the exact jar and served-asset SHA-256 hashes, original engine fractions, per-check durations, image dimensions/hashes, and cleanup outcome. Synthetic test IDs visible in report evidence are ephemeral identifiers, not credentials.',
    '', '## Limits', '',
    'This is a targeted real-Java browser workflow, not an exhaustive end-to-end test of every route, document type, browser, authorization rule, or failure mode. In particular:', '',
    ...run.notCovered.map(item => `- ${item}`),
    '',
    'The PDF/DOCX files contain ordinary selectable text. These results do not demonstrate OCR, scanned-document support, semantic analysis, or AI-authorship detection.', ''
  );
  return lines.join('\n');
}

main().catch(error => { console.error(error.message); process.exitCode = 1; });
