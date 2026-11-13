/* Optional browser verification. No runtime dependency is added to the application.
 * node src/main/resources/web/browser-smoke.cjs /absolute/path/to/playwright
 * Starts and stops its own local fixture server. All records are test-only synthetic data.
 */
'use strict';
const assert = require('node:assert/strict');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(process.argv[2] || 'playwright');
const timestamp = '2026-01-10T12:00:00Z';
const user = { id: 'user-1', name: 'Test Reviewer', email: 'reviewer@example.test', role: 'INSTITUTION_ADMIN' };
const workspace = { id: 'workspace-1', name: 'Synthetic test workspace' };
const hostile = '<img src=x onerror="window.injected=true">';
const documentText = `Original ${hostile} text. This is a synthetic passage for a browser test. More original text.`;
const passage = 'This is a synthetic passage for a browser test.';
const start = documentText.indexOf(passage);
let authenticated = false;
const docs = [
  { id: 'submission-1', title: 'Synthetic submission.txt', filename: 'Synthetic submission.txt', kind: 'SUBMISSION', status: 'READY', wordCount: 20, pageCount: 1, bytes: 100, createdAt: timestamp },
  { id: 'reference-1', title: 'Synthetic reference.txt', filename: 'Synthetic reference.txt', kind: 'REFERENCE', status: 'READY', wordCount: 30, pageCount: 1, bytes: 160, createdAt: timestamp }
];
const analyses = [];
const reviews = Array.from({ length: 123 }, (_, i) => ({ id: `review-${i}`, matchIndex: 0, decision: 'NEEDS_REVIEW', note: `Synthetic saved note ${i}`, createdAt: timestamp }));
const requests = [];
let analysisPayload;
let uploadAttempt = 0;
const envelope = (items, url) => { const page = Number(url.searchParams.get('page') || 1), size = Number(url.searchParams.get('size') || 20); return { items: items.slice((page - 1) * size, page * size), total: items.length, page, size }; };
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  const chunks = []; for await (const chunk of req) chunks.push(chunk); const raw = Buffer.concat(chunks);
  let body; try { body = JSON.parse(raw); } catch (_) { body = null; }
  requests.push({ method: req.method, path: url.pathname, search: url.search, body, rawBytes: raw.length, contentType: req.headers['content-type'] });
  res.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; form-action 'self'");
  const send = (data, status = 200) => { res.writeHead(status, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(data)); };
  if (!url.pathname.startsWith('/api/v1/')) {
    const file = { '/': 'index.html', '/index.html': 'index.html', '/app.js': 'app.js', '/styles.css': 'styles.css' }[url.pathname];
    if (!file) { res.writeHead(404); res.end(); return; }
    res.setHeader('Content-Type', file.endsWith('.js') ? 'text/javascript' : file.endsWith('.css') ? 'text/css' : 'text/html'); res.end(fs.readFileSync(path.join(__dirname, file))); return;
  }
  const route = url.pathname.slice('/api/v1'.length);
  if (route === '/auth/register' || route === '/auth/login') { authenticated = true; send({ user, workspace }); return; }
  if (!authenticated) { send({ error: { code: 'UNAUTHORIZED', message: 'Sign in required' } }, 401); return; }
  if (route === '/auth/me') { send({ user, workspace }); return; }
  if (route === '/auth/logout') { authenticated = false; res.writeHead(204); res.end(); return; }
  if (route === '/dashboard') { send({ totalDocuments: docs.length, completedAnalyses: analyses.filter(a => a.status === 'COMPLETED').length, pendingAnalyses: analyses.filter(a => a.status !== 'COMPLETED').length, failedAnalyses: 0, storageBytes: 260, recentDocuments: docs, recentAnalyses: analyses, activity: [{ action: 'REGISTER', createdAt: timestamp }] }); return; }
  if (route === '/documents' && req.method === 'POST') {
    uploadAttempt += 1;
    const filename = url.searchParams.get('filename');
    if (filename === 'reject.txt') { send({ error: { code: 'BAD_DOCUMENT', message: 'Synthetic server validation error' } }, 422); return; }
    const doc = { id: `upload-${uploadAttempt}`, title: filename, filename, kind: url.searchParams.get('kind'), status: 'EXTRACTING', wordCount: null, pageCount: null, bytes: raw.length, createdAt: timestamp };
    docs.push(doc);
    if (filename === 'abort.txt') await new Promise(resolve => setTimeout(resolve, 700));
    send(doc, 201); return;
  }
  if (route === '/documents') { const kind = url.searchParams.get('kind'); docs.forEach(doc => { if (doc.status === 'EXTRACTING') { doc.status = 'READY'; doc.wordCount = 12; doc.pageCount = 1; } }); send(envelope(docs.filter(doc => !kind || doc.kind === kind), url)); return; }
  if (route.startsWith('/documents/')) { const id = route.split('/')[2], index = docs.findIndex(doc => doc.id === id); if (req.method === 'DELETE') { docs.splice(index, 1); res.writeHead(204); res.end(); return; } send(docs[index]); return; }
  if (route === '/analyses' && req.method === 'POST') { analysisPayload = body; const analysis = { id: `analysis-${analyses.length + 1}`, documentId: body.documentId, title: 'Synthetic analysis', profile: body.profile, sourceCount: body.sourceIds.length, createdAt: timestamp, status: 'QUEUED', polls: 0 }; analyses.push(analysis); send(analysis, 202); return; }
  if (route === '/analyses') { send(envelope(analyses, url)); return; }
  if (/\/analyses\/[^/]+\/reviews$/.test(route)) { if (req.method === 'POST') { const review = { id: `review-${reviews.length}`, ...body, createdAt: timestamp }; reviews.push(review); send(review, 201); } else send(envelope(reviews, url)); return; }
  if (/\/analyses\/[^/]+\/cancel$/.test(route)) { const analysis = analyses.find(a => a.id === route.split('/')[2]); analysis.status = 'CANCELLED'; send(analysis); return; }
  if (/\/analyses\/[^/]+\/status$/.test(route)) { const analysis = analyses.find(a => a.id === route.split('/')[2]); if (++analysis.polls > 1 && analysis.status !== 'CANCELLED') { analysis.status = 'COMPLETED'; analysis.completedAt = timestamp; } send(analysis); return; }
  if (route.startsWith('/reports/')) {
    const format = route.split('/')[3];
    const report = { id: 'analysis-1', title: `Synthetic report ${hostile}`, documentText, engineVersion: 'test-fixture', profile: 'STANDARD', wordCount: 20, exactOverlap: 0.25, nearOverlap: 0.1, totalOverlap: 0.35, matches: [{ sourceId: 'reference-1', sourceTitle: `Synthetic source ${hostile}`, sourceType: 'REFERENCE', submittedPassage: passage, sourcePassage: `Source excerpt: ${passage}`, method: 'EXACT', value: 1, start, end: start + passage.length, sourceStart: 16, sourceEnd: 16 + passage.length, page: 1, citationObservation: 'Synthetic uncertain citation observation.' }], citations: [{ text: 'Synthetic citation', kind: 'AUTHOR_YEAR', start: 0, end: 5, uncertain: true }], limitations: ['Synthetic test fixture, not a scholarly finding.'], semanticStatus: 'NOT_IMPLEMENTED', aiWritingStatus: 'NOT_IMPLEMENTED' };
    if (!format) { send(report); return; }
    res.setHeader('Content-Disposition', `attachment; filename="synthetic-report.${format}"`); res.setHeader('Content-Type', { json: 'application/json', html: 'text/html', csv: 'text/csv', pdf: 'application/pdf' }[format]); res.end(format === 'json' ? JSON.stringify(report) : 'Synthetic export endpoint fixture'); return;
  }
  if (route === '/admin/health') { send({ status: 'UP', workerAlive: true, queuedJobs: 0, runningJobs: 0, failedJobs: 0 }); return; }
  if (route === '/admin/jobs' || route === '/admin/audit-logs') { send(envelope([], url)); return; }
  send({ error: { code: 'NOT_FOUND', message: 'Unknown test endpoint' } }, 404);
});
(async () => {
  let browser;
  try {
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const origin = `http://127.0.0.1:${server.address().port}`;
    browser = await chromium.launch({ headless: true });
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, reducedMotion: 'reduce' });
    const errors = [], csp = [];
    page.on('pageerror', error => errors.push(error.message));
    page.on('console', message => { if (message.text().includes('Content Security Policy')) csp.push(message.text()); });
    page.setDefaultTimeout(10000);
    async function go(hash) { await page.goto(`${origin}/#/${hash}`); await page.locator('main h1').first().waitFor(); }
    async function click(text) { await page.getByRole('button', { name: text, exact: true }).click(); }
    async function contains(selector, value) { await page.waitForFunction(({ selector, value }) => document.querySelector(selector)?.textContent.includes(value), { selector, value }); }
    await go(''); assert.match(await page.locator('h1').textContent(), /clear evidence/);
    await go('sample'); await contains('main', 'STATIC SYNTHETIC EXAMPLE'); await page.locator('.passage-mark').press('Enter'); await contains('.match-inspector', 'cannot be saved');
    await go('register'); await page.getByLabel('Full name', { exact: true }).fill(user.name); await page.getByLabel('Workspace name', { exact: true }).fill(workspace.name); await page.getByLabel('Email address', { exact: true }).fill(user.email); await page.getByLabel('Password', { exact: true }).fill('synthetic-password-123'); await click('Create account →'); await page.waitForURL('**/#/overview'); await contains('main', 'Documents');
    assert.equal(requests.find(r => r.path.endsWith('/auth/register')).contentType, 'application/json');
    await go('documents');
    const file = page.locator('input[type="file"]');
    await file.setInputFiles({ name: 'bad.exe', mimeType: 'application/octet-stream', buffer: Buffer.from('test') }); await contains('.upload-zone', 'Only PDF');
    await file.setInputFiles({ name: 'big.txt', mimeType: 'text/plain', buffer: Buffer.alloc(10 * 1024 * 1024 + 1, 65) }); await contains('.upload-zone', 'exceeds the 10 MiB');
    const beforeUpload = uploadAttempt;
    await file.setInputFiles({ name: 'reject.txt', mimeType: 'text/plain', buffer: Buffer.from('test') }); await click('Upload document ↑'); await contains('.upload-zone', 'Synthetic server validation error'); assert.equal(uploadAttempt, beforeUpload + 1);
    await file.setInputFiles({ name: 'abort.txt', mimeType: 'text/plain', buffer: Buffer.from('Synthetic upload text') }); await click('Upload document ↑'); await click('Abort upload'); await contains('.upload-zone', 'Upload aborted'); await click('Retry upload'); await contains('.upload-zone', 'File accepted'); assert.equal(uploadAttempt, beforeUpload + 3);
    assert.equal(requests.filter(r => r.path === '/api/v1/documents' && r.method === 'POST').at(-1).contentType, 'application/octet-stream');
    await file.setInputFiles({ name: 'abort.txt', mimeType: 'text/plain', buffer: Buffer.from('Background upload') }); await click('Upload document ↑'); await page.locator('.app-nav').getByRole('link', { name: 'Settings', exact: true }).click(); await page.getByRole('button', { name: 'Abort upload of abort.txt' }).click();
    await page.getByLabel('Dark', { exact: true }).check(); assert.equal(await page.evaluate(() => localStorage.getItem('originality-desk-theme')), 'dark');
    await go('compare'); await page.locator('input[type="radio"][name="submission"][value="submission-1"]').check(); const source = page.locator('input[type="checkbox"][value="reference-1"]'); await source.check(); assert.equal(await source.evaluate(node => document.activeElement === node), true); await page.locator('input[name="profile"][value="REFERENCE_AWARE"]').check(); await click('Run document comparison →'); await page.waitForURL('**/#/analysis/analysis-1'); await page.getByRole('link', { name: 'Open evidence report →' }).waitFor();
    assert.deepEqual(analysisPayload, { documentId: 'submission-1', sourceIds: ['reference-1'], profile: 'REFERENCE_AWARE' });
    await page.getByRole('link', { name: 'Open evidence report →' }).click(); await contains('.report-summary', '35%'); assert.equal(await page.locator('.document-text').textContent(), documentText); assert.equal(await page.locator('main img').count(), 0); assert.equal(await page.evaluate(() => window.injected), undefined);
    await page.locator('.match-card').click(); await contains('.match-inspector', 'Synthetic saved note 122'); await page.getByLabel('Reviewer decision', { exact: true }).selectOption('ACCEPTED'); await page.getByLabel('Reviewer note', { exact: true }).fill('Saved from the browser smoke test.'); await click('Save reviewer decision'); await contains('.review-history', 'Saved from the browser smoke test.'); assert.equal(reviews.at(-1).decision, 'ACCEPTED');
    for (const format of ['PDF', 'JSON', 'HTML', 'CSV']) { const pending = page.waitForEvent('download'); await click(`${format} ↓`); const download = await pending; assert.equal(download.suggestedFilename(), `synthetic-report.${format.toLowerCase()}`); assert.equal(await download.failure(), null); }
    await go('history'); await page.getByLabel('Status on this page', { exact: true }).selectOption('completed'); await contains('tbody', 'Synthetic analysis'); await page.getByLabel('Search this page', { exact: true }).fill('no such analysis'); await contains('main', 'No analyses to show');
    await go('workspace'); await contains('main', user.email); await contains('main', workspace.name);
    await go('admin'); await contains('main', 'Worker alive'); await click('Jobs'); await contains('main', 'No records returned'); await click('Audit logs'); await contains('main', 'No records returned');
    await go('documents'); await page.locator('tbody tr').first().getByRole('button', { name: 'Delete', exact: true }).click(); await page.getByRole('dialog').waitFor(); await click('Keep document'); assert.equal(await page.getByRole('dialog').count(), 0);
    for (const width of [320, 375, 768, 1440]) {
      await page.setViewportSize({ width, height: 900 }); await go('overview');
      const overflow = await page.evaluate(() => ({ fits: document.documentElement.scrollWidth <= innerWidth + 1, elements: [...document.querySelectorAll('main, main>*, .two-column, .stack, .panel, .app-topbar')].filter(node => node.getBoundingClientRect().right > innerWidth + 1).map(node => `${node.tagName}.${node.className}: ${Math.round(node.getBoundingClientRect().width)}px`) }));
      assert.equal(overflow.fits, true, `dashboard overflow at ${width}: ${overflow.elements.join(', ')}`);
      if (width < 701) { await click('Open navigation'); await page.locator('.sidebar .mobile-close').press('Escape'); assert.equal(await page.getByRole('button', { name: 'Open navigation' }).getAttribute('aria-expanded'), 'false'); }
      await go('sample'); await contains('main', 'STATIC SYNTHETIC EXAMPLE'); assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true, `report overflow at ${width}`);
    }
    await page.setViewportSize({ width: 1440, height: 1000 }); await go('overview'); await page.getByRole('button', { name: 'Sign out', exact: true }).click(); await page.waitForURL(`${origin}/#/`); await go('login'); await page.getByLabel('Email address', { exact: true }).fill(user.email); await page.getByLabel('Password', { exact: true }).fill('synthetic-password-123'); await click('Sign in →'); await page.waitForURL('**/#/overview');
    assert.deepEqual(errors, []); assert.deepEqual(csp, []);
    console.log('Browser smoke passed: auth, validation, upload abort/retry/navigation, source IDs/profile, polling, injection safety, persisted reviews, four downloads, filters, admin, theme, and responsive navigation.');
    console.log(`${requests.length} fixture HTTP requests; no Java backend was used by this fixture test.`);
  } finally { if (browser) await browser.close(); server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
})().catch(error => { console.error(error); process.exitCode = 1; });
