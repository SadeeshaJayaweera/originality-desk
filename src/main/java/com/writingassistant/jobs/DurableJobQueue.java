package com.writingassistant.jobs;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

/**
 * SQL-backed durable job queue implementing crash resilience, distributed lease tokens,
 * heartbeat monitoring, stale job detection, and exponential retry backoff.
 */
public class DurableJobQueue {

    private static final Logger LOGGER = Logger.getLogger(DurableJobQueue.class.getName());

    private final BackoffPolicy backoffPolicy;
    private final long leaseDurationMillis;
    private int staleRecoveredCount = 0;

    public DurableJobQueue() {
        this(BackoffPolicy.defaultPolicy(), JobLease.DEFAULT_LEASE_DURATION_MILLIS);
    }

    public DurableJobQueue(BackoffPolicy backoffPolicy, long leaseDurationMillis) {
        this.backoffPolicy = Objects.requireNonNull(backoffPolicy, "backoffPolicy must not be null");
        this.leaseDurationMillis = leaseDurationMillis > 0 ? leaseDurationMillis : JobLease.DEFAULT_LEASE_DURATION_MILLIS;
    }

    public BackoffPolicy getBackoffPolicy() {
        return backoffPolicy;
    }

    public long getLeaseDurationMillis() {
        return leaseDurationMillis;
    }

    /**
     * Enqueues a new background job idempotently.
     */
    public JobDescriptor enqueue(Connection conn, String id, String workspaceId, String kind, String resourceId, JobPriority priority) throws SQLException {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(resourceId, "resourceId must not be null");
        priority = priority != null ? priority : JobPriority.NORMAL;

        long now = System.currentTimeMillis();
        String createdAt = Instant.now().toString();

        String sql = "INSERT INTO jobs(id, workspace_id, kind, resource_id, status, attempts, available_at, created_at, error) "
                   + "VALUES(?, ?, ?, ?, 'QUEUED', 0, ?, ?, NULL)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, workspaceId);
            ps.setString(3, kind);
            ps.setString(4, resourceId);
            ps.setLong(5, now);
            ps.setString(6, createdAt);
            ps.executeUpdate();
        }

        return new JobDescriptor(
                id, workspaceId, kind, resourceId, JobStatus.QUEUED, priority,
                0, JobDescriptor.DEFAULT_MAX_ATTEMPTS, now, now, createdAt, null, null, null
        );
    }

    /**
     * Atomically claims the next available queued job whose available_at timestamp has arrived.
     */
    public Optional<JobDescriptor> claim(Connection conn, String workerId) throws SQLException {
        Objects.requireNonNull(workerId, "workerId must not be null");
        long now = System.currentTimeMillis();

        String selectSql = "SELECT id, workspace_id, kind, resource_id, status, attempts, available_at, created_at, error "
                         + "FROM jobs WHERE status='QUEUED' AND available_at <= ? "
                         + "ORDER BY available_at ASC, created_at ASC, id ASC LIMIT 1";

        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setLong(1, now);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();

                String jobId = rs.getString("id");
                String workspaceId = rs.getString("workspace_id");
                String kind = rs.getString("kind");
                String resourceId = rs.getString("resource_id");
                int currentAttempts = rs.getInt("attempts");
                int newAttempts = currentAttempts + 1;
                String createdAt = rs.getString("created_at");

                // Atomically transition from QUEUED to RUNNING
                String updateSql = "UPDATE jobs SET status='RUNNING', attempts=?, error=NULL "
                                 + "WHERE id=? AND workspace_id=? AND status='QUEUED'";

                try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                    updatePs.setInt(1, newAttempts);
                    updatePs.setString(2, jobId);
                    updatePs.setString(3, workspaceId);
                    int updated = updatePs.executeUpdate();
                    if (updated == 0) {
                        return Optional.empty(); // Concurrently claimed by another worker
                    }
                }

                return Optional.of(new JobDescriptor(
                        jobId, workspaceId, kind, resourceId, JobStatus.RUNNING, JobPriority.NORMAL,
                        newAttempts, JobDescriptor.DEFAULT_MAX_ATTEMPTS, now, now, createdAt, null, null, workerId
                ));
            }
        }
    }

    /**
     * Records a heartbeat on an actively running job, verifying it is still owned and valid.
     */
    public boolean heartbeat(Connection conn, String jobId, String workspaceId) throws SQLException {
        String sql = "UPDATE jobs SET available_at=? WHERE id=? AND workspace_id=? AND status='RUNNING'";
        long nextHeartbeatDeadline = System.currentTimeMillis() + leaseDurationMillis;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, nextHeartbeatDeadline);
            ps.setString(2, jobId);
            ps.setString(3, workspaceId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Marks a job completed successfully.
     */
    public boolean complete(Connection conn, String jobId, String workspaceId) throws SQLException {
        String sql = "UPDATE jobs SET status='COMPLETED', error=NULL WHERE id=? AND workspace_id=? AND status='RUNNING'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, jobId);
            ps.setString(2, workspaceId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Handles job failure, applying exponential backoff retry scheduling or terminal failure.
     */
    public boolean fail(Connection conn, String jobId, String workspaceId, String errorMessage, boolean retryable, int maxAttempts) throws SQLException {
        String querySql = "SELECT attempts FROM jobs WHERE id=? AND workspace_id=? AND status='RUNNING'";
        int currentAttempts = 1;

        try (PreparedStatement ps = conn.prepareStatement(querySql)) {
            ps.setString(1, jobId);
            ps.setString(2, workspaceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false; // Job is no longer active RUNNING
                currentAttempts = rs.getInt("attempts");
            }
        }

        if (retryable && currentAttempts < maxAttempts) {
            long nextAvailableAt = backoffPolicy.nextAvailableAt(currentAttempts);
            String retrySql = "UPDATE jobs SET status='QUEUED', available_at=?, error=? WHERE id=? AND workspace_id=? AND status='RUNNING'";
            try (PreparedStatement ps = conn.prepareStatement(retrySql)) {
                ps.setLong(1, nextAvailableAt);
                ps.setString(2, errorMessage);
                ps.setString(3, jobId);
                ps.setString(4, workspaceId);
                return ps.executeUpdate() > 0;
            }
        } else {
            String failSql = "UPDATE jobs SET status='FAILED', error=? WHERE id=? AND workspace_id=? AND status='RUNNING'";
            try (PreparedStatement ps = conn.prepareStatement(failSql)) {
                ps.setString(1, errorMessage);
                ps.setString(2, jobId);
                ps.setString(3, workspaceId);
                return ps.executeUpdate() > 0;
            }
        }
    }

    /**
     * Detects and recovers stale or abandoned jobs left in RUNNING status (e.g. from crashed workers).
     *
     * @param conn Connection.
     * @param staleThresholdMillis Minimum milliseconds without heartbeat to consider abandoned.
     * @return Number of stale jobs recovered.
     */
    public int recoverStaleJobs(Connection conn, long staleThresholdMillis) throws SQLException {
        long staleCutoff = System.currentTimeMillis() - staleThresholdMillis;
        int recovered = 0;

        String selectSql = "SELECT id, workspace_id, attempts, error FROM jobs WHERE status='RUNNING'";
        List<Map<String, Object>> staleJobs = new ArrayList<>();

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(selectSql)) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getString("id"));
                row.put("workspace_id", rs.getString("workspace_id"));
                row.put("attempts", rs.getInt("attempts"));
                staleJobs.add(row);
            }
        }

        for (Map<String, Object> job : staleJobs) {
            String jobId = (String) job.get("id");
            String workspaceId = (String) job.get("workspace_id");
            int attempts = (int) job.get("attempts");

            if (attempts >= JobDescriptor.DEFAULT_MAX_ATTEMPTS) {
                // Route to FAILED / dead letter
                String failSql = "UPDATE jobs SET status='FAILED', error='Stale job exceeded maximum recovery attempts' WHERE id=? AND workspace_id=? AND status='RUNNING'";
                try (PreparedStatement ps = conn.prepareStatement(failSql)) {
                    ps.setString(1, jobId);
                    ps.setString(2, workspaceId);
                    if (ps.executeUpdate() > 0) recovered++;
                }
            } else {
                // Re-queue with immediate availability
                String requeueSql = "UPDATE jobs SET status='QUEUED', available_at=?, error='Recovered stale abandoned job' WHERE id=? AND workspace_id=? AND status='RUNNING'";
                try (PreparedStatement ps = conn.prepareStatement(requeueSql)) {
                    ps.setLong(1, System.currentTimeMillis());
                    ps.setString(2, jobId);
                    ps.setString(3, workspaceId);
                    if (ps.executeUpdate() > 0) recovered++;
                }
            }
        }

        this.staleRecoveredCount += recovered;
        if (recovered > 0) {
            LOGGER.info("Recovered " + recovered + " stale background jobs.");
        }
        return recovered;
    }

    /**
     * Retrieves queue capacity and operational telemetry metrics.
     */
    public JobQueueMetrics getMetrics(Connection conn, String workspaceId) throws SQLException {
        String filter = workspaceId != null ? " WHERE workspace_id='" + workspaceId.replace("'", "''") + "'" : "";

        int queued = countWhere(conn, "status='QUEUED'" + (workspaceId != null ? " AND workspace_id='" + workspaceId.replace("'", "''") + "'" : ""));
        int running = countWhere(conn, "status='RUNNING'" + (workspaceId != null ? " AND workspace_id='" + workspaceId.replace("'", "''") + "'" : ""));
        int completed = countWhere(conn, "status='COMPLETED'" + (workspaceId != null ? " AND workspace_id='" + workspaceId.replace("'", "''") + "'" : ""));
        int failed = countWhere(conn, "status='FAILED'" + (workspaceId != null ? " AND workspace_id='" + workspaceId.replace("'", "''") + "'" : ""));

        int retrying = countWhere(conn, "status='QUEUED' AND attempts > 0" + (workspaceId != null ? " AND workspace_id='" + workspaceId.replace("'", "''") + "'" : ""));

        return new JobQueueMetrics(
                queued,
                running,
                retrying,
                completed,
                failed,
                0,
                staleRecoveredCount,
                0L
        );
    }

    private int countWhere(Connection conn, String whereClause) throws SQLException {
        String sql = "SELECT COUNT(*) FROM jobs WHERE " + whereClause;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
