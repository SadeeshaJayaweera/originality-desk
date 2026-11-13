package com.writingassistant.analysis;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentExtractorTest {
    private final DocumentExtractor extractor = new DocumentExtractor();
    private static final String TYPES = "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>";
    private static final String RELS = "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>";

    @Test
    void strictUtf8PreservesTextAndRecognizesCaseInsensitiveExtension() throws Exception {
        String text = "\uFEFFRésumé 😀\n\tﬂower\r\n";
        var extracted = extractor.extract("notes.TXT", text.getBytes(StandardCharsets.UTF_8));
        assertEquals(text.substring(1), extracted.text());
        assertEquals("TXT", extracted.format());
        assertEquals(1, extracted.pageCount());
    }

    @Test
    void rejectsInvalidUtf8NulAndBinaryControls() {
        for (byte[] bytes : new byte[][]{{(byte) 0xc3, 0x28}, {'a', 0, 'b'}, {'a', 1, 'b'}, {(byte) 0xff}}) {
            assertThrows(IOException.class, () -> extractor.extract("x.txt", bytes));
        }
    }

    @Test
    void enforcesByteAndCharacterLimitsWithoutSilentTruncation() throws Exception {
        String boundary = "é".repeat(200_000);
        assertEquals(boundary, extractor.extract("ok.txt", boundary.getBytes(StandardCharsets.UTF_8)).text());
        assertThrows(IOException.class, () -> extractor.extract("large.txt", "x".repeat(200_001).getBytes(StandardCharsets.UTF_8)));
        assertThrows(IOException.class, () -> extractor.extract("large.txt", new byte[DocumentExtractor.MAX_BYTES + 1]));
        assertThrows(IOException.class, () -> extractor.extract("empty.txt", new byte[0]));
        assertThrows(IOException.class, () -> extractor.extract(null, new byte[]{1}));
    }

    @Test
    void extensionsAndMagicMustAgree() throws Exception {
        byte[] pdf = pdf("Visible document words");
        byte[] docx = zip(parts("<w:p><w:r><w:t>Hello</w:t></w:r></w:p>"));
        assertThrows(IOException.class, () -> extractor.extract("renamed.txt", pdf));
        assertThrows(IOException.class, () -> extractor.extract("renamed.docx", pdf));
        assertThrows(IOException.class, () -> extractor.extract("renamed.pdf", docx));
        assertThrows(IOException.class, () -> extractor.extract("renamed.txt", docx));
        assertThrows(IOException.class, () -> extractor.extract("old.doc", docx));
        assertThrows(IOException.class, () -> extractor.extract("fake.pdf", "not pdf".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void extractsSyntheticPdfUsingPdfBox3AndPreservesPageSeparators() throws Exception {
        var document = extractor.extract("paper.pdf", pdf("First page research evidence", "Second page scientific results"));
        assertEquals("PDF", document.format());
        assertEquals(2, document.pageCount());
        assertTrue(document.text().contains("First page research evidence"));
        assertTrue(document.text().contains("\fSecond page scientific results"));
        assertEquals(1, document.text().chars().filter(c -> c == '\f').count());
    }

    @Test
    void rejectsEncryptedPdfEvenWithAnEmptyUserPassword() throws Exception {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.protect(new StandardProtectionPolicy("owner-password", "", new AccessPermission()));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            IOException error = assertThrows(IOException.class, () -> extractor.extract("encrypted.pdf", output.toByteArray()));
            assertTrue(error.getMessage().contains("Encrypted"));
        }
    }

    @Test
    void emptyPdfClearlyReportsOcrIsUnavailable() throws Exception {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            IOException error = assertThrows(IOException.class, () -> extractor.extract("scan.pdf", output.toByteArray()));
            assertTrue(error.getMessage().contains("OCR is unavailable"));
        }
    }

    @Test
    void rejectsMalformedAndOverBudgetPdf() throws Exception {
        assertThrows(IOException.class, () -> extractor.extract("bad.pdf", "%PDF-1.7\ninvalid".getBytes(StandardCharsets.US_ASCII)));
        byte[] excessive = pdf("bounded ".repeat(26_000));
        assertThrows(IOException.class, () -> extractor.extract("excessive.pdf", excessive));
    }

    @Test
    void boundsPdfPageCountAndUnusedExpandedStreams() throws Exception {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < 2_001; i++) document.addPage(new PDPage());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            IOException error = assertThrows(IOException.class, () -> extractor.extract("pages.pdf", output.toByteArray()));
            assertTrue(error.getMessage().contains("page limit"));
        }
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            COSStream extra = document.getDocument().createCOSStream();
            try (var stream = extra.createOutputStream()) { stream.write(new byte[8 * 1024 * 1024 + 1]); }
            document.getDocumentCatalog().getCOSObject().setItem(COSName.getPDFName("UnusedStream"), extra);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            assertTrue(output.size() < DocumentExtractor.MAX_BYTES);
            IOException error = assertThrows(IOException.class, () -> extractor.extract("streams.pdf", output.toByteArray()));
            assertTrue(error.getMessage().contains("stream size"));
        }
    }

    @Test
    void extractsRealOoxmlParagraphsTabsBreaksAndTableCells() throws Exception {
        String body = "<w:p><w:r><w:t xml:space=\"preserve\">Hello ﬃ 😀</w:t><w:tab/><w:t>world</w:t><w:br/><w:t>next</w:t></w:r></w:p>"
                + "<w:tbl><w:tr><w:tc><w:p><w:r><w:t>cell</w:t></w:r></w:p></w:tc></w:tr></w:tbl>";
        var document = extractor.extract("paper.docx", zip(parts(body)));
        assertEquals("Hello ﬃ 😀\tworld\nnext\ncell\n\t", document.text());
        assertEquals("DOCX", document.format());
        assertEquals(0, document.pageCount());
    }

    @Test
    void rejectsArbitraryZipAndWrongMainContentType() throws Exception {
        assertThrows(IOException.class, () -> extractor.extract("fake.docx", zip(Map.of("hello.txt", new byte[]{1}))));
        var parts = parts("<w:p/>");
        parts.put("[Content_Types].xml", TYPES.replace("document.main+xml", "template.main+xml").getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> extractor.extract("template.docx", zip(parts)));
        parts.remove("_rels/.rels");
        assertThrows(IOException.class, () -> extractor.extract("missing.docx", zip(parts)));
    }

    @Test
    void validatesDocumentBodyAndSupportsStrictOoxmlNamespaces() throws Exception {
        var missingBody = parts("<w:p/>");
        String invalid = new String(missingBody.get("word/document.xml"), StandardCharsets.UTF_8).replace("w:body", "w:notBody");
        missingBody.put("word/document.xml", invalid.getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> extractor.extract("missing-body.docx", zip(missingBody)));
        assertThrows(IOException.class, () -> extractor.extract("nested-body.docx", zip(parts("<w:body/>"))));
        var strict = parts("<w:p><w:r><w:t>Strict text</w:t></w:r></w:p>");
        strict.put("word/document.xml", new String(strict.get("word/document.xml"), StandardCharsets.UTF_8)
                .replace("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "http://purl.oclc.org/ooxml/wordprocessingml/main").getBytes(StandardCharsets.UTF_8));
        strict.put("_rels/.rels", RELS.replace("http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument",
                "http://purl.oclc.org/ooxml/officeDocument/relationships/officeDocument").getBytes(StandardCharsets.UTF_8));
        assertEquals("Strict text\n", extractor.extract("strict.docx", zip(strict)).text());
    }

    @Test
    void rejectsMacrosActiveEmbeddingsAndNestedPackages() throws Exception {
        for (String name : new String[]{"word/vbaProject.bin", "word/embeddings/object.bin", "word/activeX/activeX1.xml", "word/package.zip"}) {
            var parts = parts("<w:p/>");
            parts.put(name, new byte[]{1, 2, 3});
            assertThrows(IOException.class, () -> extractor.extract("unsafe.docx", zip(parts)), name);
        }
        var parts = parts("<w:p/>");
        parts.put("word/media/disguised.png", zip(Map.of("payload.txt", new byte[]{1})));
        assertThrows(IOException.class, () -> extractor.extract("nested.docx", zip(parts)));
        assertThrows(IOException.class, () -> extractor.extract("active.docx", zip(parts("<w:altChunk/>"))));
    }

    @Test
    void rejectsExternalRelationshipsIncludingHyperlinksAndPackageRelationships() throws Exception {
        for (String attributes : new String[]{
                "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink\" Target=\"https://example.org\" TargetMode=\"External\"",
                "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/package\" Target=\"embedded.docx\"",
                "Type=\"safe\" Target=\"file:///etc/passwd\"",
                "Type=\"safe\" Target=\"../../../../outside.xml\""}) {
            var parts = parts("<w:p/>");
            parts.put("word/_rels/document.xml.rels", ("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"r1\" " + attributes + "/></Relationships>").getBytes(StandardCharsets.UTF_8));
            assertThrows(IOException.class, () -> extractor.extract("external.docx", zip(parts)), attributes);
        }
    }

    @Test
    void rejectsDtdEntityExpansionAndDeepXml() throws Exception {
        var parts = parts("<w:p/>");
        parts.put("word/document.xml", ("<!DOCTYPE foo [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>&xxe;</w:t></w:r></w:p></w:body></w:document>").getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> extractor.extract("xxe.docx", zip(parts)));
        assertThrows(IOException.class, () -> extractor.extract("deep.docx", zip(parts("<w:p>".repeat(130) + "</w:p>".repeat(130)))));
    }

    @Test
    void rejectsDocxTextBeyondLimitDuringSaxExtraction() throws Exception {
        StringBuilder text = new StringBuilder();
        Random random = new Random(19);
        for (int i = 0; i < 200_001; i++) text.append((char) ('a' + random.nextInt(26)));
        IOException error = assertThrows(IOException.class, () -> extractor.extract("long.docx", zip(parts("<w:p><w:r><w:t>" + text + "</w:t></w:r></w:p>"))));
        assertTrue(error.getMessage().contains("200000"));
    }

    @Test
    void rejectsZipTraversalCountRatiosAndTruncatedDirectory() throws Exception {
        for (String name : new String[]{"../escape", "/absolute", "word/../escape", "C:/windows", "word\\escape"}) {
            var parts = parts("<w:p/>");
            parts.put(name, new byte[]{1});
            assertThrows(IOException.class, () -> extractor.extract("traversal.docx", zip(parts)));
        }
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int i = 0; i <= SafeZip.MAX_ENTRIES; i++) entries.put("entry" + i, new byte[]{1});
        assertThrows(IOException.class, () -> extractor.extract("count.docx", zip(entries)));
        entries.clear();
        entries.put("bomb.xml", "a".repeat(300_000).getBytes(StandardCharsets.US_ASCII));
        IOException ratio = assertThrows(IOException.class, () -> extractor.extract("bomb.docx", zip(entries)));
        assertTrue(ratio.getMessage().contains("ratio"));
        byte[] complete = zip(parts("<w:p/>"));
        assertThrows(IOException.class, () -> extractor.extract("truncated.docx", java.util.Arrays.copyOf(complete, complete.length - 22)));
    }

    @Test
    void zipExpandedAggregateAndIndividualEntryBudgetsAreIndependent() throws Exception {
        byte[] block = new byte[24 * 1024];
        new Random(7).nextBytes(block);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int i = 0; i < 150; i++) output.write(block);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) entries.put("part" + i, output.toByteArray());
        byte[] aggregate = zip(entries);
        assertTrue(aggregate.length < DocumentExtractor.MAX_BYTES);
        IOException error = assertThrows(IOException.class, () -> extractor.extract("aggregate.docx", aggregate));
        assertTrue(error.getMessage().contains("expanded"));
        byte[] individual = new byte[SafeZip.MAX_ENTRY_BYTES + 1];
        assertThrows(IOException.class, () -> extractor.extract("entry.docx", zip(Map.of("huge.xml", individual))));
    }

    @Test
    void rejectsDuplicateZipEntriesAndMalformedRelationshipPartNames() throws Exception {
        var entries = parts("<w:p/>");
        entries.put("word/one.xml", "<root/>".getBytes(StandardCharsets.UTF_8));
        entries.put("word/two.xml", "<root/>".getBytes(StandardCharsets.UTF_8));
        byte[] duplicate = storedZip(entries);
        byte[] oldName = "word/two.xml".getBytes(StandardCharsets.UTF_8);
        byte[] newName = "word/one.xml".getBytes(StandardCharsets.UTF_8);
        for (int occurrence = 0; occurrence < 2; occurrence++) {
            int at = indexOf(duplicate, oldName);
            System.arraycopy(newName, 0, duplicate, at, newName.length);
        }
        IOException error = assertThrows(IOException.class, () -> extractor.extract("duplicates.docx", duplicate));
        assertTrue(error.getMessage().contains("Duplicate"));
        entries = parts("<w:p/>");
        entries.put("malformed.rels", RELS.getBytes(StandardCharsets.UTF_8));
        byte[] malformed = zip(entries);
        IOException relationship = assertThrows(IOException.class, () -> extractor.extract("malformed.docx", malformed));
        assertTrue(relationship.getMessage().contains("relationship part path"));
    }

    @Test
    void storedZipEntriesAlsoWorkAndCrcTamperingFails() throws Exception {
        byte[] bytes = storedZip(parts("<w:p><w:r><w:t>Plain text</w:t></w:r></w:p>"));
        assertTrue(extractor.extract("stored.docx", bytes).text().contains("Plain text"));
        int at = indexOf(bytes, "Plain text".getBytes(StandardCharsets.UTF_8));
        bytes[at] = 'X';
        assertThrows(IOException.class, () -> extractor.extract("corrupt.docx", bytes));
    }

    private static Map<String, byte[]> parts(String body) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", TYPES.getBytes(StandardCharsets.UTF_8));
        entries.put("_rels/.rels", RELS.getBytes(StandardCharsets.UTF_8));
        entries.put("word/document.xml", ("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>" + body + "</w:body></w:document>").getBytes(StandardCharsets.UTF_8));
        return entries;
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException { return zip(entries, false); }
    private static byte[] storedZip(Map<String, byte[]> entries) throws IOException { return zip(entries, true); }
    private static byte[] zip(Map<String, byte[]> entries, boolean stored) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (var entry : entries.entrySet()) {
                ZipEntry item = new ZipEntry(entry.getKey());
                if (stored) {
                    CRC32 crc = new CRC32();
                    crc.update(entry.getValue());
                    item.setMethod(ZipEntry.STORED);
                    item.setSize(entry.getValue().length);
                    item.setCrc(crc.getValue());
                }
                zip.putNextEntry(item);
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static byte[] pdf(String... pages) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (String text : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(50, 700);
                    stream.showText(text);
                    stream.endText();
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private static int indexOf(byte[] bytes, byte[] target) {
        outer: for (int i = 0; i <= bytes.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) if (bytes[i + j] != target[j]) continue outer;
            return i;
        }
        throw new AssertionError("Missing fixture text");
    }
}
