package com.writingassistant.server;

import com.google.gson.*;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class OriginalityDeskServerTest {
    @TempDir Path directory;
    OriginalityDeskServer server;
    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    static final String TEXT = "The northern observatory records the movement of distant stars with carefully calibrated instruments. "
            + "Researchers compare the collected measurements against their original reference tables before publishing results. "
            + "Reliable scientific evidence requires transparent methods and independent review by experienced investigators. "
            + "This synthetic paragraph contains enough distinct words to exercise local exact overlap detection without external services.";

    @BeforeEach void start() throws Exception { startServer(); }
    void startServer() throws Exception { startServer(Duration.ofSeconds(30)); }
    void startServer(Duration timeout) throws Exception {
        server = new OriginalityDeskServer(new ServerConfig(directory, "127.0.0.1", 0, "http://localhost:0", false, timeout));
        server.start();
    }
    @AfterEach void stop() throws Exception { if (server != null) server.close(); }

    record Response(int status, byte[] bytes, HttpHeaders headers) {
        String text() { return new String(bytes, StandardCharsets.UTF_8); }
        JsonObject json() { return JsonParser.parseString(text()).getAsJsonObject(); }
        Response expect(int expected) { assertEquals(expected, status, text()); return this; }
    }
    Response request(String method, String path, String cookie, String type, byte[] body, String suppliedOrigin, Map<String,String> extra) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(server.publicOrigin() + path)).timeout(Duration.ofSeconds(20));
        if (cookie != null) builder.header("Cookie", cookie);
        if (suppliedOrigin != null) builder.header("Origin", suppliedOrigin);
        if (type != null) builder.header("Content-Type", type);
        if (body != null && body.length > Service.MAX_UPLOAD) builder.expectContinue(true);
        extra.forEach(builder::header);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        HttpResponse<byte[]> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        return new Response(response.statusCode(), response.body(), response.headers());
    }
    Response json(String method, String path, String cookie, Object body) throws Exception {
        return request(method, path, cookie, "application/json", body == null ? null : Service.JSON.toJson(body).getBytes(StandardCharsets.UTF_8), server.publicOrigin(), Map.of());
    }
    String register(String email) throws Exception {
        Response response = json("POST", "/api/v1/auth/register", null,
                Map.of("name", "Test User", "email", email, "password", "a long test password 123", "workspaceName", "Workspace " + email, "role", "SUPERUSER")).expect(201);
        assertEquals("INSTITUTION_ADMIN", response.json().getAsJsonObject("user").get("role").getAsString());
        return response.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
    }
    JsonObject upload(String cookie, String filename, String kind, String text) throws Exception {
        return request("POST", "/api/v1/documents?filename=" + URLEncoder.encode(filename, StandardCharsets.UTF_8) + "&kind=" + kind,
                cookie, "text/plain", text.getBytes(StandardCharsets.UTF_8), server.publicOrigin(), Map.of()).expect(201).json();
    }
    JsonObject ready(String cookie, JsonObject doc) throws Exception {
        return poll(cookie, "/api/v1/documents/" + doc.get("id").getAsString(), "READY");
    }
    JsonObject poll(String cookie, String path, String wanted) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        JsonObject last = null;
        while (System.nanoTime() < deadline) {
            last = json("GET", path, cookie, null).expect(200).json();
            String status = last.get("status").getAsString();
            if (status.equals(wanted)) return last;
            if (Set.of("FAILED", "CANCELLED").contains(status)) fail("Unexpected terminal state: " + last);
            Thread.sleep(75);
        }
        fail("Polling timed out: " + last); return null;
    }
    JsonObject analyze(String cookie, String documentId, Object sources, String key) throws Exception {
        Map<String,Object> input = new LinkedHashMap<>();
        input.put("documentId", documentId); input.put("profile", "STANDARD");
        if (sources != null) input.put("sourceIds", sources);
        return request("POST", "/api/v1/analyses", cookie, "application/json", Service.JSON.toJson(input).getBytes(StandardCharsets.UTF_8), server.publicOrigin(), key == null ? Map.of() : Map.of("Idempotency-Key", key)).expect(202).json();
    }
    Connection database() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("server.sqlite3"));
        try (Statement st = connection.createStatement()) { st.execute("PRAGMA busy_timeout=5000"); st.execute("PRAGMA foreign_keys=ON"); }
        return connection;
    }
    long count(String sql) throws Exception {
        try (Connection c = database(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) { assertTrue(rs.next()); return rs.getLong(1); }
    }
    void execute(String sql) throws Exception { try (Connection c = database(); Statement st = c.createStatement()) { st.executeUpdate(sql); } }

    @Test void authenticationUsesOpaqueHashedExpiringSessionsAndLogoutRevokes() throws Exception {
        String cookie = register("first@example.test");
        JsonObject me = json("GET", "/api/v1/auth/me", cookie, null).expect(200).json();
        assertEquals("first@example.test", me.getAsJsonObject("user").get("email").getAsString());
        assertFalse(me.toString().contains("password"));
        assertEquals(1, count("SELECT COUNT(*) FROM schema_migrations WHERE version=1"));
        try (Connection c = database(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT token_hash,expires_at FROM sessions")) {
            assertTrue(rs.next()); assertEquals(64, rs.getString(1).length()); assertFalse(cookie.contains(rs.getString(1)));
            assertTrue(rs.getLong(2) > System.currentTimeMillis());
        }
        json("POST", "/api/v1/auth/login", null, Map.of("email", "first@example.test", "password", "wrong password")).expect(401);
        json("POST", "/api/v1/auth/login", null, Map.of("email", "unknown@example.test", "password", "wrong password")).expect(401);
        Response login = json("POST", "/api/v1/auth/login", null, Map.of("email", "FIRST@example.test", "password", "a long test password 123")).expect(200);
        String set = login.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(set.contains("HttpOnly")); assertTrue(set.contains("SameSite=Strict")); assertTrue(set.contains("Max-Age=43200"));
        assertFalse(set.contains("a long test"));
        String loginCookie = set.split(";", 2)[0];
        json("POST", "/api/v1/auth/logout", loginCookie, Map.of()).expect(204);
        json("GET", "/api/v1/auth/me", loginCookie, null).expect(401);
        execute("UPDATE sessions SET expires_at=0");
        json("GET", "/api/v1/auth/me", cookie, null).expect(401);
    }

    @Test void completeWorkflowUsesRealSnapshotsExportsAndSeparateReviews() throws Exception {
        String cookie = register("workflow@example.test");
        JsonObject source = ready(cookie, upload(cookie, "=2+2.txt", "REFERENCE", TEXT));
        JsonObject submitted = ready(cookie, upload(cookie, "paper.txt", "SUBMISSION", TEXT));
        assertTrue(submitted.get("wordCount").getAsInt() > 40);
        assertEquals(1, submitted.get("pageCount").getAsInt());
        JsonObject analysis = analyze(cookie, submitted.get("id").getAsString(), null, "one-analysis");
        String id = analysis.get("id").getAsString();
        JsonObject complete = poll(cookie, "/api/v1/analyses/" + id, "COMPLETED");
        assertEquals(1, complete.get("sourceCount").getAsInt());
        JsonObject snapshot = json("GET", "/api/v1/reports/" + id, cookie, null).expect(200).json();
        assertEquals(TEXT, snapshot.get("documentText").getAsString());
        assertEquals("paper.txt", snapshot.get("filename").getAsString());
        assertEquals(1, snapshot.get("pageCount").getAsInt());
        assertEquals("und", snapshot.get("language").getAsString());
        assertEquals(Security.hash(TEXT), snapshot.get("sha256").getAsString());
        assertEquals(submitted.get("createdAt"), snapshot.get("submittedAt"));
        assertEquals(1, snapshot.getAsJsonArray("sources").size());
        JsonObject identity = snapshot.getAsJsonArray("sources").get(0).getAsJsonObject();
        assertEquals(source.get("id"), identity.get("id"));
        assertEquals("=2+2.txt", identity.get("title").getAsString());
        assertEquals("REFERENCE", identity.get("type").getAsString());
        assertEquals(Security.hash(TEXT), identity.get("sha256").getAsString());
        assertTrue(snapshot.getAsJsonObject("result").getAsJsonArray("matches").size() > 0);
        assertEquals(source.get("id"), snapshot.getAsJsonObject("result").getAsJsonArray("matches").get(0).getAsJsonObject().get("sourceId"));
        Response jsonExport = json("GET", "/api/v1/reports/" + id + "/json", cookie, null).expect(200);
        assertEquals(snapshot, jsonExport.json()); assertEquals("no-store", jsonExport.headers().firstValue("Cache-Control").orElseThrow());
        Response html = json("GET", "/api/v1/reports/" + id + "/html", cookie, null).expect(200);
        assertTrue(html.text().startsWith("<!doctype html>")); assertTrue(html.headers().firstValue("Content-Security-Policy").orElseThrow().contains("sandbox"));
        Response csv = json("GET", "/api/v1/reports/" + id + "/csv", cookie, null).expect(200);
        assertTrue(csv.text().contains("\"'=2+2.txt\""));
        Response pdf = json("GET", "/api/v1/reports/" + id + "/pdf", cookie, null).expect(200);
        assertTrue(pdf.text().startsWith("%PDF-"));
        try (var parsed = Loader.loadPDF(pdf.bytes())) { assertTrue(parsed.getNumberOfPages() > 0); }
        json("POST", "/api/v1/analyses/" + id + "/reviews", cookie, Map.of("matchIndex", 0, "decision", "DISMISSED", "note", "Reviewed independently")).expect(201);
        JsonObject reviews = json("GET", "/api/v1/analyses/" + id + "/reviews", cookie, null).expect(200).json();
        assertEquals(1, reviews.get("total").getAsInt());
        assertEquals(snapshot, json("GET", "/api/v1/reports/" + id, cookie, null).expect(200).json());
        JsonObject dashboard = json("GET", "/api/v1/dashboard", cookie, null).expect(200).json();
        assertEquals(2, dashboard.get("totalDocuments").getAsInt()); assertEquals(1, dashboard.get("completedAnalyses").getAsInt());
        assertEquals(TEXT.getBytes(StandardCharsets.UTF_8).length * 2L, dashboard.get("storageBytes").getAsLong());
        assertEquals(0, dashboard.get("pendingAnalyses").getAsInt());
        assertTrue(dashboard.getAsJsonArray("activity").size() >= 4);
    }

    @Test void tenantIdsAreHiddenAcrossEveryResourcePathAndSourceValidation() throws Exception {
        String owner = register("owner@example.test"), stranger = register("stranger@example.test");
        JsonObject source = ready(owner, upload(owner, "source.txt", "REFERENCE", TEXT));
        JsonObject submitted = ready(owner, upload(owner, "paper.txt", "SUBMISSION", TEXT));
        String sourceId = source.get("id").getAsString(), documentId = submitted.get("id").getAsString();
        String id = analyze(owner, documentId, List.of(sourceId), "private-idempotency").get("id").getAsString();
        poll(owner, "/api/v1/analyses/" + id, "COMPLETED");
        for (String path : List.of("/documents/" + documentId, "/analyses/" + id, "/analyses/" + id + "/status", "/analyses/" + id + "/reviews",
                "/reports/" + id, "/reports/" + id + "/json", "/reports/" + id + "/html", "/reports/" + id + "/csv", "/reports/" + id + "/pdf")) {
            json("GET", "/api/v1" + path, stranger, null).expect(404);
            json("GET", "/api/v1" + path, null, null).expect(401);
        }
        json("DELETE", "/api/v1/documents/" + documentId, stranger, null).expect(404);
        json("POST", "/api/v1/analyses/" + id + "/cancel", stranger, Map.of()).expect(404);
        json("POST", "/api/v1/analyses/" + id + "/reviews", stranger, Map.of("matchIndex", 0, "decision", "ACCEPTED", "note", "intrusion")).expect(404);
        json("POST", "/api/v1/analyses", stranger, Map.of("documentId", documentId, "profile", "STANDARD", "sourceIds", List.of())).expect(404);
        JsonObject ownDoc = ready(stranger, upload(stranger, "other.txt", "SUBMISSION", TEXT));
        json("POST", "/api/v1/analyses", stranger, Map.of("documentId", ownDoc.get("id").getAsString(), "profile", "STANDARD", "sourceIds", List.of(sourceId))).expect(404);
        assertEquals(0, json("GET", "/api/v1/analyses", stranger, null).expect(200).json().get("total").getAsInt());
        assertEquals(0, json("GET", "/api/v1/documents?kind=REFERENCE", stranger, null).expect(200).json().get("total").getAsInt());
        JsonObject health = json("GET", "/api/v1/admin/health", stranger, null).expect(200).json();
        assertEquals(0, health.get("failedJobs").getAsInt());
        for (String type : List.of("jobs", "audit-logs")) {
            String response = json("GET", "/api/v1/admin/" + type, stranger, null).expect(200).text();
            assertFalse(response.contains(documentId)); assertFalse(response.contains(sourceId)); assertFalse(response.contains(id));
        }
        assertNotEquals(id, analyze(stranger, ownDoc.get("id").getAsString(), List.of(), "private-idempotency").get("id").getAsString());
    }

    @Test void allPrivateCollectionsAndWritesRequireAuthentication() throws Exception {
        for (String path : List.of("/auth/me", "/dashboard", "/documents", "/analyses", "/admin/health", "/admin/jobs", "/admin/audit-logs"))
            json("GET", "/api/v1" + path, null, null).expect(401);
        for (String path : List.of("/auth/logout", "/documents", "/analyses", "/analyses/missing/cancel", "/analyses/missing/reviews"))
            json("POST", "/api/v1" + path, null, Map.of()).expect(401);
        json("DELETE", "/api/v1/documents/missing", null, null).expect(401);
        json("GET", "/health/live", null, null).expect(200);
        assertEquals(Set.of("status"), json("GET", "/health/ready", null, null).expect(200).json().keySet());
    }

    @Test void sameOriginStrictJsonAndStaticAllowlistAreEnforced() throws Exception {
        String cookie = register("security@example.test");
        byte[] login = "{}".getBytes(StandardCharsets.UTF_8);
        request("POST", "/api/v1/auth/login", null, "application/json", login, null, Map.of()).expect(403);
        request("POST", "/api/v1/auth/login", null, "application/json", login, "https://evil.example", Map.of()).expect(403);
        request("POST", "/api/v1/auth/logout", cookie, "text/plain", login, server.publicOrigin(), Map.of()).expect(415);
        request("GET", "/api/v1/auth/me", cookie, null, null, server.publicOrigin(), Map.of("Sec-Fetch-Site", "same-site")).expect(403);
        request("GET", "/api/v1/auth/me", cookie, null, null, "https://evil.example", Map.of()).expect(403);
        for (String malformed : List.of("{", "{} {}", "{name:'bad'}", "{\"email\":\"a\",\"email\":\"b\"}", "/*comment*/{}", "[]"))
            request("POST", "/api/v1/auth/login", null, "application/json", malformed.getBytes(StandardCharsets.UTF_8), server.publicOrigin(), Map.of()).expect(400);
        json("GET", "/api/v1/documents?page=0", cookie, null).expect(400);
        json("GET", "/api/v1/documents?size=101", cookie, null).expect(400);
        json("GET", "/api/v1/documents?page=1&page=2", cookie, null).expect(400);
        for (String path : List.of("/pom.xml", "/web/index.html", "/%2e%2e/pom.xml", "/api/v1/documents/%2fsecret"))
            json("GET", path, cookie, null).expect(404);
        Response notFound = json("GET", "/not-found", cookie, null).expect(404);
        assertTrue(notFound.json().getAsJsonObject("error").has("code")); assertFalse(notFound.text().contains("Exception"));
        assertTrue(notFound.headers().firstValue("Content-Security-Policy").isPresent());
    }

    @Test void uploadErrorsDeduplicationAndPaginationAreReal() throws Exception {
        String cookie = register("uploads@example.test");
        JsonObject first = ready(cookie, upload(cookie, "first.txt", "REFERENCE", TEXT));
        request("POST", "/api/v1/documents?filename=again.txt&kind=REFERENCE", cookie, "text/plain", TEXT.getBytes(StandardCharsets.UTF_8), server.publicOrigin(), Map.of()).expect(409);
        ready(cookie, upload(cookie, "same-kind-other.txt", "SUBMISSION", TEXT));
        request("POST", "/api/v1/documents?filename=bad.pdf&kind=REFERENCE", cookie, "application/pdf", TEXT.getBytes(StandardCharsets.UTF_8), server.publicOrigin(), Map.of()).expect(415);
        request("POST", "/api/v1/documents?filename=bad.txt", cookie, "text/plain", new byte[]{0, 1, 2}, server.publicOrigin(), Map.of()).expect(415);
        request("POST", "/api/v1/documents?filename=..%2Fbad.txt", cookie, "text/plain", new byte[]{65}, server.publicOrigin(), Map.of()).expect(400);
        request("POST", "/api/v1/documents?filename=empty.txt", cookie, "text/plain", new byte[0], server.publicOrigin(), Map.of()).expect(400);
        request("POST", "/api/v1/documents?filename=bad.exe", cookie, "application/octet-stream", new byte[]{65}, server.publicOrigin(), Map.of()).expect(415);
        request("POST", "/api/v1/documents?filename=wrong.txt", cookie, "application/pdf", new byte[]{65}, server.publicOrigin(), Map.of()).expect(415);
        request("POST", "/api/v1/documents?filename=big.txt", cookie, "text/plain", new byte[Service.MAX_UPLOAD + 1], server.publicOrigin(), Map.of()).expect(413);
                byte[] tooLarge = new byte[Service.MAX_UPLOAD + 1];
                HttpRequest chunked = HttpRequest.newBuilder(URI.create(server.publicOrigin() + "/api/v1/documents?filename=chunked.txt"))
                        .timeout(Duration.ofSeconds(20)).header("Cookie", cookie).header("Origin", server.publicOrigin()).header("Content-Type", "text/plain")
                        .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new java.io.ByteArrayInputStream(tooLarge))).build();
                assertEquals(413, http.send(chunked, HttpResponse.BodyHandlers.ofByteArray()).statusCode());
        JsonObject list = json("GET", "/api/v1/documents?kind=REFERENCE&page=1&size=1", cookie, null).expect(200).json();
        assertEquals(1, list.get("total").getAsInt()); assertEquals(1, list.get("size").getAsInt());
        assertEquals(first.get("id"), list.getAsJsonArray("items").get(0).getAsJsonObject().get("id"));
        assertEquals(0, json("GET", "/api/v1/documents?kind=REFERENCE&page=2&size=1", cookie, null).expect(200).json().getAsJsonArray("items").size());
        try (var paths = Files.list(directory.resolve("documents"))) {
            for (Path path : paths.toList()) assertTrue(path.getFileName().toString().matches("[A-Za-z0-9_-]{43}"));
        }
    }

    @Test void invalidExtractableContentFailsPersistentlyWithoutInventedProgress() throws Exception {
        String cookie = register("failure@example.test");
        JsonObject doc = request("POST", "/api/v1/documents?filename=broken.pdf&kind=SUBMISSION", cookie, "application/pdf",
                "%PDF-invalid".getBytes(StandardCharsets.US_ASCII), server.publicOrigin(), Map.of()).expect(201).json();
        JsonObject failed = poll(cookie, "/api/v1/documents/" + doc.get("id").getAsString(), "FAILED");
        assertFalse(failed.get("error").getAsString().isBlank()); assertFalse(failed.has("progress"));
        json("POST", "/api/v1/analyses", cookie, Map.of("documentId", doc.get("id").getAsString(), "profile", "QUICK")).expect(409);
        assertEquals(1, json("GET", "/api/v1/admin/health", cookie, null).expect(200).json().get("failedJobs").getAsInt());
    }

    @Test void idempotencyIsTenantScopedAndDeletionErasesDependentSnapshotsReviewsJobsAndBytes() throws Exception {
        String cookie = register("deletion@example.test");
        JsonObject source = ready(cookie, upload(cookie, "reference.txt", "REFERENCE", TEXT));
        JsonObject submitted = ready(cookie, upload(cookie, "submission.txt", "SUBMISSION", TEXT));
        String sourceId = source.get("id").getAsString(), documentId = submitted.get("id").getAsString();
        String id = analyze(cookie, documentId, List.of(sourceId), "replay-me").get("id").getAsString();
        assertEquals(id, analyze(cookie, documentId, List.of(sourceId), "replay-me").get("id").getAsString());
        poll(cookie, "/api/v1/analyses/" + id, "COMPLETED");
        assertEquals(id, analyze(cookie, documentId, List.of(sourceId), "replay-me").get("id").getAsString());
        request("POST", "/api/v1/analyses", cookie, "application/json", Service.JSON.toJson(Map.of("documentId", documentId, "profile", "QUICK", "sourceIds", List.of(sourceId))).getBytes(StandardCharsets.UTF_8), server.publicOrigin(), Map.of("Idempotency-Key", "replay-me")).expect(409);
        assertEquals(1, count("SELECT COUNT(*) FROM jobs WHERE kind='ANALYZE'"));
        json("POST", "/api/v1/analyses/" + id + "/reviews", cookie, Map.of("matchIndex", 0, "decision", "ACCEPTED", "note", "temporary note")).expect(201);
        json("DELETE", "/api/v1/documents/" + sourceId, cookie, null).expect(204);
        json("GET", "/api/v1/analyses/" + id, cookie, null).expect(404);
        json("GET", "/api/v1/reports/" + id, cookie, null).expect(404);
        assertEquals(0, count("SELECT COUNT(*) FROM analyses")); assertEquals(0, count("SELECT COUNT(*) FROM reviews"));
        assertEquals(0, count("SELECT COUNT(*) FROM analysis_sources")); assertEquals(0, count("SELECT COUNT(*) FROM jobs WHERE kind='ANALYZE'"));
        assertEquals(1, count("SELECT COUNT(*) FROM documents"));
        try (var paths = Files.list(directory.resolve("documents"))) { assertEquals(1, paths.count()); }
        String audit = json("GET", "/api/v1/admin/audit-logs", cookie, null).expect(200).text();
        assertTrue(audit.contains("DOCUMENT_DELETED")); assertFalse(audit.contains("temporary note")); assertFalse(audit.contains(TEXT));
        json("DELETE", "/api/v1/documents/" + documentId, cookie, null).expect(204);
        assertEquals(0, count("SELECT COUNT(*) FROM jobs"));
        try (var paths = Files.list(directory.resolve("documents"))) { assertEquals(0, paths.count()); }
    }

    @Test void interruptedJobsRecoverOnRestartAndRetriesHaveAnUpperBound() throws Exception {
        String cookie = register("recovery@example.test");
        JsonObject submitted = ready(cookie, upload(cookie, "recover.txt", "SUBMISSION", TEXT));
        String id = analyze(cookie, submitted.get("id").getAsString(), List.of(), null).get("id").getAsString();
        poll(cookie, "/api/v1/analyses/" + id, "COMPLETED");
        server.close(); server = null;
        execute("UPDATE analyses SET status='RUNNING',result_json=NULL,completed_at=NULL");
        execute("UPDATE jobs SET status='RUNNING',attempts=1 WHERE kind='ANALYZE'");
        startServer();
        poll(cookie, "/api/v1/analyses/" + id, "COMPLETED");
        assertEquals(2, count("SELECT attempts FROM jobs WHERE kind='ANALYZE'"));
        server.close(); server = null;
        execute("UPDATE analyses SET status='RUNNING',result_json=NULL,completed_at=NULL");
        execute("UPDATE jobs SET status='RUNNING',attempts=3 WHERE kind='ANALYZE'");
        startServer();
        poll(cookie, "/api/v1/analyses/" + id, "FAILED");
        assertEquals(3, count("SELECT attempts FROM jobs WHERE kind='ANALYZE'"));
    }

    @Test void persistedQueuedCancellationCannotBeResurrectedOnRestart() throws Exception {
        String cookie = register("cancel@example.test");
        JsonObject submitted = ready(cookie, upload(cookie, "cancel.txt", "SUBMISSION", TEXT));
        String id = analyze(cookie, submitted.get("id").getAsString(), List.of(), null).get("id").getAsString();
        poll(cookie, "/api/v1/analyses/" + id, "COMPLETED");
        server.close(); server = null;
        execute("UPDATE analyses SET status='QUEUED',result_json=NULL,completed_at=NULL");
        execute("UPDATE jobs SET status='QUEUED',available_at=9999999999999 WHERE kind='ANALYZE'");
        startServer();
        json("POST", "/api/v1/analyses/" + id + "/cancel", cookie, Map.of()).expect(200);
        assertEquals("CANCELLED", json("GET", "/api/v1/analyses/" + id + "/status", cookie, null).expect(200).json().get("status").getAsString());
        json("GET", "/api/v1/reports/" + id, cookie, null).expect(409);
        server.close(); server = null; startServer();
        assertEquals("CANCELLED", json("GET", "/api/v1/analyses/" + id, cookie, null).expect(200).json().get("status").getAsString());
        assertEquals(0, count("SELECT COUNT(*) FROM jobs WHERE status IN ('QUEUED','RUNNING')"));
    }

    @Test void jobTimeoutIsTerminalAndNeverPublishesALateResult() throws Exception {
        String cookie = register("timeout@example.test");
        JsonObject submitted = ready(cookie, upload(cookie, "timeout.txt", "SUBMISSION", TEXT));
        server.close(); server = null; startServer(Duration.ofNanos(1));
        String id = analyze(cookie, submitted.get("id").getAsString(), List.of(), null).get("id").getAsString();
        JsonObject failed = poll(cookie, "/api/v1/analyses/" + id, "FAILED");
        assertEquals("Job exceeded its time limit", failed.get("error").getAsString());
        Thread.sleep(200);
        assertFalse(json("GET", "/api/v1/analyses/" + id, cookie, null).expect(200).json().has("result"));
        assertEquals(1, count("SELECT attempts FROM jobs WHERE kind='ANALYZE'"));
        json("GET", "/api/v1/reports/" + id, cookie, null).expect(409);
    }

    @Test void transientPublishFailureRetriesWithPersistedBackoff() throws Exception {
        String cookie = register("retry@example.test");
        JsonObject submitted = ready(cookie, upload(cookie, "retry.txt", "SUBMISSION", TEXT));
        execute("CREATE TRIGGER testing_failure BEFORE UPDATE OF result_json ON analyses WHEN NEW.status='COMPLETED' BEGIN SELECT RAISE(ABORT,'test transient failure'); END");
        String id = analyze(cookie, submitted.get("id").getAsString(), List.of(), null).get("id").getAsString();
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (count("SELECT COUNT(*) FROM jobs WHERE kind='ANALYZE' AND status='QUEUED' AND attempts=1") == 0 && System.nanoTime() < deadline) Thread.sleep(25);
        assertEquals(1, count("SELECT COUNT(*) FROM jobs WHERE kind='ANALYZE' AND status='QUEUED' AND attempts=1"));
        assertTrue(count("SELECT available_at FROM jobs WHERE kind='ANALYZE'") > System.currentTimeMillis());
        execute("DROP TRIGGER testing_failure");
        poll(cookie, "/api/v1/analyses/" + id, "COMPLETED");
        assertEquals(2, count("SELECT attempts FROM jobs WHERE kind='ANALYZE'"));
    }

    @Test void sourceCapsSelfMatchingAndDatabaseTenantForeignKeysAreEnforced() throws Exception {
        String cookie = register("constraints@example.test");
        JsonObject submitted = ready(cookie, upload(cookie, "constraints.txt", "SUBMISSION", TEXT));
        String docId = submitted.get("id").getAsString();
        json("POST", "/api/v1/analyses", cookie, Map.of("documentId", docId, "profile", "QUICK", "sourceIds", List.of(docId))).expect(400);
        List<String> tooMany = new ArrayList<>(); for (int i = 0; i < 51; i++) tooMany.add("source-" + i);
        json("POST", "/api/v1/analyses", cookie, Map.of("documentId", docId, "profile", "QUICK", "sourceIds", tooMany)).expect(400);
        json("POST", "/api/v1/analyses", cookie, Map.of("documentId", docId, "profile", "REMOTE_AI", "sourceIds", List.of())).expect(400);
        register("other-constraint@example.test");
        String analysisId = analyze(cookie, docId, List.of(), null).get("id").getAsString();
        assertThrows(SQLException.class, () -> execute("INSERT INTO analysis_sources(workspace_id,analysis_id,document_id) SELECT workspace_id,'" + analysisId + "','" + docId + "' FROM users WHERE email='other-constraint@example.test'"));
    }

    @Test void httpAuthenticationRateLimitAndPersistedQueueCapacityAreEnforced() throws Exception {
        String cookie = register("limits@example.test");
        JsonObject submitted = ready(cookie, upload(cookie, "limits.txt", "SUBMISSION", TEXT));
        for (int i = 0; i < 14; i++) json("POST", "/api/v1/auth/login", null, Map.of()).expect(400);
        Response limited = json("POST", "/api/v1/auth/login", null, Map.of()).expect(429);
        assertTrue(limited.headers().firstValue("Retry-After").isPresent());
        execute("WITH RECURSIVE n(x) AS (SELECT 1 UNION ALL SELECT x+1 FROM n WHERE x<100) INSERT INTO jobs(id,workspace_id,kind,resource_id,status,available_at,created_at) SELECT 'fixture-'||x,u.workspace_id,'EXTRACT','fixture-doc-'||x,'QUEUED',9999999999999,'fixture' FROM n CROSS JOIN users u WHERE u.email='limits@example.test'");
        request("POST", "/api/v1/documents?filename=queue-full.txt", cookie, "text/plain", "unique content".getBytes(StandardCharsets.UTF_8), server.publicOrigin(), Map.of()).expect(429);
        json("POST", "/api/v1/analyses", cookie, Map.of("documentId", submitted.get("id").getAsString(), "profile", "QUICK", "sourceIds", List.of())).expect(429);
        assertEquals(1, count("SELECT COUNT(*) FROM documents")); assertEquals(0, count("SELECT COUNT(*) FROM analyses"));
        try (var paths = Files.list(directory.resolve("documents"))) { assertEquals(1, paths.count()); }
    }

    @Test void sourceDeletionAlsoRemovesPersistedQueuedDependentsBeforeRestart() throws Exception {
        String cookie = register("queued-delete@example.test");
        JsonObject source = ready(cookie, upload(cookie, "source.txt", "REFERENCE", TEXT));
        JsonObject submitted = ready(cookie, upload(cookie, "submission.txt", "SUBMISSION", TEXT));
        String id = analyze(cookie, submitted.get("id").getAsString(), List.of(source.get("id").getAsString()), null).get("id").getAsString();
        poll(cookie, "/api/v1/analyses/" + id, "COMPLETED");
        server.close(); server = null;
        execute("UPDATE analyses SET status='QUEUED',result_json=NULL,completed_at=NULL");
        execute("UPDATE jobs SET status='QUEUED',available_at=9999999999999 WHERE kind='ANALYZE'");
        startServer();
        json("DELETE", "/api/v1/documents/" + source.get("id").getAsString(), cookie, null).expect(204);
        server.close(); server = null; startServer();
        json("GET", "/api/v1/analyses/" + id, cookie, null).expect(404);
        json("GET", "/api/v1/reports/" + id, cookie, null).expect(404);
        assertEquals(0, count("SELECT COUNT(*) FROM jobs WHERE kind='ANALYZE'"));
        assertEquals(0, count("SELECT COUNT(*) FROM analyses"));
    }

    @Test void failedCommitCleansTheUntrackedBlobAndReturnsOnlyASafeError() throws Exception {
        String cookie = register("commit-failure@example.test");
        execute("CREATE TABLE testing_commit_failure (id TEXT REFERENCES documents(id) DEFERRABLE INITIALLY DEFERRED)");
        execute("CREATE TRIGGER testing_deferred_failure AFTER INSERT ON documents BEGIN INSERT INTO testing_commit_failure(id) VALUES('missing'); END");
        Response failed = request("POST", "/api/v1/documents?filename=commit.txt", cookie, "text/plain", TEXT.getBytes(StandardCharsets.UTF_8), server.publicOrigin(), Map.of()).expect(500);
        assertEquals("INTERNAL_ERROR", failed.json().getAsJsonObject("error").get("code").getAsString());
        assertFalse(failed.text().contains("SQLITE")); assertFalse(failed.text().contains(directory.toString()));
        assertEquals(0, count("SELECT COUNT(*) FROM documents"));
        try (var paths = Files.list(directory.resolve("documents"))) { assertEquals(0, paths.count()); }
        execute("DROP TRIGGER testing_deferred_failure"); execute("DROP TABLE testing_commit_failure");
        ready(cookie, upload(cookie, "recovered.txt", "SUBMISSION", TEXT));
    }

    @Test void staticClasspathAssetsUseOnlyTheGetAllowlist() throws Exception {
        for (String path : List.of("/", "/index.html", "/app.js", "/styles.css")) {
            Response asset = json("GET", path, null, null).expect(200);
            assertTrue(asset.bytes().length > 0); assertTrue(asset.headers().firstValue("X-Content-Type-Options").isPresent());
        }
        json("POST", "/app.js", null, Map.of()).expect(405);
        json("GET", "/db/V001__server.sql", null, null).expect(404);
    }

    @Test void forwardedIpHeadersCannotRotateTheAuthenticationRateBucket() throws Exception {
        long elapsedInWindow = System.currentTimeMillis() % 60_000;
        if (elapsedInWindow > 58_000) Thread.sleep(60_000 - elapsedInWindow + 10);
        for (int i = 0; i < 15; i++) {
            request("POST", "/api/v1/auth/login", null, "application/json", "{}".getBytes(StandardCharsets.UTF_8), server.publicOrigin(),
                    Map.of("X-Forwarded-For", "192.0.2." + (i + 1), "Forwarded", "for=198.51.100." + (i + 1), "X-Real-IP", "203.0.113." + (i + 1))).expect(400);
        }
        Response limited = request("POST", "/api/v1/auth/login", null, "application/json", "{}".getBytes(StandardCharsets.UTF_8), server.publicOrigin(),
                Map.of("X-Forwarded-For", "192.0.2.250", "Forwarded", "for=198.51.100.250", "X-Real-IP", "203.0.113.250")).expect(429);
        assertEquals("LIMIT_EXCEEDED", limited.json().getAsJsonObject("error").get("code").getAsString());
        assertEquals("60", limited.headers().firstValue("Retry-After").orElseThrow());
        json("GET", "/health/ready", null, null).expect(200);
    }

    @Test void concurrentDuplicateUploadsCreateOneBlobAndOneExtractionJob() throws Exception {
        String cookie = register("upload-race@example.test");
        HttpRequest upload = HttpRequest.newBuilder(URI.create(server.publicOrigin() + "/api/v1/documents?filename=race.txt&kind=REFERENCE"))
                .timeout(Duration.ofSeconds(20)).header("Cookie", cookie).header("Origin", server.publicOrigin()).header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(TEXT, StandardCharsets.UTF_8)).build();
        var first = http.sendAsync(upload, HttpResponse.BodyHandlers.ofByteArray());
        var second = http.sendAsync(upload, HttpResponse.BodyHandlers.ofByteArray());
        var one = first.get(20, java.util.concurrent.TimeUnit.SECONDS);
        var two = second.get(20, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(Set.of(201, 409), Set.of(one.statusCode(), two.statusCode()));
        var winner = one.statusCode() == 201 ? one : two;
        var duplicate = one.statusCode() == 409 ? one : two;
        assertEquals("DUPLICATE_DOCUMENT", JsonParser.parseString(new String(duplicate.body(), StandardCharsets.UTF_8)).getAsJsonObject()
                .getAsJsonObject("error").get("code").getAsString());
        String documentId = JsonParser.parseString(new String(winner.body(), StandardCharsets.UTF_8)).getAsJsonObject().get("id").getAsString();
        assertEquals(1, count("SELECT COUNT(*) FROM documents"));
        assertEquals(1, count("SELECT COUNT(*) FROM jobs WHERE kind='EXTRACT'"));
        try (var paths = Files.list(directory.resolve("documents"))) { assertEquals(1, paths.count()); }
        json("DELETE", "/api/v1/documents/" + documentId, cookie, null).expect(204);
        Thread.sleep(250);
        json("GET", "/api/v1/documents/" + documentId, cookie, null).expect(404);
        assertEquals(0, count("SELECT COUNT(*) FROM documents"));
        assertEquals(0, count("SELECT COUNT(*) FROM jobs"));
        try (var paths = Files.list(directory.resolve("documents"))) { assertEquals(0, paths.count()); }
    }

    @Test void snapshotMetadataRemainsImmutableAndDoesNotInferUnknownPaginationOrLanguage() throws Exception {
        String cookie = register("snapshot-metadata@example.test");
        JsonObject submitted = ready(cookie, upload(cookie, "original.txt", "SUBMISSION", TEXT));
        String documentId = submitted.get("id").getAsString();
        execute("UPDATE documents SET page_count=0 WHERE id='" + documentId + "'");
        String id = analyze(cookie, documentId, List.of(), null).get("id").getAsString();
        poll(cookie, "/api/v1/analyses/" + id, "COMPLETED");
        JsonObject snapshot = json("GET", "/api/v1/reports/" + id, cookie, null).expect(200).json();
        assertEquals(0, snapshot.get("pageCount").getAsInt()); assertEquals("und", snapshot.get("language").getAsString());
        assertEquals("original.txt", snapshot.get("filename").getAsString());
        assertEquals(Security.hash(TEXT), snapshot.get("sha256").getAsString());
        assertEquals(submitted.get("createdAt"), snapshot.get("submittedAt"));
        assertEquals(0, snapshot.getAsJsonArray("sources").size());
        execute("UPDATE documents SET filename='changed.txt',page_count=999,sha256='changed',created_at='2020-01-01T00:00:00Z' WHERE id='" + documentId + "'");
        assertEquals(snapshot, json("GET", "/api/v1/reports/" + id + "/json", cookie, null).expect(200).json());
    }

    @Test void configuredRetentionDeletesOldFilesAndDependentSnapshotsWithoutCrossTenantDamage() throws Exception {
        String owner = register("retention-owner@example.test"), other = register("retention-other@example.test");
        JsonObject oldReference = ready(owner, upload(owner, "old-reference.txt", "REFERENCE", TEXT));
        JsonObject currentSubmission = ready(owner, upload(owner, "current-submission.txt", "SUBMISSION", TEXT));
        JsonObject currentReference = ready(owner, upload(owner, "current-reference.txt", "REFERENCE", TEXT + " Current reference."));
        JsonObject oldSubmission = ready(owner, upload(owner, "old-submission.txt", "SUBMISSION", TEXT + " Old original."));
        JsonObject otherReference = ready(other, upload(other, "other-current-reference.txt", "REFERENCE", TEXT));
        JsonObject otherSubmission = ready(other, upload(other, "other-current-submission.txt", "SUBMISSION", TEXT));
        JsonObject otherOld = ready(other, upload(other, "other-old.txt", "REFERENCE", TEXT + " Other old reference."));
        String dependent = analyze(owner, currentSubmission.get("id").getAsString(), List.of(oldReference.get("id").getAsString()), null).get("id").getAsString();
        String independent = analyze(other, otherSubmission.get("id").getAsString(), List.of(otherReference.get("id").getAsString()), null).get("id").getAsString();
        poll(owner, "/api/v1/analyses/" + dependent, "COMPLETED"); poll(other, "/api/v1/analyses/" + independent, "COMPLETED");
        json("POST", "/api/v1/analyses/" + dependent + "/reviews", owner, Map.of("matchIndex", 0, "decision", "ACCEPTED", "note", "Private annotation to erase")).expect(201);
        String aged = java.time.Instant.now().minus(Duration.ofDays(3)).toString();
        for (JsonObject doc : List.of(oldReference, oldSubmission, otherOld))
            execute("UPDATE documents SET created_at='" + aged + "' WHERE id='" + doc.get("id").getAsString() + "'");
        server.close(); server = null; startServer();
        json("GET", "/api/v1/documents/" + oldReference.get("id").getAsString(), owner, null).expect(200);
        server.close(); server = null;
        server = new OriginalityDeskServer(new ServerConfig(directory, "127.0.0.1", 0, "http://localhost:0", false, Duration.ofSeconds(30), 1));
        server.start();
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (count("SELECT COUNT(*) FROM audit_logs WHERE action='DOCUMENT_EXPIRED'") < 3 && System.nanoTime() < deadline) Thread.sleep(50);
        assertEquals(3, count("SELECT COUNT(*) FROM audit_logs WHERE action='DOCUMENT_EXPIRED' AND user_id='SYSTEM'"));
        json("GET", "/api/v1/documents/" + oldReference.get("id").getAsString(), owner, null).expect(404);
        json("GET", "/api/v1/documents/" + oldSubmission.get("id").getAsString(), owner, null).expect(404);
        json("GET", "/api/v1/documents/" + otherOld.get("id").getAsString(), other, null).expect(404);
        for (JsonObject doc : List.of(currentSubmission, currentReference))
            json("GET", "/api/v1/documents/" + doc.get("id").getAsString(), owner, null).expect(200);
        for (JsonObject doc : List.of(otherSubmission, otherReference))
            json("GET", "/api/v1/documents/" + doc.get("id").getAsString(), other, null).expect(200);
        json("GET", "/api/v1/reports/" + dependent, owner, null).expect(404);
        json("GET", "/api/v1/reports/" + independent, other, null).expect(200);
        json("GET", "/api/v1/reports/" + independent, owner, null).expect(404);
        assertEquals(4, count("SELECT COUNT(*) FROM documents")); assertEquals(1, count("SELECT COUNT(*) FROM analyses"));
        assertEquals(0, count("SELECT COUNT(*) FROM reviews")); assertEquals(5, count("SELECT COUNT(*) FROM jobs"));
        String ownerAudit = json("GET", "/api/v1/admin/audit-logs", owner, null).expect(200).text();
        String otherAudit = json("GET", "/api/v1/admin/audit-logs", other, null).expect(200).text();
        assertTrue(ownerAudit.contains(oldReference.get("id").getAsString())); assertFalse(ownerAudit.contains(otherOld.get("id").getAsString()));
        assertFalse(otherAudit.contains(oldReference.get("id").getAsString())); assertTrue(otherAudit.contains(otherOld.get("id").getAsString()));
        assertFalse(ownerAudit.contains(TEXT)); assertFalse(ownerAudit.contains("Private annotation to erase"));
        try (var paths = Files.list(directory.resolve("documents"))) { assertEquals(4, paths.count()); }
    }

    @Test void requestCorrelationIdsAreServerGeneratedAndIgnoreCallerValues() throws Exception {
        Response response = request("GET", "/health/live", null, null, null, null, Map.of("X-Request-Id", "person@example.test upstream-secret" )).expect(200);
        String requestId = response.headers().firstValue("X-Request-Id").orElseThrow();
        assertEquals(4, UUID.fromString(requestId).version());
        assertNotEquals(requestId, json("GET", "/health/live", null, null).expect(200).headers().firstValue("X-Request-Id").orElseThrow());
        assertFalse(requestId.contains("example.test"));
    }

    @Test void trustedDatabaseRolesNotCallerFieldsControlPrivilegedEndpoints() throws Exception {
        String cookie = register("roles@example.test");
        JsonObject submitted = ready(cookie, upload(cookie, "role.txt", "SUBMISSION", TEXT));
        String id = analyze(cookie, submitted.get("id").getAsString(), List.of(), null).get("id").getAsString();
        poll(cookie, "/api/v1/analyses/" + id, "COMPLETED");
        execute("UPDATE users SET role='MEMBER'");
        for (String path : List.of("health", "jobs", "audit-logs")) json("GET", "/api/v1/admin/" + path, cookie, null).expect(403);
        json("POST", "/api/v1/analyses/" + id + "/reviews", cookie, Map.of("matchIndex", 0, "decision", "ACCEPTED", "note", "bad", "role", "INSTITUTION_ADMIN")).expect(403);
        json("GET", "/api/v1/analyses/" + id + "/reviews", cookie, null).expect(403);
        json("GET", "/api/v1/reports/" + id, cookie, null).expect(200);
    }
}
