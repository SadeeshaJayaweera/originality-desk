package com.writingassistant.server;

import com.google.gson.*;
import com.writingassistant.analysis.DocumentExtractor;
import com.writingassistant.analysis.EvidenceEngine;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

final class Service {
    static final Gson JSON = new GsonBuilder().serializeNulls().create();
    static final int MAX_UPLOAD = 10 * 1024 * 1024;
    static final int MAX_TEXT = DocumentExtractor.MAX_CHARACTERS;
    static final int MAX_SOURCE_TEXT = EvidenceEngine.MAX_CORPUS_CHARACTERS;
    static final long SESSION_MILLIS = 12 * 60 * 60 * 1000L;
    private static final String DOCUMENT_COLUMNS = "id,title,filename,kind,status,error,word_count AS wordCount,page_count AS pageCount,bytes,created_at AS createdAt";
    private static final String ANALYSIS_COLUMNS = "id,document_id AS documentId,title,status,profile,created_at AS createdAt,completed_at AS completedAt,error,source_count AS sourceCount";
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    final ServerDatabase db;

    record Actor(String userId, String workspaceId, String role) {
        void admin() {
            if (!"INSTITUTION_ADMIN".equals(role)) throw new ApiException(403, "FORBIDDEN", "Administrator role required");
        }
        void reviewer() {
            if (!Set.of("INSTITUTION_ADMIN", "REVIEWER").contains(role))
                throw new ApiException(403, "FORBIDDEN", "Reviewer role required");
        }
    }
    record Authentication(String token, Map<String,Object> body) {}
    Service(ServerDatabase db) { this.db = db; }
    static String now() { return Instant.now().toString(); }
    static String id() { return UUID.randomUUID().toString(); }
    static String string(JsonObject input, String field, int max) {
        JsonElement value = input.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw ApiException.bad("Expected string: " + field);
        String result = value.getAsString();
        if (result.isBlank() || result.length() > max || result.indexOf('\0') >= 0)
            throw ApiException.bad("Invalid " + field);
        return result;
    }
    static int integer(JsonObject input, String field) {
        JsonElement value = input.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.getAsString().matches("[0-9]{1,8}")) throw ApiException.bad("Invalid " + field);
        return value.getAsInt();
    }
    static String email(JsonObject input) {
        String email = string(input, "email", 254).strip().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches()) throw ApiException.bad("Invalid email");
        return email;
    }

    Authentication register(JsonObject input) throws Exception {
        String name = string(input, "name", 120).strip();
        String email = email(input), password = string(input, "password", 1024);
        String workspace = string(input, "workspaceName", 120).strip();
        if (password.length() < 12) throw ApiException.bad("Password must contain at least 12 characters");
        String hash = Security.password(password);
        return db.transaction(sql -> {
            if (sql.one("SELECT id FROM users WHERE email=?", email) != null)
                throw ApiException.conflict("Registration could not be completed with that email");
            if (sql.number("SELECT COUNT(*) FROM workspaces") >= 1000) throw ApiException.limit("Workspace limit reached");
            String userId = id(), workspaceId = id();
            sql.execute("INSERT INTO workspaces(id,name,created_at) VALUES(?,?,?)", workspaceId, workspace, now());
            sql.execute("INSERT INTO users(id,workspace_id,name,email,role,password_hash,created_at) VALUES(?,?,?,?,?,?,?)",
                    userId, workspaceId, name, email, "INSTITUTION_ADMIN", hash, now());
            Actor actor = new Actor(userId, workspaceId, "INSTITUTION_ADMIN");
            audit(sql, actor, "REGISTER", workspaceId);
            return new Authentication(session(sql, actor), identity(sql, actor));
        });
    }

    Authentication login(JsonObject input) throws Exception {
        String email = email(input), password = string(input, "password", 1024);
        Map<String,Object> user = db.transaction(sql -> sql.one("SELECT id,workspace_id,role,password_hash FROM users WHERE email=?", email));
        if (!Security.verify(password, user == null ? null : (String) user.get("password_hash")))
            throw new ApiException(401, "INVALID_CREDENTIALS", "Invalid email or password");
        Actor actor = new Actor((String) user.get("id"), (String) user.get("workspace_id"), (String) user.get("role"));
        return db.transaction(sql -> {
            audit(sql, actor, "LOGIN", actor.userId());
            return new Authentication(session(sql, actor), identity(sql, actor));
        });
    }
    private String session(ServerDatabase.Sql sql, Actor actor) throws Exception {
        long current = System.currentTimeMillis();
        sql.execute("DELETE FROM sessions WHERE expires_at<=?", current);
        sql.execute("DELETE FROM sessions WHERE workspace_id=? AND user_id=? AND token_hash IN (SELECT token_hash FROM sessions WHERE workspace_id=? AND user_id=? ORDER BY created_at DESC LIMIT -1 OFFSET 9)",
                actor.workspaceId(), actor.userId(), actor.workspaceId(), actor.userId());
        if (sql.number("SELECT COUNT(*) FROM sessions") >= 10_000) throw ApiException.limit("Session limit reached");
        String token = Security.token();
        sql.execute("INSERT INTO sessions(token_hash,workspace_id,user_id,expires_at,created_at) VALUES(?,?,?,?,?)",
                Security.hash(token), actor.workspaceId(), actor.userId(), current + SESSION_MILLIS, current);
        return token;
    }
    Actor authenticate(String token) throws Exception {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw unauthorized();
        Actor actor = db.transaction(sql -> {
            Map<String,Object> row = sql.one("SELECT u.id,u.workspace_id,u.role FROM sessions s JOIN users u ON u.workspace_id=s.workspace_id AND u.id=s.user_id WHERE s.token_hash=? AND s.expires_at>?", Security.hash(token), System.currentTimeMillis());
            return row == null ? null : new Actor((String) row.get("id"), (String) row.get("workspace_id"), (String) row.get("role"));
        });
        if (actor == null) throw unauthorized();
        return actor;
    }
    private static ApiException unauthorized() { return new ApiException(401, "UNAUTHENTICATED", "Authentication required"); }
    void logout(Actor actor, String token) throws Exception {
        db.transaction(sql -> {
            sql.execute("DELETE FROM sessions WHERE workspace_id=? AND user_id=? AND token_hash=?", actor.workspaceId(), actor.userId(), Security.hash(token));
            audit(sql, actor, "LOGOUT", actor.userId()); return null;
        });
    }
    Map<String,Object> me(Actor actor) throws Exception { return db.transaction(sql -> identity(sql, actor)); }
    private Map<String,Object> identity(ServerDatabase.Sql sql, Actor actor) throws Exception {
        return Map.of("user", sql.one("SELECT id,name,email,role FROM users WHERE workspace_id=? AND id=?", actor.workspaceId(), actor.userId()),
                "workspace", sql.one("SELECT id,name FROM workspaces WHERE id=?", actor.workspaceId()));
    }

    Map<String,Object> upload(Actor actor, String filename, String kind, String mime, byte[] bytes) throws Exception {
        validateUpload(filename, mime, bytes);
        if (!Set.of("REFERENCE", "SUBMISSION").contains(kind)) throw ApiException.bad("Invalid document kind");
        String hash = Security.hash(bytes);
        String documentId = id(), storageName = Security.token();
        Path path = db.blobs.resolve(storageName);
        try {
            return db.transaction(sql -> {
                String workspace = actor.workspaceId();
                if (sql.one("SELECT id FROM documents WHERE workspace_id=? AND kind=? AND sha256=?", workspace, kind, hash) != null)
                    throw new ApiException(409, "DUPLICATE_DOCUMENT", "This document already exists in this workspace and kind");
                if (sql.number("SELECT COUNT(*) FROM documents WHERE workspace_id=?", workspace) >= 2000
                        || sql.number("SELECT COUNT(*) FROM documents") >= 10_000)
                    throw ApiException.limit("Document limit reached");
                if (sql.number("SELECT COALESCE(SUM(bytes),0) FROM documents WHERE workspace_id=?", workspace) + bytes.length > 256L * 1024 * 1024
                        || sql.number("SELECT COALESCE(SUM(bytes),0) FROM documents") + bytes.length > 2L * 1024 * 1024 * 1024)
                    throw new ApiException(413, "STORAGE_LIMIT", "Document storage limit reached");
                checkJobCapacity(sql, workspace);
                Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                ServerDatabase.privatePermissions(path, "rw-------");
                sql.execute("INSERT INTO documents(id,workspace_id,title,filename,kind,status,bytes,created_at,storage_name,sha256) VALUES(?,?,?,?,?,'EXTRACTING',?,?,?,?)",
                        documentId, workspace, filename, filename, kind, bytes.length, now(), storageName, hash);
                enqueue(sql, workspace, "EXTRACT", documentId);
                audit(sql, actor, "DOCUMENT_UPLOADED", documentId);
                return document(sql, workspace, documentId);
            });
        } catch (Exception e) {
            try { Files.deleteIfExists(path); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
    }

    private static void validateUpload(String filename, String mime, byte[] bytes) throws IOException {
        if (filename == null || filename.isBlank() || filename.length() > 200 || filename.contains("/") || filename.contains("\\")
                || filename.equals(".") || filename.equals("..") || filename.codePoints().anyMatch(c -> Character.isISOControl(c)))
            throw ApiException.bad("Invalid filename");
        if (bytes.length == 0) throw ApiException.bad("Document is empty");
        if (bytes.length > MAX_UPLOAD) throw new ApiException(413, "UPLOAD_TOO_LARGE", "Upload limit is 10 MiB");
        String lower = filename.toLowerCase(Locale.ROOT);
        String type = mime == null ? "application/octet-stream" : mime.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        boolean generic = type.equals("application/octet-stream");
        if (lower.endsWith(".txt")) {
            if (!generic && !type.equals("text/plain")) throw unsupported();
            try {
                String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
                if (text.startsWith("%PDF-") || text.startsWith("{\\rtf")
                        || text.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t' && c != '\f')) throw unsupported();
            } catch (CharacterCodingException e) { throw unsupported(); }
        } else if (lower.endsWith(".pdf")) {
            if ((!generic && !type.equals("application/pdf")) || bytes.length < 5
                    || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) throw unsupported();
        } else if (lower.endsWith(".docx")) {
            if ((!generic && !type.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    || bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K' || bytes[2] != 3 || bytes[3] != 4) throw unsupported();
            long expanded = 0; int entries = 0; boolean document = false, types = false;
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                byte[] buffer = new byte[8192];
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    if (++entries > 2000) throw ApiException.bad("DOCX archive has too many entries");
                    document |= entry.getName().equals("word/document.xml");
                    types |= entry.getName().equals("[Content_Types].xml");
                    for (int count; (count = zip.read(buffer)) != -1;) {
                        expanded += count;
                        if (expanded > 20L * 1024 * 1024) throw ApiException.bad("DOCX expanded size exceeds limit");
                    }
                }
            } catch (IOException e) { throw unsupported(); }
            if (!document || !types) throw unsupported();
        } else throw unsupported();
    }
    private static ApiException unsupported() { return new ApiException(415, "UNSUPPORTED_DOCUMENT", "Expected UTF-8 TXT, PDF, or DOCX with matching content type and signature"); }

    Map<String,Object> documents(Actor actor, String kind, int page, int size) throws Exception {
        if (kind != null && !Set.of("REFERENCE", "SUBMISSION").contains(kind)) throw ApiException.bad("Invalid document kind");
        return db.transaction(sql -> {
            String filter = kind == null ? "workspace_id=?" : "workspace_id=? AND kind=?";
            Object[] values = kind == null ? new Object[]{actor.workspaceId()} : new Object[]{actor.workspaceId(), kind};
            Object[] paged = Arrays.copyOf(values, values.length + 2);
            paged[values.length] = size; paged[values.length + 1] = (long) (page - 1) * size;
            return envelope(sql.rows("SELECT " + DOCUMENT_COLUMNS + " FROM documents WHERE " + filter + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?", paged),
                    sql.number("SELECT COUNT(*) FROM documents WHERE " + filter, values), page, size);
        });
    }
    Map<String,Object> document(Actor actor, String id) throws Exception { return db.transaction(sql -> document(sql, actor.workspaceId(), id)); }
    private Map<String,Object> document(ServerDatabase.Sql sql, String workspace, String id) throws Exception {
        Map<String,Object> result = sql.one("SELECT " + DOCUMENT_COLUMNS + " FROM documents WHERE workspace_id=? AND id=?", workspace, id);
        if (result == null) throw ApiException.missing();
        return result;
    }
    void deleteDocument(Actor actor, String documentId) throws Exception {
        db.transaction(sql -> deleteDocument(sql, actor, documentId, "DOCUMENT_DELETED", null));
        db.checkpoint();
    }

    private boolean deleteDocument(ServerDatabase.Sql sql, Actor actor, String documentId,
                                   String action, Instant cutoff) throws Exception {
        Map<String,Object> doc = sql.one("SELECT storage_name,created_at FROM documents WHERE workspace_id=? AND id=?", actor.workspaceId(), documentId);
        if (doc == null) {
            if (cutoff == null) throw ApiException.missing();
            return false;
        }
        if (cutoff != null && !Instant.parse((String) doc.get("created_at")).isBefore(cutoff)) return false;
        // Remove whole dependent snapshots, not just source links: snapshots contain source passages.
        List<Map<String,Object>> dependent = sql.rows("SELECT id FROM analyses WHERE workspace_id=? AND (document_id=? OR id IN (SELECT analysis_id FROM analysis_sources WHERE workspace_id=? AND document_id=?))",
                actor.workspaceId(), documentId, actor.workspaceId(), documentId);
        for (Map<String,Object> row : dependent) {
            sql.execute("DELETE FROM jobs WHERE workspace_id=? AND kind='ANALYZE' AND resource_id=?", actor.workspaceId(), row.get("id"));
            sql.execute("DELETE FROM analyses WHERE workspace_id=? AND id=?", actor.workspaceId(), row.get("id"));
        }
        sql.execute("DELETE FROM jobs WHERE workspace_id=? AND kind='EXTRACT' AND resource_id=?", actor.workspaceId(), documentId);
        Files.deleteIfExists(blob((String) doc.get("storage_name")));
        sql.execute("DELETE FROM documents WHERE workspace_id=? AND id=?", actor.workspaceId(), documentId);
        audit(sql, actor, action, documentId);
        return true;
    }

    static final int RETENTION_BATCH = 100;
    int expireDocuments(int retentionDays, Instant currentTime) throws Exception {
        if (retentionDays < 0 || retentionDays > 3650) throw new IllegalArgumentException("Invalid retention period");
        if (retentionDays == 0) return 0;
        Instant cutoff = currentTime.minus(java.time.Duration.ofDays(retentionDays));
        // Internal maintenance enumerates the bounded document table; each deletion rechecks tenant and age.
        // Parse Instants rather than compare variable-precision timestamp strings or round to SQL milliseconds.
        List<Map<String,Object>> candidates = db.transaction(sql -> sql.rows("SELECT id,workspace_id,created_at FROM documents ORDER BY created_at,id"));
        int deleted = 0;
        for (Map<String,Object> candidate : candidates) {
            if (!Instant.parse((String) candidate.get("created_at")).isBefore(cutoff)) continue;
            Actor system = new Actor("SYSTEM", (String) candidate.get("workspace_id"), "SYSTEM");
            if (db.transaction(sql -> deleteDocument(sql, system, (String) candidate.get("id"), "DOCUMENT_EXPIRED", cutoff))) deleted++;
            if (deleted >= RETENTION_BATCH) break;
        }
        if (deleted > 0) db.checkpoint();
        return deleted;
    }
    Path blob(String name) throws IOException {
        if (name == null || !name.matches("[A-Za-z0-9_-]{43}")) throw new IOException("Invalid stored path");
        Path path = db.blobs.resolve(name);
        if (Files.isSymbolicLink(path)) throw new IOException("Unsafe stored path");
        return path;
    }

    Map<String,Object> createAnalysis(Actor actor, JsonObject input, String key) throws Exception {
        String documentId = string(input, "documentId", 100), profile = string(input, "profile", 30);
        if (!Set.of("QUICK", "STANDARD", "REFERENCE_AWARE").contains(profile)) throw ApiException.bad("Invalid profile");
        if (key != null && (!key.matches("[\\x21-\\x7e]{1,128}"))) throw ApiException.bad("Invalid Idempotency-Key");
        boolean automatic = !input.has("sourceIds");
        TreeSet<String> requested = new TreeSet<>();
        if (!automatic) {
            JsonElement sources = input.get("sourceIds");
            if (!sources.isJsonArray() || sources.getAsJsonArray().size() > 50) throw ApiException.bad("sourceIds must be an array of at most 50 IDs");
            for (JsonElement item : sources.getAsJsonArray()) {
                if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString() || item.getAsString().length() > 100 || item.getAsString().isBlank())
                    throw ApiException.bad("Invalid source ID");
                requested.add(item.getAsString());
            }
        }
        String requestHash = Security.hash(JSON.toJson(List.of(documentId, profile, automatic, requested)));
        return db.transaction(sql -> {
            String workspace = actor.workspaceId();
            if (key != null) {
                Map<String,Object> existing = sql.one("SELECT id,request_hash FROM analyses WHERE workspace_id=? AND idempotency_key=?", workspace, key);
                if (existing != null) {
                    if (!requestHash.equals(existing.get("request_hash"))) throw ApiException.conflict("Idempotency-Key was used for a different request");
                    return analysis(sql, workspace, (String) existing.get("id"), true);
                }
            }
            Map<String,Object> doc = sql.one("SELECT title,status FROM documents WHERE workspace_id=? AND id=?", workspace, documentId);
            if (doc == null) throw ApiException.missing();
            if (!"READY".equals(doc.get("status"))) throw ApiException.conflict("Document is not ready");
            if (automatic) {
                for (Map<String,Object> row : sql.rows("SELECT id FROM documents WHERE workspace_id=? AND kind='REFERENCE' AND status='READY' AND id<>? ORDER BY id LIMIT 51", workspace, documentId))
                    requested.add((String) row.get("id"));
                if (requested.size() > 50) throw ApiException.bad("More than 50 references available; select sourceIds explicitly");
            }
            long totalText = 0;
            for (String source : requested) {
                if (source.equals(documentId)) throw ApiException.bad("Document cannot be its own source");
                Map<String,Object> row = sql.one("SELECT status,LENGTH(text) AS length FROM documents WHERE workspace_id=? AND id=?", workspace, source);
                if (row == null) throw ApiException.missing();
                if (!"READY".equals(row.get("status"))) throw ApiException.conflict("A source is not ready");
                totalText += ((Number) row.get("length")).longValue();
            }
            if (totalText > MAX_SOURCE_TEXT) throw new ApiException(413, "ANALYSIS_TOO_LARGE", "Source corpus exceeds 1,000,000 UTF-16 characters");
            if (sql.number("SELECT COUNT(*) FROM analyses WHERE workspace_id=?", workspace) >= 2000
                    || sql.number("SELECT COUNT(*) FROM analyses") >= 20_000) throw ApiException.limit("Analysis limit reached");
            checkJobCapacity(sql, workspace);
            String analysisId = id();
            sql.execute("INSERT INTO analyses(id,workspace_id,document_id,title,status,profile,created_at,source_count,idempotency_key,request_hash) VALUES(?,?,?,?,'QUEUED',?,?,?,?,?)",
                    analysisId, workspace, documentId, doc.get("title"), profile, now(), requested.size(), key, requestHash);
            for (String source : requested)
                sql.execute("INSERT INTO analysis_sources(workspace_id,analysis_id,document_id) VALUES(?,?,?)", workspace, analysisId, source);
            enqueue(sql, workspace, "ANALYZE", analysisId);
            audit(sql, actor, "ANALYSIS_CREATED", analysisId);
            return analysis(sql, workspace, analysisId, false);
        });
    }
    Map<String,Object> analyses(Actor actor, int page, int size) throws Exception {
        return db.transaction(sql -> envelope(sql.rows("SELECT " + ANALYSIS_COLUMNS + " FROM analyses WHERE workspace_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?", actor.workspaceId(), size, (long) (page - 1) * size),
                sql.number("SELECT COUNT(*) FROM analyses WHERE workspace_id=?", actor.workspaceId()), page, size));
    }
    Map<String,Object> analysis(Actor actor, String analysisId, boolean result) throws Exception {
        return db.transaction(sql -> analysis(sql, actor.workspaceId(), analysisId, result));
    }
    private Map<String,Object> analysis(ServerDatabase.Sql sql, String workspace, String analysisId, boolean result) throws Exception {
        Map<String,Object> row = sql.one("SELECT " + ANALYSIS_COLUMNS + (result ? ",result_json" : "") + " FROM analyses WHERE workspace_id=? AND id=?", workspace, analysisId);
        if (row == null) throw ApiException.missing();
        if (result) {
            String snapshot = (String) row.remove("result_json");
            if (snapshot != null) row.put("result", JsonParser.parseString(snapshot).getAsJsonObject().get("result"));
        }
        return row;
    }
    Map<String,Object> cancel(Actor actor, String analysisId) throws Exception {
        return db.transaction(sql -> {
            Map<String,Object> row = analysis(sql, actor.workspaceId(), analysisId, false);
            if (Set.of("QUEUED", "RUNNING").contains(row.get("status"))) {
                sql.execute("UPDATE analyses SET status='CANCELLED',completed_at=?,error=NULL WHERE workspace_id=? AND id=?", now(), actor.workspaceId(), analysisId);
                sql.execute("UPDATE jobs SET status='CANCELLED',error=NULL WHERE workspace_id=? AND kind='ANALYZE' AND resource_id=? AND status IN ('QUEUED','RUNNING')", actor.workspaceId(), analysisId);
                audit(sql, actor, "ANALYSIS_CANCELLED", analysisId);
            }
            return analysis(sql, actor.workspaceId(), analysisId, false);
        });
    }
    JsonObject report(Actor actor, String analysisId) throws Exception {
        return db.transaction(sql -> {
            Map<String,Object> row = sql.one("SELECT status,result_json FROM analyses WHERE workspace_id=? AND id=?", actor.workspaceId(), analysisId);
            if (row == null) throw ApiException.missing();
            if (!"COMPLETED".equals(row.get("status")) || row.get("result_json") == null) throw ApiException.conflict("Report is not available");
            return JsonParser.parseString((String) row.get("result_json")).getAsJsonObject();
        });
    }
    Map<String,Object> review(Actor actor, String analysisId, JsonObject input) throws Exception {
        actor.reviewer();
        int index = integer(input, "matchIndex");
        String decision = string(input, "decision", 32);
        if (!decision.matches("[A-Z_]{1,32}")) throw ApiException.bad("Invalid decision");
        JsonElement noteElement = input.get("note");
        if (noteElement == null || !noteElement.isJsonPrimitive() || !noteElement.getAsJsonPrimitive().isString()
                || noteElement.getAsString().length() > 4000 || noteElement.getAsString().indexOf('\0') >= 0) throw ApiException.bad("Invalid note");
        String note = noteElement.getAsString();
        return db.transaction(sql -> {
            Map<String,Object> row = sql.one("SELECT status,result_json FROM analyses WHERE workspace_id=? AND id=?", actor.workspaceId(), analysisId);
            if (row == null) throw ApiException.missing();
            if (!"COMPLETED".equals(row.get("status"))) throw ApiException.conflict("Analysis is not complete");
            JsonObject report = JsonParser.parseString((String) row.get("result_json")).getAsJsonObject();
            if (index >= report.getAsJsonObject("result").getAsJsonArray("matches").size()) throw ApiException.bad("Invalid matchIndex");
                        if (sql.number("SELECT COUNT(*) FROM reviews WHERE workspace_id=? AND analysis_id=?", actor.workspaceId(), analysisId) >= 1000
                                || sql.number("SELECT COUNT(*) FROM reviews WHERE workspace_id=?", actor.workspaceId()) >= 10_000)
                            throw ApiException.limit("Reviewer note limit reached");
            String reviewId = id();
            sql.execute("INSERT INTO reviews(id,workspace_id,analysis_id,user_id,match_index,decision,note,created_at) VALUES(?,?,?,?,?,?,?,?)",
                    reviewId, actor.workspaceId(), analysisId, actor.userId(), index, decision, note, now());
            audit(sql, actor, "REVIEW_CREATED", analysisId);
            return sql.one("SELECT id,analysis_id AS analysisId,user_id AS userId,match_index AS matchIndex,decision,note,created_at AS createdAt FROM reviews WHERE workspace_id=? AND id=?", actor.workspaceId(), reviewId);
        });
    }
    Map<String,Object> reviews(Actor actor, String analysisId, int page, int size) throws Exception {
        actor.reviewer();
        return db.transaction(sql -> {
            analysis(sql, actor.workspaceId(), analysisId, false);
            return envelope(sql.rows("SELECT id,analysis_id AS analysisId,user_id AS userId,match_index AS matchIndex,decision,note,created_at AS createdAt FROM reviews WHERE workspace_id=? AND analysis_id=? ORDER BY created_at,id LIMIT ? OFFSET ?",
                    actor.workspaceId(), analysisId, size, (long) (page - 1) * size),
                    sql.number("SELECT COUNT(*) FROM reviews WHERE workspace_id=? AND analysis_id=?", actor.workspaceId(), analysisId), page, size);
        });
    }

    Map<String,Object> dashboard(Actor actor) throws Exception {
        return db.transaction(sql -> {
            String workspace = actor.workspaceId();
            Map<String,Object> result = new LinkedHashMap<>();
            result.put("totalDocuments", sql.number("SELECT COUNT(*) FROM documents WHERE workspace_id=?", workspace));
            result.put("storageBytes", sql.number("SELECT COALESCE(SUM(bytes),0) FROM documents WHERE workspace_id=?", workspace));
            result.put("completedAnalyses", sql.number("SELECT COUNT(*) FROM analyses WHERE workspace_id=? AND status='COMPLETED'", workspace));
            result.put("pendingAnalyses", sql.number("SELECT COUNT(*) FROM analyses WHERE workspace_id=? AND status IN ('QUEUED','RUNNING')", workspace));
            result.put("failedAnalyses", sql.number("SELECT COUNT(*) FROM analyses WHERE workspace_id=? AND status='FAILED'", workspace));
            result.put("recentDocuments", sql.rows("SELECT " + DOCUMENT_COLUMNS + " FROM documents WHERE workspace_id=? ORDER BY created_at DESC,id DESC LIMIT 10", workspace));
            result.put("recentAnalyses", sql.rows("SELECT " + ANALYSIS_COLUMNS + " FROM analyses WHERE workspace_id=? ORDER BY created_at DESC,id DESC LIMIT 10", workspace));
            result.put("activity", sql.rows("SELECT id,action,resource_id AS resourceId,created_at AS createdAt FROM audit_logs WHERE workspace_id=? ORDER BY id DESC LIMIT 20", workspace));
            return result;
        });
    }
    Map<String,Object> adminList(Actor actor, String type, int page, int size) throws Exception {
        actor.admin();
        return db.transaction(sql -> {
            boolean jobs = type.equals("jobs");
            String table = jobs ? "jobs" : "audit_logs";
            String columns = jobs ? "id,kind,resource_id AS resourceId,status,attempts,available_at AS availableAt,created_at AS createdAt,error"
                    : "id,user_id AS userId,action,resource_id AS resourceId,created_at AS createdAt";
            String order = jobs ? "created_at DESC,id DESC" : "id DESC";
            return envelope(sql.rows("SELECT " + columns + " FROM " + table + " WHERE workspace_id=? ORDER BY " + order + " LIMIT ? OFFSET ?", actor.workspaceId(), size, (long) (page - 1) * size),
                    sql.number("SELECT COUNT(*) FROM " + table + " WHERE workspace_id=?", actor.workspaceId()), page, size);
        });
    }
    Map<String,Object> adminHealth(Actor actor, boolean workerAlive) throws Exception {
        actor.admin();
        return db.transaction(sql -> Map.of("status", workerAlive ? "UP" : "DEGRADED", "workerAlive", workerAlive,
                "queuedJobs", sql.number("SELECT COUNT(*) FROM jobs WHERE workspace_id=? AND status='QUEUED'", actor.workspaceId()),
                "runningJobs", sql.number("SELECT COUNT(*) FROM jobs WHERE workspace_id=? AND status='RUNNING'", actor.workspaceId()),
                "failedJobs", sql.number("SELECT COUNT(*) FROM jobs WHERE workspace_id=? AND status='FAILED'", actor.workspaceId())));
    }
    boolean ready() { try { return db.transaction(sql -> sql.number("SELECT 1")) == 1; } catch (Exception e) { return false; } }
    static Map<String,Object> envelope(List<Map<String,Object>> items, long total, int page, int size) {
        return Map.of("items", items, "total", total, "page", page, "size", size);
    }
    static void checkJobCapacity(ServerDatabase.Sql sql, String workspace) throws Exception {
        if (sql.number("SELECT COUNT(*) FROM jobs WHERE workspace_id=? AND status IN ('QUEUED','RUNNING')", workspace) >= 100
                || sql.number("SELECT COUNT(*) FROM jobs WHERE status IN ('QUEUED','RUNNING')") >= 200)
            throw ApiException.limit("Job queue is full");
    }
    static void enqueue(ServerDatabase.Sql sql, String workspace, String kind, String resourceId) throws Exception {
        sql.execute("INSERT INTO jobs(id,workspace_id,kind,resource_id,status,available_at,created_at) VALUES(?,?,?,?,'QUEUED',?,?)", id(), workspace, kind, resourceId, System.currentTimeMillis(), now());
    }
    static void audit(ServerDatabase.Sql sql, Actor actor, String action, String resourceId) throws Exception {
        sql.execute("INSERT INTO audit_logs(workspace_id,user_id,action,resource_id,created_at) VALUES(?,?,?,?,?)", actor.workspaceId(), actor.userId(), action, resourceId, now());
        sql.execute("DELETE FROM audit_logs WHERE workspace_id=? AND id NOT IN (SELECT id FROM audit_logs WHERE workspace_id=? ORDER BY id DESC LIMIT 2000)", actor.workspaceId(), actor.workspaceId());
    }
}
