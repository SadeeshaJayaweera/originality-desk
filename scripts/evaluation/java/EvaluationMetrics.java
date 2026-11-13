package com.writingassistant.evaluation;

import java.util.Arrays;
import java.util.List;

final class EvaluationMetrics {
    record Prediction(boolean expected, boolean predicted) { }
    record Classification(int cases, int positives, int negatives, int tp, int fp, int tn, int fn,
            Double precision, Double recall, Double f1, Double falsePositiveRate, Double falseNegativeRate) { }
    record Timing(int samples, double meanMs, double medianMs, double p95Ms, double minMs, double maxMs) { }
    private EvaluationMetrics() { }

    static Classification classify(List<Prediction> predictions) {
        int tp = 0, fp = 0, tn = 0, fn = 0;
        for (Prediction prediction : predictions) {
            if (prediction.expected()) {
                if (prediction.predicted()) tp++; else fn++;
            } else {
                if (prediction.predicted()) fp++; else tn++;
            }
        }
        return new Classification(predictions.size(), tp + fn, tn + fp, tp, fp, tn, fn,
                ratio(tp, tp + fp), ratio(tp, tp + fn), ratio(2 * tp, 2 * tp + fp + fn),
                ratio(fp, fp + tn), ratio(fn, fn + tp));
    }

    private static Double ratio(int numerator, int denominator) {
        return denominator == 0 ? null : (double) numerator / denominator;
    }

    static Timing timing(List<Long> values) {
        if (values.isEmpty()) throw new IllegalArgumentException("Timing samples required");
        long[] sorted = values.stream().mapToLong(Long::longValue).sorted().toArray();
        double median = sorted.length % 2 == 0
                ? (sorted[sorted.length / 2 - 1] / 2.0 + sorted[sorted.length / 2] / 2.0)
                : sorted[sorted.length / 2];
        int p95 = (int) Math.ceil(sorted.length * 0.95) - 1;
        return new Timing(sorted.length, Arrays.stream(sorted).average().orElseThrow() / 1e6,
                median / 1e6, sorted[p95] / 1e6, sorted[0] / 1e6, sorted[sorted.length - 1] / 1e6);
    }

    static String outcome(boolean expected, boolean predicted) {
        return expected ? (predicted ? "TP" : "FN") : (predicted ? "FP" : "TN");
    }
}
