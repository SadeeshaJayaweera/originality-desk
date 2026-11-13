package com.writingassistant.analysis;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OcrQualityAndControlsTest {

    @Test
    void ocrConfigEnforcesInvariants() {
        assertThrows(IllegalArgumentException.class, () -> new OcrConfig(0, 200, 1000L, 2000L, 0.5f, 2));
        assertThrows(IllegalArgumentException.class, () -> new OcrConfig(10, 50, 1000L, 2000L, 0.5f, 2)); // DPI < 72
        assertThrows(IllegalArgumentException.class, () -> new OcrConfig(10, 700, 1000L, 2000L, 0.5f, 2)); // DPI > 600
        assertThrows(IllegalArgumentException.class, () -> new OcrConfig(10, 200, -100L, 2000L, 0.5f, 2));
        assertThrows(IllegalArgumentException.class, () -> new OcrConfig(10, 200, 3000L, 2000L, 0.5f, 2)); // total < perPage
        assertThrows(IllegalArgumentException.class, () -> new OcrConfig(10, 200, 1000L, 2000L, -0.1f, 2));
        assertThrows(IllegalArgumentException.class, () -> new OcrConfig(10, 200, 1000L, 2000L, 1.1f, 2));
        assertThrows(IllegalArgumentException.class, () -> new OcrConfig(10, 200, 1000L, 2000L, 0.5f, 0));

        OcrConfig config = new OcrConfig(25, 300, 15000L, 60000L, 0.65f, 4);
        assertEquals(25, config.maxPages());
        assertEquals(300, config.dpi());
        assertEquals(15000L, config.perPageTimeoutMs());
        assertEquals(60000L, config.totalTimeoutMs());
        assertEquals(0.65f, config.minConfidenceThreshold());
        assertEquals(4, config.maxConcurrentTasks());
    }

    @Test
    void ocrResultCalculatesMetricsAndAcceptability() {
        OcrResult goodResult = OcrResult.of("This is valid academic prose extracted by OCR.", 0.88f, 1, 0.50f, 120L);
        assertEquals(0.88f, goodResult.confidence());
        assertEquals(1, goodResult.pagesProcessed());
        assertEquals(8, goodResult.wordCount());
        assertTrue(goodResult.acceptableQuality());
        assertEquals(120L, goodResult.durationMs());

        OcrResult lowConfResult = OcrResult.of("Garbled scan", 0.35f, 1, 0.50f, 90L);
        assertFalse(lowConfResult.acceptableQuality(), "Should be rejected when confidence < minThreshold");

        OcrResult emptyResult = OcrResult.of("", 0.95f, 1, 0.50f, 50L);
        assertFalse(emptyResult.acceptableQuality(), "Should be rejected when text is empty");
    }

    @Test
    void ocrQualityAssessorParsesTsvConfidence() {
        String tsv = "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext\n"
                + "5\t1\t1\t1\t1\t1\t10\t10\t50\t20\t90.0\tHello\n"
                + "5\t1\t1\t1\t1\t2\t70\t10\t60\t20\t80.0\tWorld\n"
                + "4\t1\t1\t1\t1\t0\t0\t0\t0\t0\t-1\t\n"; // Non-word row with -1

        float conf = OcrQualityAssessor.computeTsvConfidence(tsv);
        assertEquals(0.85f, conf, 0.01f);

        assertEquals(0.0f, OcrQualityAssessor.computeTsvConfidence(""));
        assertEquals(0.0f, OcrQualityAssessor.computeTsvConfidence(null));
    }

    @Test
    void ocrQualityAssessorEstimatesConfidenceOnProseVsGarbage() {
        String naturalProse = "The academic integrity analysis examines the provenance of scholarly contributions.";
        float proseScore = OcrQualityAssessor.estimateConfidence(naturalProse);
        assertTrue(proseScore > 0.70f, "Natural prose should have high confidence score (> 0.70), got: " + proseScore);

        String garbageScan = "#$#%@^! ~~~ |||| {[[ \\\\\\\\\\ 12891823912839128391823";
        float garbageScore = OcrQualityAssessor.estimateConfidence(garbageScan);
        assertTrue(garbageScore < 0.40f, "Garbage symbol text should have low confidence (< 0.40), got: " + garbageScore);

        assertEquals(0.0f, OcrQualityAssessor.estimateConfidence(null));
        assertEquals(0.0f, OcrQualityAssessor.estimateConfidence("   "));
    }

    @Test
    void resourceGovernorEnforcesPageLimit() throws IOException {
        OcrConfig config = new OcrConfig(10, 200, 5000L, 10000L, 0.5f, 1);
        OcrResourceGovernor governor = new OcrResourceGovernor(config);

        assertEquals(5, governor.checkPageLimit(5));
        assertEquals(10, governor.checkPageLimit(10));
        assertThrows(IOException.class, () -> governor.checkPageLimit(11));
    }

    @Test
    void resourceGovernorEnforcesConcurrencyPermits() throws Exception {
        OcrConfig config = new OcrConfig(10, 200, 200L, 10000L, 0.5f, 1);
        OcrResourceGovernor governor = new OcrResourceGovernor(config);
        assertEquals(1, governor.getAvailablePermits());

        CountDownLatch taskRunningLatch = new CountDownLatch(1);
        CountDownLatch finishTaskLatch = new CountDownLatch(1);
        AtomicBoolean secondTaskFailed = new AtomicBoolean(false);

        Thread thread1 = new Thread(() -> {
            try {
                governor.executeWithPermit(() -> {
                    taskRunningLatch.countDown();
                    try {
                        finishTaskLatch.await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException(e);
                    }
                    return "task1-done";
                });
            } catch (Exception ignored) { }
        });

        thread1.start();
        assertTrue(taskRunningLatch.await(1, TimeUnit.SECONDS));

        // When permit 1 is in use, task 2 must timeout waiting for permit
        try {
            governor.executeWithPermit(() -> "task2-done");
        } catch (IOException e) {
            secondTaskFailed.set(true);
            assertTrue(e.getMessage().contains("concurrency limit"));
        }

        finishTaskLatch.countDown();
        thread1.join();

        assertTrue(secondTaskFailed.get(), "Second task should have been throttled and timed out");
        assertEquals(1, governor.getAvailablePermits(), "Permit must be returned after execution");
    }
}
