package com.writingassistant.analysis;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Enforces concurrency boundaries, page limits, and timeout quotas on OCR operations
 * to prevent CPU starvation and out-of-memory errors.
 */
public class OcrResourceGovernor {

    private final OcrConfig config;
    private final Semaphore semaphore;

    public OcrResourceGovernor() {
        this(OcrConfig.DEFAULT);
    }

    public OcrResourceGovernor(OcrConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.semaphore = new Semaphore(config.maxConcurrentTasks(), true);
    }

    public OcrConfig getConfig() {
        return config;
    }

    public int getAvailablePermits() {
        return semaphore.availablePermits();
    }

    /**
     * Executes the given task under a global OCR concurrency permit with timeout.
     *
     * @param task task to execute
     * @param <T>  result type
     * @return task result
     * @throws IOException if permit cannot be acquired or task throws IOException
     */
    public <T> T executeWithPermit(OcrTask<T> task) throws IOException {
        boolean acquired = false;
        try {
            acquired = semaphore.tryAcquire(config.perPageTimeoutMs(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new IOException("OCR concurrency limit of " + config.maxConcurrentTasks()
                        + " reached; timed out waiting for permit");
            }
            return task.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for OCR permit", e);
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }
    }

    /**
     * Validates that requested page count does not exceed configured maximum.
     *
     * @param requestedPages number of pages to process
     * @return requestedPages if within limit
     * @throws IOException if requestedPages exceeds maxPages
     */
    public int checkPageLimit(int requestedPages) throws IOException {
        if (requestedPages > config.maxPages()) {
            throw new IOException("Document contains " + requestedPages + " pages, exceeding maximum OCR page limit of "
                    + config.maxPages());
        }
        return requestedPages;
    }

    @FunctionalInterface
    public interface OcrTask<T> {
        T run() throws IOException;
    }
}
