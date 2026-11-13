package com.writingassistant.server;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class FileUploadSecurityTest {

    @Test
    void testUploadFilenameSanitizationAndPathTraversalPrevention(@TempDir Path tempDir) throws Exception {
        try (ServerDatabase db = new ServerDatabase(tempDir.resolve("upload_security_data"))) {
            Service service = new Service(db);

            JsonObject reg = new JsonObject();
            reg.addProperty("name", "Uploader");
            reg.addProperty("email", "uploader@security.test");
            reg.addProperty("password", "SecurePassword123!");
            reg.addProperty("workspaceName", "UploadSec");
            Service.Authentication auth = service.register(reg);
            Service.Actor actor = service.authenticate(auth.token());

            byte[] validText = "Safe content for upload verification.".getBytes(StandardCharsets.UTF_8);

            // 1. Path traversal via ../ or ..\
            String[] maliciousFilenames = {
                    "../evil.txt",
                    "..\\evil.txt",
                    "/etc/passwd",
                    "C:\\Windows\\System32\\drivers\\etc\\hosts",
                    ".",
                    "..",
                    "foo/bar.txt",
                    "foo\\bar.txt",
                    "evil\u0000file.txt",
                    "a".repeat(201) + ".txt"
            };

            for (String badName : maliciousFilenames) {
                ApiException ex = assertThrows(ApiException.class, () ->
                        service.upload(actor, badName, "SUBMISSION", "text/plain", validText));
                assertEquals(400, ex.status, "Malicious filename should be rejected with 400 BAD_REQUEST: " + badName);
            }

            // 2. Upload with valid filename creates randomized blob storage file, NOT using original filename
            Map<String, Object> doc = service.upload(actor, "my-thesis.txt", "SUBMISSION", "text/plain", validText);
            String docId = (String) doc.get("id");
            assertNotNull(docId);

            // Assert original filename is preserved in metadata
            assertEquals("my-thesis.txt", doc.get("filename"));

            // Assert that no file named "my-thesis.txt" exists in the storage directory
            assertFalse(Files.exists(db.blobs.resolve("my-thesis.txt")),
                    "Raw user filename must never be used directly on the filesystem");
        }
    }

    @Test
    void testUploadSizeAndMimeValidation(@TempDir Path tempDir) throws Exception {
        try (ServerDatabase db = new ServerDatabase(tempDir.resolve("upload_size_data"))) {
            Service service = new Service(db);

            JsonObject reg = new JsonObject();
            reg.addProperty("name", "Uploader2");
            reg.addProperty("email", "uploader2@security.test");
            reg.addProperty("password", "SecurePassword123!");
            reg.addProperty("workspaceName", "UploadSize");
            Service.Authentication auth = service.register(reg);
            Service.Actor actor = service.authenticate(auth.token());

            // 1. Empty upload rejected
            ApiException emptyEx = assertThrows(ApiException.class, () ->
                    service.upload(actor, "empty.txt", "SUBMISSION", "text/plain", new byte[0]));
            assertEquals(400, emptyEx.status);

            // 2. Oversized upload (> 10 MiB) rejected
            byte[] oversized = new byte[10 * 1024 * 1024 + 1];
            ApiException sizeEx = assertThrows(ApiException.class, () ->
                    service.upload(actor, "large.txt", "SUBMISSION", "text/plain", oversized));
            assertEquals(413, sizeEx.status);

            // 3. Disguised format (PDF extension with HTML content) rejected
            byte[] fakePdf = "<html><body>Not a PDF</body></html>".getBytes();
            ApiException mimeEx = assertThrows(ApiException.class, () ->
                    service.upload(actor, "fake.pdf", "SUBMISSION", "application/pdf", fakePdf));
            assertEquals(415, mimeEx.status);

            // 4. DOCX with corrupt ZIP rejected
            byte[] fakeDocx = new byte[]{80, 75, 3, 4, 0, 0, 0, 0};
            ApiException docxEx = assertThrows(ApiException.class, () ->
                    service.upload(actor, "corrupt.docx", "SUBMISSION",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", fakeDocx));
            assertEquals(415, docxEx.status);
        }
    }

    @Test
    void testZipBombDefenseInDocxUpload(@TempDir Path tempDir) throws Exception {
        try (ServerDatabase db = new ServerDatabase(tempDir.resolve("zip_bomb_data"))) {
            Service service = new Service(db);

            JsonObject reg = new JsonObject();
            reg.addProperty("name", "Uploader3");
            reg.addProperty("email", "uploader3@security.test");
            reg.addProperty("password", "SecurePassword123!");
            reg.addProperty("workspaceName", "ZipBomb");
            Service.Authentication auth = service.register(reg);
            Service.Actor actor = service.authenticate(auth.token());

            // Create a zip with excessive expansion ratio (> 20MB expanded from small compressed stream)
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
                zos.write("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"></Types>".getBytes());
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("word/document.xml"));
                // Write 21MB of zeros (deflated to tiny size)
                byte[] chunk = new byte[65536];
                for (int i = 0; i < 336; i++) { // 336 * 64KB = ~22MB
                    zos.write(chunk);
                }
                zos.closeEntry();
            }

            byte[] zipBomb = baos.toByteArray();
            ApiException bombEx = assertThrows(ApiException.class, () ->
                    service.upload(actor, "bomb.docx", "SUBMISSION",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", zipBomb));
            assertEquals(400, bombEx.status, "Zip bomb expansion exceeding limits must be rejected with 400");
        }
    }
}
