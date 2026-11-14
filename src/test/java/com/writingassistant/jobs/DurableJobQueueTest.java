package com.writingassistant.jobs;

import com.writingassistant.persistence.migration.DatabaseMigrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DurableJobQueueTest {

    @Test
    @DisplayName("BackoffPolicy calculates exponential delays with upper bound caps")
    void backoffPolicyCalculations() {
        BackoffPolicy policy = new BackoffPolicy(1000L, 10_000L, 2.0);

        assertEquals(0L, policy.computeDelay(0));

        long delay1 = policy.computeDelay(1); // ~1000ms
        assertTrue(delay1 >= 1000L && delay1 <= 1200L, "Attempt 1 delay: " + delay1);

        long delay2 = policy.computeDelay(2); // ~2000ms
        assertTrue(delay2 >= 2000L && delay2 <= 2400L, "Attempt 2 delay: " + delay2);

        long delay3 = policy.computeDelay(3); // ~4000ms
        assertTrue(delay3 >= 4000L && delay3 <= 4800L, "Attempt 3 delay: " + delay3);

        // Cap adherence
        long delayHigh = policy.computeDelay(10);
        assertTrue(delayHigh <= 10_000L, "Delay must never exceed maxDelayMillis: " + delayHigh);
    }

    @Test
    @DisplayName("JobLease tracks acquisition, heartbeat renewal, and expiration")
    void jobLeaseLifecycle() {
        JobLease lease = JobLease.acquire("worker-alpha", 1000L);
        assertEquals("worker-alpha", lease.leaseOwner());
        assertFalse(lease.isExpired(System.currentTimeMillis()));

        // Expired after leaseDuration
        assertTrue(lease.isExpired(System.currentTimeMillis() + 1500L));

        // Renew extends lease
        JobLease renewed = lease.renew(System.currentTimeMillis() + 800L);
        assertFalse(renewed.isExpired(System.currentTimeMillis() + 900L));
    }

    @Test
    @DisplayName("DurableJobQueue enqueues, atomically claims, and completes background jobs")
    void enqueueClaimAndCompleteJob(@TempDir Path tempDir) throws Exception {
        Path dbPath = tempDir.resolve("queue_test.db");
        String jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        DatabaseMigrationService.verifyOrMigrate(jdbcUrl);

        DurableJobQueue queue = new DurableJobQueue();

        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            seedWorkspace(conn, "ws-1");

            // 1. Enqueue job
            JobDescriptor enqueued = queue.enqueue(conn, "job-1", "ws-1", "EXTRACT", "doc-1", JobPriority.HIGH);
            assertNotNull(enqueued);
            assertEquals(JobStatus.QUEUED, enqueued.status());

            // 2. Claim job
            Optional<JobDescriptor> claimed = queue.claim(conn, "worker-node-1");
            assertTrue(claimed.isPresent());
            assertEquals("job-1", claimed.get().id());
            assertEquals(JobStatus.RUNNING, claimed.get().status());
            assertEquals(1, claimed.get().attempts());

            // Second worker attempting claim gets empty because queue is empty
            Optional<JobDescriptor> emptyClaim = queue.claim(conn, "worker-node-2");
            assertTrue(emptyClaim.isEmpty());

            // 3. Heartbeat
            assertTrue(queue.heartbeat(conn, "job-1", "ws-1"));

            // 4. Complete job
            assertTrue(queue.complete(conn, "job-1", "ws-1"));

            // Verify status
            JobQueueMetrics metrics = queue.getMetrics(conn, "ws-1");
            assertEquals(0, metrics.queuedCount());
            assertEquals(0, metrics.runningCount());
            assertEquals(1, metrics.completedCount());
        }
    }

    @Test
    @DisplayName("DurableJobQueue retries transient failures with backoff and fails terminally after max attempts")
    void retryAndTerminalFailure(@TempDir Path tempDir) throws Exception {
        Path dbPath = tempDir.resolve("retry_test.db");
        String jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        DatabaseMigrationService.verifyOrMigrate(jdbcUrl);

        DurableJobQueue queue = new DurableJobQueue(new BackoffPolicy(500L, 5000L, 2.0), 30_000L);

        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            seedWorkspace(conn, "ws-retry");
            queue.enqueue(conn, "job-retry-1", "ws-retry", "ANALYZE", "doc-r1", JobPriority.NORMAL);

            // Attempt 1: Claim then fail retryable
            Optional<JobDescriptor> claim1 = queue.claim(conn, "worker-1");
            assertTrue(claim1.isPresent());
            assertEquals(1, claim1.get().attempts());

            boolean retried = queue.fail(conn, "job-retry-1", "ws-retry", "Transient lock timeout", true, 2);
            assertTrue(retried);

            // Check status is now QUEUED (retrying)
            JobQueueMetrics m1 = queue.getMetrics(conn, "ws-retry");
            assertEquals(1, m1.retryingCount());

            // Force available_at to past so it can be claimed again for test
            try (Statement st = conn.createStatement()) {
                st.execute("UPDATE jobs SET available_at=0 WHERE id='job-retry-1'");
            }

            // Attempt 2: Claim then fail terminally (attempts >= maxAttempts)
            Optional<JobDescriptor> claim2 = queue.claim(conn, "worker-1");
            assertTrue(claim2.isPresent());
            assertEquals(2, claim2.get().attempts());

            boolean failedTerminal = queue.fail(conn, "job-retry-1", "ws-retry", "Permanent error", true, 2);
            assertTrue(failedTerminal);

            JobQueueMetrics m2 = queue.getMetrics(conn, "ws-retry");
            assertEquals(1, m2.failedCount());
            assertEquals(0, m2.runningCount());
            assertEquals(0, m2.queuedCount());
        }
    }

    @Test
    @DisplayName("DurableJobQueue recovers abandoned/stale jobs left in RUNNING status by lost workers")
    void staleJobRecovery(@TempDir Path tempDir) throws Exception {
        Path dbPath = tempDir.resolve("stale_test.db");
        String jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        DatabaseMigrationService.verifyOrMigrate(jdbcUrl);

        DurableJobQueue queue = new DurableJobQueue();

        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            seedWorkspace(conn, "ws-stale");

            // Seed an abandoned running job (e.g. from crashed process)
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO jobs(id, workspace_id, kind, resource_id, status, attempts, available_at, created_at) "
                            + "VALUES('stale-1', 'ws-stale', 'EXTRACT', 'doc-s1', 'RUNNING', 1, 0, ?)")) {
                ps.setString(1, Instant.now().toString());
                ps.executeUpdate();
            }

            // Recover stale jobs
            int recovered = queue.recoverStaleJobs(conn, 1000L);
            assertEquals(1, recovered);

            // Job should now be back in QUEUED state
            JobQueueMetrics metrics = queue.getMetrics(conn, "ws-stale");
            assertEquals(1, metrics.queuedCount());
            assertEquals(0, metrics.runningCount());
        }
    }

    private void seedWorkspace(Connection conn, String workspaceId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO workspaces(id, name, created_at) VALUES(?, ?, ?)")) {
            ps.setString(1, workspaceId);
            ps.setString(2, "Workspace " + workspaceId);
            ps.setString(3, Instant.now().toString());
            ps.executeUpdate();
        }
    }
}
