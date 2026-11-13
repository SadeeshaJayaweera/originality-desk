/**
 * Bounded local extraction and corpus evidence. Integration calls are synchronous and stateless;
 * the backend must call them on its background worker, not on a UI/request thread.
 *
 * <h2>Extraction policy</h2>
 * Inputs are at most 10 MiB and extracted strings at most 200000 UTF-16 code units. Limits reject
 * the document rather than returning a silently truncated success. Extensions must agree with
 * format signatures. TXT requires strict UTF-8 and rejects NUL/binary controls. A UTF-8 BOM is
 * removed. PDF uses PDFBox 3 Loader, rejects encryption, caps glyph/output text, pages (2000),
 * indirect objects (20000), and checks expanded streams (8 MiB each, 32 MiB total, ratio 200).
 * Page boundaries are form feeds. A PDF without extractable alphanumeric text reports that OCR
 * is unavailable. These checks do not make a third-party PDF parser a hard CPU/heap sandbox;
 * deployments handling untrusted uploads should additionally isolate/time-limit their worker.
 *
 * <p>DOCX requires the OOXML Word main content type, package relationship and document part, not
 * just a renamed ZIP. ZIP limits are 512 entries, 4 MiB per expanded entry, 20 MiB aggregate and
 * ratio 100 per entry. Both directory metadata and actual inflation are checked. Traversal,
 * duplicate/inconsistent entries, encryption, ZIP64, macros, active/embedded packages, DTDs and
 * external relationships are rejected. This deliberately also rejects ordinary external
 * hyperlinks. XML has depth/element/attribute budgets and cannot load external resources.
 * Extraction includes main-body paragraphs, tables, tabs and line breaks, not headers, footnotes,
 * drawing text, layout or OCR. DOCX pageCount is 0 (unknown), TXT 1, PDF the actual page count.
 *
 * <h2>Evidence methodology</h2>
 * Only supplied source text is searched; no network search occurs. Original Java UTF-16,
 * half-open coordinates are retained through NFKC/case/whitespace normalization, ligature
 * expansion and removal of invisible formatting/soft hyphens. Public TextNormalizer and
 * Fingerprints utilities expose normalization, coordinate mapping and collision-free word
 * shingles. English-oriented low-information/boilerplate filtering is conservative and can
 * suppress legitimate repeated material.
 *
 * <p>QUICK uses exact 12-word shingles only. STANDARD uses exact 8-word shingles and lexical near
 * windows. REFERENCE_AWARE uses exact 6-word shingles and lexical near windows, excluding detected
 * trailing reference lists from both matching and the covered-word denominator; wordCount still
 * describes the entire submission. Reference headings are uncertain English-language heuristics.
 * Near evidence uses sentence/paragraph content-word set Jaccard at least 0.62, at least 5 shared
 * terms and comparable term-set lengths. Inverted indexes and deterministic candidate/work caps
 * bound retrieval; result limitations describe the caps and any actual truncation.
 *
 * <p>Coverage values are fractions, not percentages: exact is the union of words in exact evidence,
 * near is only additional words in near windows, total is their union. Overlapping sources never
 * add coverage twice. Near-window coverage is not a claim that every word was copied. Source ids
 * must be unique; the submitted documentId is excluded from sources to prevent self-matching.
 * Evidence is capped at 200 matches and 2000 original characters per passage. Pages are inferred
 * only from form feeds. Citation/quote/reference patterns are uncertain observations, never
 * bibliographic validation; quoted or apparently cited prose is not automatically exempted.
 * Semantic analysis, OCR and AI authorship inference are not implemented.
 */
package com.writingassistant.analysis;
