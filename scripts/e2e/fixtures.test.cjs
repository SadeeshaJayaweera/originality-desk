'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { crc32, storedZip, syntheticDocx, sharedPassage, referenceText, submissionText } = require('./fixtures.cjs');

test('CRC32 agrees with the standard check vector', () => {
  assert.equal(crc32(Buffer.from('123456789')), 0xcbf43926);
  assert.equal(crc32(Buffer.alloc(0)), 0);
});

test('stored ZIP has consistent central/local offsets, lengths and checksums', () => {
  const zip = storedZip([['first.txt', 'first'], ['second.txt', 'second']]);
  const eocd = zip.length - 22;
  assert.equal(zip.readUInt32LE(eocd), 0x06054b50);
  assert.equal(zip.readUInt16LE(eocd + 10), 2);
  let cursor = zip.readUInt32LE(eocd + 16);
  assert.equal(cursor + zip.readUInt32LE(eocd + 12), eocd);
  for (const name of ['first.txt', 'second.txt']) {
    assert.equal(zip.readUInt32LE(cursor), 0x02014b50);
    const nameLength = zip.readUInt16LE(cursor + 28), offset = zip.readUInt32LE(cursor + 42);
    assert.equal(zip.subarray(cursor + 46, cursor + 46 + nameLength).toString(), name);
    assert.equal(zip.readUInt32LE(offset), 0x04034b50);
    const size = zip.readUInt32LE(offset + 18), checksum = zip.readUInt32LE(offset + 14);
    assert.equal(checksum, crc32(zip.subarray(offset + 30 + nameLength, offset + 30 + nameLength + size)));
    assert.equal(checksum, zip.readUInt32LE(cursor + 16)); cursor += 46 + nameLength;
  }
  assert.equal(cursor, eocd);
});

test('DOCX fixture is deterministic and contains its required package parts', () => {
  const first = syntheticDocx();
  assert.equal(first.equals(syntheticDocx()), true);
  for (const part of ['[Content_Types].xml', '_rels/.rels', 'word/document.xml', 'SYNTHETIC DOCX']) assert.equal(first.includes(Buffer.from(part)), true);
});

test('both synthetic TXT fixtures contain the intentional shared passage', () => {
  assert.equal(referenceText.includes(sharedPassage), true);
  assert.equal(submissionText.includes(sharedPassage), true);
  assert.match(referenceText, /^SYNTHETIC REFERENCE/);
  assert.match(submissionText, /^SYNTHETIC SUBMISSION/);
});
