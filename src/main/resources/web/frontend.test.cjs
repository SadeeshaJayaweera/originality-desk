/* Run with: node --test src/main/resources/web/frontend.test.cjs (no dependencies). */
'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, 'app.js'), 'utf8');
const context = vm.createContext({ Intl });
vm.runInContext(source.split('\n(function () {')[0], context);
const C = context.OriginalityCore;
const plain = value => JSON.parse(JSON.stringify(value));

test('upload validation accepts supported extensions up to exactly 10 MiB', () => {
  for (const name of ['paper.pdf', 'paper.DOCX', 'paper.txt']) assert.equal(C.validateFile({ name, size: C.MAX_BYTES }), '');
  assert.match(C.validateFile({ name: 'paper.pdf', size: C.MAX_BYTES + 1 }), /10 MiB/);
  assert.match(C.validateFile({ name: 'paper.doc', size: 4 }), /Only PDF/);
  assert.match(C.validateFile({ name: 'paper.txt', size: 0 }), /empty/);
  assert.match(C.validateFile(null), /Choose/);
});
test('status helpers distinguish extraction, analysis, cancellation, and failures', () => {
  assert.equal(C.isReady('EXTRACTING'), false); assert.equal(C.isReady('READY'), true);
  assert.equal(C.isTerminal('RUNNING'), false); assert.equal(C.isComplete('COMPLETED'), true);
  assert.equal(C.isTerminal('CANCELLED'), true); assert.equal(C.isFailed('FAILED'), true);
  assert.equal(C.isTerminal('UNKNOWN'), false);
});
test('overlapping matches preserve every original character exactly once', () => {
  const original = 'A😀 <script>alert(1)</script> scholarly text.';
  const parts = C.segments(original, [{ start: 0, end: 8 }, { start: 5, end: 16 }, { start: 5, end: 16 }]);
  assert.equal(parts.map(part => part.text).join(''), original);
  assert.deepEqual(plain(parts.find(part => part.start === 5).matches), [0, 1, 2]);
  assert.equal(parts.reduce((total, part) => total + part.text.length, 0), original.length);
});
test('invalid, noninteger, inverted, and out-of-bounds offsets never corrupt text', () => {
  const original = 'Original text';
  const parts = C.segments(original, [{ start: -1, end: 3 }, { start: 1.5, end: 5 }, { start: 7, end: 3 }, { start: 0, end: 99 }, { start: 0, end: 0 }]);
  assert.deepEqual(plain(parts), [{ text: original, start: 0, end: original.length, matches: [] }]);
  assert.deepEqual(plain(C.segments('', [])), []);
});
test('engine fractions are percentages and absent values are never invented zeros', () => {
  assert.equal(C.percent(0.125), '12.5%'); assert.equal(C.percent(0), '0%');
  assert.equal(C.percent(null), 'Not reported'); assert.equal(C.number(undefined), 'Not reported');
  assert.equal(C.number(0), '0'); assert.equal(C.formatBytes(C.MAX_BYTES), '10.0 MiB');
});
test('flat server reports and nested contract reports are both supported', () => {
  const flat = { documentText: 'text', matches: [], totalOverlap: 0 };
  assert.equal(C.reportResult(flat), flat); assert.equal(C.reportResult({ result: flat }), flat);
  assert.equal(C.reportResult({}), null);
});
test('admin and reviewer role checks use actual backend roles', () => {
  assert.equal(C.isAdmin('INSTITUTION_ADMIN'), true); assert.equal(C.isAdmin('REVIEWER'), false);
  assert.equal(C.canReview('REVIEWER'), true); assert.equal(C.canReview('STUDENT'), false);
});
test('collection envelopes and download filenames are handled safely', () => {
  assert.deepEqual(C.collection({ items: [1, 2] }), [1, 2]);
  assert.deepEqual(plain(C.collection(null)), []);
  assert.equal(C.safeFilename('../report\n.csv', 'report.csv'), '.._report_.csv');
});
test('assets comply with the three-file static allowlist and strict self CSP', () => {
  const html = fs.readFileSync(path.join(__dirname, 'index.html'), 'utf8');
  const css = fs.readFileSync(path.join(__dirname, 'styles.css'), 'utf8');
  assert.doesNotMatch(html, /\son\w+\s*=|\sstyle\s*=|<script[^>]*>(?!\s*<\/script>)/i);
  assert.doesNotMatch(source, /\.innerHTML\s*=|insertAdjacentHTML|\beval\s*\(|new Function\b/);
  assert.doesNotMatch(html + css, /https?:\/\/|@import|url\(/i);
  assert.deepEqual([...html.matchAll(/<script src="([^"]+)"/g)].map(match => match[1]), ['./app.js']);
  assert.match(html, /STATIC|Synthetic example/); assert.match(html, /id="main"/);
});
