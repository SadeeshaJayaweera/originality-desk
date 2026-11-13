package com.writingassistant.evaluation;

import java.util.List;

final class EvaluationSelfTest {
    private EvaluationSelfTest() { }

    static void run() {
        var balanced = EvaluationMetrics.classify(List.of(
                new EvaluationMetrics.Prediction(true, true), new EvaluationMetrics.Prediction(true, false),
                new EvaluationMetrics.Prediction(false, true), new EvaluationMetrics.Prediction(false, false)));
        check(balanced.tp() == 1 && balanced.fp() == 1 && balanced.tn() == 1 && balanced.fn() == 1, "Confusion counts");
        check(balanced.precision() == 0.5 && balanced.recall() == 0.5 && balanced.f1() == 0.5, "Precision/recall/F1");
        check(balanced.falsePositiveRate() == 0.5 && balanced.falseNegativeRate() == 0.5, "FPR/FNR denominators");
        var missed = EvaluationMetrics.classify(List.of(new EvaluationMetrics.Prediction(true, false)));
        check(missed.precision() == null && missed.recall() == 0 && missed.f1() == 0, "Undefined precision versus zero F1");
        check(missed.falsePositiveRate() == null && missed.falseNegativeRate() == 1, "No-negative cohort rates");
        var empty = EvaluationMetrics.classify(List.of());
        check(empty.precision() == null && empty.recall() == null && empty.f1() == null, "Empty denominators");
        var timing = EvaluationMetrics.timing(List.of(4_000_000L, 1_000_000L, 3_000_000L, 2_000_000L));
        check(timing.samples() == 4 && timing.meanMs() == 2.5 && timing.medianMs() == 2.5, "Timing units/median");
        check(timing.p95Ms() == 4 && timing.minMs() == 1 && timing.maxMs() == 4, "Nearest-rank p95");
        check(EvaluationMetrics.outcome(true, false).equals("FN") && EvaluationMetrics.outcome(false, true).equals("FP"), "Outcome names");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Harness self-check failed: " + message);
    }
}
