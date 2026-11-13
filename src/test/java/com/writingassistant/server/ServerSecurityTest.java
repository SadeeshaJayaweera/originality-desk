package com.writingassistant.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ServerSecurityTest {
    @TempDir Path directory;

    @Test void passwordsUseIndependentSaltsSufficientWorkAndConstantTimeComparisonPath() {
        String first = Security.password("a long password"), second = Security.password("a long password");
        assertNotEquals(first, second);
        assertTrue(Integer.parseInt(first.split(":")[0]) >= 210_000);
        assertTrue(Security.verify("a long password", first));
        assertFalse(Security.verify("a wrong password", first));
        assertFalse(Security.verify("a long password", null));
        assertEquals(64, Security.hash("opaque token").length());
        assertNotEquals(Security.token(), Security.token());
    }

    @Test void limiterEnforcesBothPerKeyAndTotalKeyBounds() {
        Security.Limiter limiter = new Security.Limiter();
        limiter.take("one", 2); limiter.take("one", 2);
        ApiException failure = assertThrows(ApiException.class, () -> limiter.take("one", 2));
        assertEquals(429, failure.status);
        for (int i = 0; i < 4095; i++) limiter.take("key-" + i, 1);
        assertThrows(ApiException.class, () -> limiter.take("overflow", 1));
    }

    @Test void environmentDefaultsAndOriginValidationAreExplicit() {
        ServerConfig config = ServerConfig.fromEnvironment(Map.of());
        assertEquals("127.0.0.1", config.host()); assertEquals(8080, config.port());
        assertEquals("http://localhost:8080", config.publicOrigin()); assertFalse(config.secureCookies());
        assertTrue(config.dataDir().getFileName().toString().equals("desk-server-data"));
        ServerConfig secure = ServerConfig.fromEnvironment(Map.of("DESK_DATA_DIR", directory.toString(), "DESK_HOST", "0.0.0.0", "DESK_PORT", "8090", "DESK_PUBLIC_ORIGIN", "https://desk.example", "DESK_SECURE_COOKIES", "true"));
        assertTrue(secure.secureCookies()); assertEquals(8090, secure.port());
        for (String origin : new String[]{"https://user:secret@example.test", "https://example.test/path", "ftp://example.test", "https://example.test?secret=1", "https://example.test#fragment"})
            assertThrows(IllegalArgumentException.class, () -> new ServerConfig(directory, "localhost", 8080, origin, true, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.fromEnvironment(Map.of("DESK_SECURE_COOKIES", "yes")));
        assertThrows(IllegalArgumentException.class, () -> new ServerConfig(directory, "localhost", 8080, "https://example.test", false, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new ServerConfig(directory, "localhost", 65536, "http://example.test", false, Duration.ofSeconds(1)));
    }

    @Test void retentionEnvironmentIsOptInBoundedAndConstructorCompatible() {
        assertEquals(0, ServerConfig.fromEnvironment(Map.of()).retentionDays());
        assertEquals(1, ServerConfig.fromEnvironment(Map.of("DESK_RETENTION_DAYS", "1")).retentionDays());
        assertEquals(3650, ServerConfig.fromEnvironment(Map.of("DESK_RETENTION_DAYS", "3650")).retentionDays());
        assertEquals(0, new ServerConfig(directory, "localhost", 8080, "http://localhost:8080", false, Duration.ofSeconds(1)).retentionDays());
        for (String invalid : new String[]{"-1", "3651", "", "true", "1.5", " 1", "+1", "999999999999"})
            assertThrows(IllegalArgumentException.class, () -> ServerConfig.fromEnvironment(Map.of("DESK_RETENTION_DAYS", invalid)), invalid);
        assertThrows(IllegalArgumentException.class, () -> new ServerConfig(directory, "localhost", 8080, "http://localhost:8080", false, Duration.ofSeconds(1), -1));
        assertThrows(IllegalArgumentException.class, () -> new ServerConfig(directory, "localhost", 8080, "http://localhost:8080", false, Duration.ofSeconds(1), 3651));
    }

    @Test void retentionCutoffUsesExactInstantsAndIncludesUnextractedOriginals() throws Exception {
        try (ServerDatabase db = new ServerDatabase(directory.resolve("retention"))) {
            Service service = new Service(db);
            JsonObject input = new JsonObject();
            input.addProperty("name", "Owner"); input.addProperty("email", "retention-boundary@example.test");
            input.addProperty("password", "a long test password"); input.addProperty("workspaceName", "Boundary");
            Service.Actor actor = service.authenticate(service.register(input).token());
            Instant current = Instant.parse("2026-11-13T12:00:00Z");
            Instant cutoff = current.minus(Duration.ofDays(1));
            String old = (String) service.upload(actor, "old.txt", "REFERENCE", "text/plain", new byte[]{65}).get("id");
            String boundary = (String) service.upload(actor, "boundary.txt", "SUBMISSION", "text/plain", new byte[]{66}).get("id");
            String fresh = (String) service.upload(actor, "fresh.txt", "SUBMISSION", "text/plain", new byte[]{67}).get("id");
            db.transaction(sql -> {
                sql.execute("UPDATE documents SET created_at=? WHERE workspace_id=? AND id=?", cutoff.minusNanos(1).toString(), actor.workspaceId(), old);
                sql.execute("UPDATE documents SET created_at=? WHERE workspace_id=? AND id=?", cutoff.toString(), actor.workspaceId(), boundary);
                sql.execute("UPDATE documents SET created_at=? WHERE workspace_id=? AND id=?", cutoff.plusNanos(1).toString(), actor.workspaceId(), fresh);
                return null;
            });
            assertEquals(0, service.expireDocuments(0, current));
            assertEquals(1, service.expireDocuments(1, current));
            assertThrows(ApiException.class, () -> service.document(actor, old));
            assertEquals("EXTRACTING", service.document(actor, boundary).get("status"));
            assertEquals("EXTRACTING", service.document(actor, fresh).get("status"));
            assertEquals(0, service.expireDocuments(1, current));
            try (var paths = Files.list(db.blobs)) { assertEquals(2, paths.count()); }
        }
    }

    @Test void requestEventsContainOnlyAllowlistedCategoriesAndServerCorrelationFields() {
        String requestId = "11111111-1111-4111-8111-111111111111";
        String event = RequestEvents.completed("sensitive-method@example.test", "/api/v1/reports/sensitive-document/pdf", 404, 12, requestId);
        JsonObject json = com.google.gson.JsonParser.parseString(event).getAsJsonObject();
        assertEquals(java.util.Set.of("event", "method", "routeCategory", "status", "durationMs", "requestId"), json.keySet());
        assertEquals("request_completed", json.get("event").getAsString());
        assertEquals("OTHER", json.get("method").getAsString());
        assertEquals("reports.pdf", json.get("routeCategory").getAsString());
        assertEquals(404, json.get("status").getAsInt()); assertEquals(12, json.get("durationMs").getAsInt());
        assertFalse(event.contains("sensitive-document")); assertFalse(event.contains("example.test"));
        String unknown = RequestEvents.completed("GET", "/not-found?token=upstream-secret", -1, -1, requestId);
        assertFalse(unknown.contains("upstream-secret")); assertFalse(unknown.contains("not-found"));
        JsonObject unmatched = com.google.gson.JsonParser.parseString(unknown).getAsJsonObject();
        assertEquals("unmatched", unmatched.get("routeCategory").getAsString());
        assertEquals(0, unmatched.get("status").getAsInt()); assertEquals(0, unmatched.get("durationMs").getAsInt());
    }

    @Test void reportsEscapeMarkupAndSpreadsheetFormulasWithoutChangingStoredEvidence() {
        JsonObject report = new JsonObject(), result = new JsonObject(), match = new JsonObject();
        report.addProperty("title", "<script>alert('title')</script>");
        report.addProperty("documentText", "<img src=x onerror=alert(1)>");
        match.addProperty("sourceTitle", "=HYPERLINK(\"https://invalid\")");
        match.addProperty("submittedPassage", "<script>alert('evidence')</script>");
        match.addProperty("sourcePassage", "@SUM(1,2)");
        JsonArray matches = new JsonArray(); matches.add(match); result.add("matches", matches); report.add("result", result);
        String original = report.toString();
        String html = Reports.html(report);
        assertFalse(html.contains("<script>")); assertFalse(html.contains("<img"));
        assertTrue(html.contains("&lt;script&gt;"));
        String csv = Reports.csv(report);
        assertTrue(csv.contains("\"'=HYPERLINK(")); assertTrue(csv.contains("\"'@SUM(1,2)\""));
        for (String value : new String[]{"=1+1", "+1+1", "-1+1", "@SUM(A1)", "  =1+1", "\t=1+1", "\r=1+1", "\uFEFF=1+1"})
            assertTrue(Reports.csvCell(value).startsWith("\"'"), value);
        assertEquals("\"a\"\"b\"", Reports.csvCell("a\"b"));
        assertEquals(original, report.toString());
    }

    @Test void databaseHasItsOwnMigrationAndExclusiveDataDirectoryLock() throws Exception {
        Path data = directory.resolve("data");
        try (ServerDatabase first = new ServerDatabase(data)) {
            assertTrue(Files.isRegularFile(data.resolve("server.sqlite3")));
            assertEquals(1L, first.transaction(sql -> sql.number("SELECT MAX(version) FROM schema_migrations")).longValue());
            assertThrows(Exception.class, () -> new ServerDatabase(data));
        }
        try (ServerDatabase reopened = new ServerDatabase(data)) {
            assertEquals(1L, reopened.transaction(sql -> sql.number("SELECT COUNT(*) FROM schema_migrations")).longValue());
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + data.resolve("server.sqlite3")); var st = connection.createStatement()) {
            st.executeUpdate("INSERT INTO schema_migrations(version,applied_at) VALUES(99,'future')");
        }
        assertThrows(Exception.class, () -> new ServerDatabase(data));
    }

    @Test void symlinkDataDirectoriesAndDatabaseFilesAreRejected() throws Exception {
        Path actual = Files.createDirectory(directory.resolve("actual"));
        Path linked = directory.resolve("linked"); Files.createSymbolicLink(linked, actual);
        assertThrows(Exception.class, () -> new ServerDatabase(linked));
        Path data = Files.createDirectory(directory.resolve("data"));
        Path other = Files.createFile(directory.resolve("other.sqlite3"));
        Files.createSymbolicLink(data.resolve("server.sqlite3"), other);
        assertThrows(Exception.class, () -> new ServerDatabase(data));
    }
}
