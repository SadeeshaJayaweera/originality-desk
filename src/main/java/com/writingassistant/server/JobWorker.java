package com.writingassistant.server;

import com.google.gson.JsonObject;
import com.writingassistant.analysis.DocumentExtractor;
import com.writingassistant.analysis.EvidenceEngine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Durable, bounded work queue. Database locks are never held during extraction or comparison. */
final class JobWorker implements AutoCloseable {
    private static final int MAX_ATTEMPTS = 3;
    private final Service service;
    private final ServerConfig config;
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final ThreadPoolExecutor computation;
    private final Thread dispatcher;
        private long nextRetentionNanos;

    JobWorker(Service service, ServerConfig config) throws Exception {
        this.service = service; this.config = config;
        computation = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), runnable -> {
            Thread thread = new Thread(runnable, "desk-computation"); thread.setDaemon(true); return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
        recover();
        dispatcher = new Thread(this::run, "desk-jobs"); dispatcher.setDaemon(true);
    }
    void start() { dispatcher.start(); }
    boolean alive() { return dispatcher.isAlive() && !stopping.get(); }

    private void recover() throws Exception {
        service.db.transaction(sql -> {
            // Only this process owns the lock, so RUNNING means the previous process was interrupted.
            for (Map<String,Object> job : sql.rows("SELECT * FROM jobs WHERE status='RUNNING'")) {
                if (((Number) job.get("attempts")).intValue() >= MAX_ATTEMPTS)
                    terminal(sql, job, "FAILED", "Job interrupted too many times");
                else {
                    sql.execute("UPDATE jobs SET status='QUEUED',available_at=?,error='Recovered interrupted job' WHERE workspace_id=? AND id=?", System.currentTimeMillis(), job.get("workspace_id"), job.get("id"));
                    if (job.get("kind").equals("ANALYZE"))
                        sql.execute("UPDATE analyses SET status='QUEUED',error=NULL WHERE workspace_id=? AND id=? AND status='RUNNING'", job.get("workspace_id"), job.get("resource_id"));
                }
            }
            // A crash between writing a blob and committing the document may leave an orphan.
            Set<String> retained = new HashSet<>();
            for (Map<String,Object> row : sql.rows("SELECT storage_name FROM documents")) retained.add((String) row.get("storage_name"));
            try (var paths = Files.list(service.db.blobs)) {
                for (var path : paths.toList()) if (!retained.contains(path.getFileName().toString()) && !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    Files.deleteIfExists(path);
            }
            return null;
        });
    }
    private void run() {
        Map<String,Object> current = null;
        while (!stopping.get()) {
            try {
                if (current != null) {
                    fail(current, "Job persistence operation could not be completed", true);
                    current = null;
                }
                maintainRetention();
                // A timed-out uncooperative computation never permits unbounded replacement threads.
                if (computation.getActiveCount() != 0) { Thread.sleep(100); continue; }
                current = claim();
                if (current == null) { Thread.sleep(100); continue; }
                perform(current);
                current = null;
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            catch (Exception e) {
                System.err.println("desk-jobs: persistence operation failed (" + e.getClass().getSimpleName() + ")");
                try { Thread.sleep(1000); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            }
        }
    }
    private void maintainRetention() {
        if (config.retentionDays() == 0) return;
        long current = System.nanoTime();
        if (nextRetentionNanos != 0 && current - nextRetentionNanos < 0) return;
        nextRetentionNanos = current + Duration.ofMinutes(1).toNanos();
        try {
            if (service.expireDocuments(config.retentionDays(), Instant.now()) >= Service.RETENTION_BATCH)
                nextRetentionNanos = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        } catch (Exception e) {
            System.err.println("{\"event\":\"retention_cleanup_failed\"}");
        }
    }

    private Map<String,Object> claim() throws Exception {
        return service.db.transaction(sql -> {
            Map<String,Object> job = sql.one("SELECT * FROM jobs WHERE status='QUEUED' AND available_at<=? ORDER BY available_at,created_at,id LIMIT 1", System.currentTimeMillis());
            if (job == null) return null;
            sql.execute("UPDATE jobs SET status='RUNNING',attempts=attempts+1,error=NULL WHERE workspace_id=? AND id=? AND status='QUEUED'", job.get("workspace_id"), job.get("id"));
            job.put("attempts", ((Number) job.get("attempts")).intValue() + 1);
            if (job.get("kind").equals("ANALYZE"))
                sql.execute("UPDATE analyses SET status='RUNNING',error=NULL WHERE workspace_id=? AND id=? AND status='QUEUED'", job.get("workspace_id"), job.get("resource_id"));
            return job;
        });
    }
    private boolean active(Map<String,Object> job) throws Exception {
        return service.db.transaction(sql -> active(sql, job));
    }
    private boolean active(ServerDatabase.Sql sql, Map<String,Object> job) throws Exception {
        return sql.one("SELECT id FROM jobs WHERE workspace_id=? AND id=? AND status='RUNNING'", job.get("workspace_id"), job.get("id")) != null;
    }
    private void perform(Map<String,Object> job) throws Exception {
        Future<Object> future = computation.submit(() -> job.get("kind").equals("EXTRACT") ? extract(job) : analyze(job));
        long deadline = System.nanoTime() + config.jobTimeout().toNanos();
        try {
            while (true) {
                if (stopping.get()) { future.cancel(true); return; }
                maintainRetention();
                if (!active(job)) { future.cancel(true); return; }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new TimeoutException();
                try {
                    Object output = future.get(Math.min(100, Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining))), TimeUnit.MILLISECONDS);
                    publish(job, output); return;
                } catch (TimeoutException e) { if (System.nanoTime() >= deadline) throw e; }
            }
        } catch (InterruptedException e) { future.cancel(true); throw e; }
        catch (TimeoutException e) { future.cancel(true); fail(job, "Job exceeded its time limit", false); }
        catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CancellationException || cause instanceof InterruptedException) {
                fail(job, "Job interrupted", true);
            } else if (cause instanceof IOException || cause instanceof IllegalArgumentException || cause instanceof ApiException) {
                fail(job, job.get("kind").equals("EXTRACT") ? "Document extraction failed: invalid, unsupported, empty, or oversized content" : "Analysis exceeded evidence or text limits", false);
            } else fail(job, "Job could not be completed", true);
        } catch (Exception e) {
            future.cancel(true);
            fail(job, "Job persistence operation could not be completed", true);
        } finally {
            if (!future.isDone()) future.cancel(true);
            computation.purge();
        }
    }
    private DocumentExtractor.ExtractedDocument extract(Map<String,Object> job) throws Exception {
        Map<String,Object> doc = service.db.transaction(sql -> sql.one("SELECT filename,storage_name FROM documents WHERE workspace_id=? AND id=? AND status='EXTRACTING'", job.get("workspace_id"), job.get("resource_id")));
        if (doc == null) throw new CancellationException();
        byte[] bytes;
        try (var in = Files.newInputStream(service.blob((String) doc.get("storage_name")), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            bytes = in.readNBytes(Service.MAX_UPLOAD + 1);
        }
        if (bytes.length > Service.MAX_UPLOAD) throw new IOException("File too large");
        var extracted = new DocumentExtractor().extract((String) doc.get("filename"), bytes);
        if (extracted.text().isBlank() || extracted.text().length() > Service.MAX_TEXT) throw new IOException("Invalid extracted length");
        return extracted;
    }
    private record SourceIdentity(String id, String title, String type, String sha256) {}
    private record Input(String documentId, String title, String text, String profile, String createdAt,
                         String filename, int pageCount, String sha256, String submittedAt,
                         List<EvidenceEngine.Source> sources, List<SourceIdentity> sourceIdentities) {}
    private JsonObject analyze(Map<String,Object> job) throws Exception {
        Input input = service.db.transaction(sql -> {
            String workspace = (String) job.get("workspace_id"), analysisId = (String) job.get("resource_id");
            Map<String,Object> analysis = sql.one("SELECT document_id,title,profile,created_at FROM analyses WHERE workspace_id=? AND id=? AND status='RUNNING'", workspace, analysisId);
            if (analysis == null) throw new CancellationException();
            Map<String,Object> doc = sql.one("SELECT text,filename,page_count,sha256,created_at FROM documents WHERE workspace_id=? AND id=? AND status='READY'", workspace, analysis.get("document_id"));
            if (doc == null) throw new CancellationException();
            List<EvidenceEngine.Source> sources = new ArrayList<>();
            List<SourceIdentity> identities = new ArrayList<>();
            for (Map<String,Object> row : sql.rows("SELECT d.id,d.title,d.text,d.kind,d.sha256 FROM analysis_sources s JOIN documents d ON d.workspace_id=s.workspace_id AND d.id=s.document_id WHERE s.workspace_id=? AND s.analysis_id=? AND d.status='READY' ORDER BY d.id", workspace, analysisId)) {
                sources.add(new EvidenceEngine.Source((String) row.get("id"), (String) row.get("title"), (String) row.get("text"), (String) row.get("kind")));
                identities.add(new SourceIdentity((String) row.get("id"), (String) row.get("title"), (String) row.get("kind"), (String) row.get("sha256")));
            }
            return new Input((String) analysis.get("document_id"), (String) analysis.get("title"), (String) doc.get("text"),
                    (String) analysis.get("profile"), (String) analysis.get("created_at"), (String) doc.get("filename"),
                    Math.max(0, ((Number) doc.get("page_count")).intValue()), (String) doc.get("sha256"), (String) doc.get("created_at"), sources, identities);
        });
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        var result = new EvidenceEngine().analyze(input.documentId(), input.title(), input.text(), input.sources(), input.profile());
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        JsonObject snapshot = new JsonObject();
                snapshot.add("result", Service.JSON.toJsonTree(result));
        snapshot.addProperty("id", (String) job.get("resource_id"));
        snapshot.addProperty("analysisId", (String) job.get("resource_id"));
        snapshot.addProperty("documentId", input.documentId());
        snapshot.addProperty("title", input.title());
        snapshot.addProperty("createdAt", input.createdAt());
                snapshot.addProperty("profile", input.profile());
                snapshot.addProperty("sourceCount", input.sources().size());
                snapshot.addProperty("status", "COMPLETED");
        snapshot.addProperty("documentText", input.text());
                snapshot.addProperty("filename", input.filename());
                snapshot.addProperty("pageCount", input.pageCount());
                snapshot.addProperty("language", "und");
                snapshot.addProperty("sha256", input.sha256());
                snapshot.addProperty("submittedAt", input.submittedAt());
                snapshot.add("sources", Service.JSON.toJsonTree(input.sourceIdentities()));
        if (Service.JSON.toJson(snapshot).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 2 * 1024 * 1024)
            throw new IOException("Result limit");
        return snapshot;
    }
    private void publish(Map<String,Object> job, Object output) throws Exception {
        service.db.transaction(sql -> {
            // A cancellation or document deletion wins over a late computation, even across retries.
            if (!active(sql, job)) return null;
            if (job.get("kind").equals("EXTRACT")) {
                var doc = (DocumentExtractor.ExtractedDocument) output;
                int words = doc.text().strip().split("\\s+").length;
                sql.execute("UPDATE documents SET status='READY',text=?,word_count=?,page_count=?,error=NULL WHERE workspace_id=? AND id=? AND status='EXTRACTING'",
                        doc.text(), words, doc.pageCount(), job.get("workspace_id"), job.get("resource_id"));
            } else {
                String completedAt = Service.now();
                ((JsonObject) output).addProperty("completedAt", completedAt);
                String snapshot = Service.JSON.toJson(output);
                long size = snapshot.length();
                if (sql.number("SELECT COALESCE(SUM(LENGTH(result_json)),0) FROM analyses WHERE workspace_id=?", job.get("workspace_id")) + size > 64L * 1024 * 1024
                        || sql.number("SELECT COALESCE(SUM(LENGTH(result_json)),0) FROM analyses") + size > 1024L * 1024 * 1024) {
                    terminal(sql, job, "FAILED", "Report storage limit reached"); return null;
                }
                sql.execute("UPDATE analyses SET status='COMPLETED',result_json=?,document_text=NULL,completed_at=?,error=NULL WHERE workspace_id=? AND id=? AND status='RUNNING'",
                        snapshot, completedAt, job.get("workspace_id"), job.get("resource_id"));
            }
            sql.execute("UPDATE jobs SET status='COMPLETED',error=NULL WHERE workspace_id=? AND id=? AND status='RUNNING'", job.get("workspace_id"), job.get("id"));
            return null;
        });
    }
    private void fail(Map<String,Object> job, String error, boolean retry) throws Exception {
        service.db.transaction(sql -> {
            if (!active(sql, job)) return null;
            int attempts = ((Number) job.get("attempts")).intValue();
            if (retry && attempts < MAX_ATTEMPTS) {
                sql.execute("UPDATE jobs SET status='QUEUED',available_at=?,error=? WHERE workspace_id=? AND id=?", System.currentTimeMillis() + (1000L << (attempts - 1)), error, job.get("workspace_id"), job.get("id"));
                if (job.get("kind").equals("ANALYZE"))
                    sql.execute("UPDATE analyses SET status='QUEUED',error=? WHERE workspace_id=? AND id=? AND status='RUNNING'", error, job.get("workspace_id"), job.get("resource_id"));
            } else terminal(sql, job, "FAILED", error);
            return null;
        });
    }
    private void terminal(ServerDatabase.Sql sql, Map<String,Object> job, String status, String error) throws Exception {
        sql.execute("UPDATE jobs SET status=?,error=? WHERE workspace_id=? AND id=?", status, error, job.get("workspace_id"), job.get("id"));
        if (job.get("kind").equals("EXTRACT"))
            sql.execute("UPDATE documents SET status='FAILED',error=?,text=NULL WHERE workspace_id=? AND id=? AND status='EXTRACTING'", error, job.get("workspace_id"), job.get("resource_id"));
        else sql.execute("UPDATE analyses SET status=?,error=?,completed_at=?,result_json=NULL,document_text=NULL WHERE workspace_id=? AND id=? AND status IN ('QUEUED','RUNNING')", status, error, Service.now(), job.get("workspace_id"), job.get("resource_id"));
    }
    @Override public void close() throws InterruptedException {
        stopping.set(true); dispatcher.interrupt(); computation.shutdownNow(); dispatcher.join(5000);
        computation.awaitTermination(2, TimeUnit.SECONDS);
    }
}
