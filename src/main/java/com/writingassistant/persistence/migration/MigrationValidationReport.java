package com.writingassistant.persistence.migration;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Audit report detailing the results and verification checks of a database transfer.
 */
public record MigrationValidationReport(
        String status,
        boolean dryRun,
        long totalSourceRows,
        long totalTargetRows,
        Map<String, Long> sourceRowCounts,
        Map<String, Long> targetRowCounts,
        List<String> integrityViolations,
        List<String> auditMessages,
        long durationMillis
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_DRY_RUN_SUCCESS = "DRY_RUN_SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    public MigrationValidationReport {
        Objects.requireNonNull(status, "status must not be null");
        sourceRowCounts = sourceRowCounts != null ? Collections.unmodifiableMap(sourceRowCounts) : Map.of();
        targetRowCounts = targetRowCounts != null ? Collections.unmodifiableMap(targetRowCounts) : Map.of();
        integrityViolations = integrityViolations != null ? List.copyOf(integrityViolations) : List.of();
        auditMessages = auditMessages != null ? List.copyOf(auditMessages) : List.of();
    }

    public boolean isSuccessful() {
        return STATUS_SUCCESS.equals(status) || STATUS_DRY_RUN_SUCCESS.equals(status);
    }
}
