'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

// Deliberately strict sanity check for this explicitly closed static document, not a general HTML validator.
function balancedTags(html) {
  const stack = [], voids = new Set(['area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input', 'link', 'meta', 'param', 'source', 'track', 'wbr']);
  let cursor = 0;
  while ((cursor = html.indexOf('<', cursor)) !== -1) {
    if (html.startsWith('<!--', cursor)) { const end = html.indexOf('-->', cursor + 4); assert.notEqual(end, -1, 'Unterminated comment'); cursor = end + 3; continue; }
    let end = cursor + 1, quote = '';
    for (; end < html.length; end += 1) {
      const char = html[end];
      assert.notEqual(char, '<', `Nested markup token or tag injected into an attribute near offset ${end}`);
      if (quote) { if (char === quote) quote = ''; }
      else if (char === '"' || char === "'") quote = char;
      else if (char === '>') break;
    }
    assert.ok(end < html.length && !quote, 'Unterminated tag or attribute');
    const token = html.slice(cursor, end + 1);
    if (!/^<!doctype html>$/i.test(token)) {
      const match = token.match(/^<(\/)?([a-z][a-z0-9-]*)(?:\s[^<>]*)?\s*\/?>$/i);
      assert.ok(match, `Malformed tag: ${token}`);
      const [, close, rawName] = match, name = rawName.toLowerCase();
      if (close) { assert.equal(voids.has(name), false, `Void closing tag: ${token}`); assert.equal(stack.pop(), name, `Mismatched closing tag: ${token}`); }
      else if (!voids.has(name)) stack.push(name);
    }
    cursor = end + 1;
  }
  assert.deepEqual(stack, [], 'Unclosed tags remain');
}

const html = fs.readFileSync(path.resolve(__dirname, '../../src/main/resources/web/index.html'), 'utf8');
test('landing HTML has strictly balanced tags and no tag tokens in attributes', () => balancedTags(html));
test('landing navigation targets and the color-theme button retain their real markup', () => {
  for (const id of ['capabilities', 'process', 'privacy']) assert.equal((html.match(new RegExp(`<section id="${id}"`, 'g')) || []).length, 1);
  assert.match(html, /class="icon-button theme-toggle" aria-label="Change color theme">◐<\/button>/);
  assert.match(html, /<mark>Careful attribution[^<]+<\/mark>/);
});
test('markup guard catches malformed endings and injected tag fragments', () => {
  for (const value of ['<p>text</h2>', '<button class="</head>icon">x</button>', '<section>text</</title>section>']) assert.throws(() => balancedTags(value));
});
