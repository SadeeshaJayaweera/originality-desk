package com.writingassistant.persistence.migration;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Set;

/**
 * Transforms column values from SQLite data representations to PostgreSQL equivalents.
 */
public final class TypeTransformer {

    private static final Set<String> TIMESTAMP_COLUMNS = Set.of(
            "created_at",
            "completed_at"
    );

    private static final Set<String> BOOLEAN_COLUMNS = Set.of(
            "is_open_access"
    );

    private TypeTransformer() {}

    /**
     * Converts an extracted SQLite column value to its PostgreSQL target representation.
     *
     * @param columnName Database column name.
     * @param value      Extracted value from SQLite ResultSet.
     * @return Transformed object ready for PostgreSQL PreparedStatement binding.
     */
    public static Object transform(String columnName, Object value) {
        if (value == null) {
            return null;
        }

        String lowerCol = columnName.toLowerCase();

        // 1. Convert text-based ISO-8601 strings to java.sql.Timestamp
        if (TIMESTAMP_COLUMNS.contains(lowerCol)) {
            if (value instanceof String strVal) {
                if (strVal.isBlank()) return null;
                try {
                    return Timestamp.from(Instant.parse(strVal));
                } catch (DateTimeParseException e) {
                    try {
                        return Timestamp.from(OffsetDateTime.parse(strVal).toInstant());
                    } catch (Exception ex) {
                        return strVal; // Fallback to raw string if custom format
                    }
                }
            } else if (value instanceof Number num) {
                return new Timestamp(num.longValue());
            }
        }

        // 2. Convert SQLite 0/1 integers to Integer or Boolean if desired
        if (BOOLEAN_COLUMNS.contains(lowerCol) && value instanceof Number num) {
            return num.intValue() != 0 ? 1 : 0;
        }

        return value;
    }
}
