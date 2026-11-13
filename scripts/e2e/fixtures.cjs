'use strict';

const sharedPassage = 'Careful attribution makes the relationship between an argument and its sources visible, allowing readers to distinguish inherited evidence from a writer\'s independent interpretation.';
const referenceText = [
  'SYNTHETIC REFERENCE — created solely for browser verification.',
  'Invented research notes: reading with context',
  sharedPassage,
  'A source record is useful when its author, context, and limitations can be examined alongside the passage being reviewed. These invented notes are not a real publication or an academic authority.',
  'This file exists only in an isolated, temporary software-test workspace.'
].join('\n\n');
const submissionText = [
  'SYNTHETIC SUBMISSION — created solely for browser verification.',
  'Reading between the references',
  'This short invented essay demonstrates evidence review. It is not an assessment of a real student or publication.',
  sharedPassage,
  'An invented second paragraph observes that notes about context and attribution should accompany a comparison. Shared wording alone cannot settle intent. A reviewer must examine how a passage functions in its surrounding argument.',
  'References\nSynthetic Research Notes (2026). Invented material for this isolated software test.'
].join('\n\n');
const reviewerNote = 'Synthetic test review: the shared wording is intentional in this invented corpus. Attribution must be assessed by a person; this is not a misconduct finding.';

function crc32(bytes) {
  let crc = 0xffffffff;
  for (const byte of bytes) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit += 1) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

// A tiny stored ZIP writer is sufficient for a standards-shaped, text-only DOCX fixture.
// It avoids a build step, Java fixture compilation, or an unannounced npm dependency.
function storedZip(entries) {
  const local = [], central = []; let offset = 0, centralSize = 0;
  for (const [filename, contents] of entries) {
    const name = Buffer.from(filename, 'utf8'), data = Buffer.from(contents, 'utf8');
    const checksum = crc32(data);
    const header = Buffer.alloc(30);
    header.writeUInt32LE(0x04034b50, 0); header.writeUInt16LE(20, 4);
    header.writeUInt16LE(0x0800, 6); header.writeUInt16LE(0, 8);
    header.writeUInt16LE(0x0021, 12); // 1980-01-01, a deterministic valid DOS date.
    header.writeUInt32LE(checksum, 14); header.writeUInt32LE(data.length, 18);
    header.writeUInt32LE(data.length, 22); header.writeUInt16LE(name.length, 26);
    local.push(header, name, data);
    const directory = Buffer.alloc(46);
    directory.writeUInt32LE(0x02014b50, 0); directory.writeUInt16LE(20, 4); directory.writeUInt16LE(20, 6);
    directory.writeUInt16LE(0x0800, 8); directory.writeUInt16LE(0, 10); directory.writeUInt16LE(0x0021, 14);
    directory.writeUInt32LE(checksum, 16); directory.writeUInt32LE(data.length, 20);
    directory.writeUInt32LE(data.length, 24); directory.writeUInt16LE(name.length, 28);
    directory.writeUInt32LE(offset, 42); central.push(directory, name);
    offset += header.length + name.length + data.length; centralSize += directory.length + name.length;
  }
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(entries.length, 8); end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(centralSize, 12); end.writeUInt32LE(offset, 16);
  return Buffer.concat([...local, ...central, end]);
}

function syntheticDocx() {
  const paragraphs = [
    'SYNTHETIC DOCX — created solely for a real API extraction check.',
    'This invented document contains ordinary selectable text, not a scan or an image.',
    'Its temporary test workspace will be deleted after the browser workflow finishes.'
  ];
  const escape = value => value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  return storedZip([
    ['[Content_Types].xml', '<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>'],
    ['_rels/.rels', '<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>'],
    ['word/document.xml', '<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>' + paragraphs.map(value => `<w:p><w:r><w:t>${escape(value)}</w:t></w:r></w:p>`).join('') + '<w:sectPr/></w:body></w:document>']
  ]);
}

module.exports = { sharedPassage, referenceText, submissionText, reviewerNote, crc32, storedZip, syntheticDocx };
