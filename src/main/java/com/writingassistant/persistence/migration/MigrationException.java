package com.writingassistant.persistence.migration;

/**
 * Exception thrown when database migration, validation, or schema verification fails.
 */
public class MigrationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MigrationException(String message) {
        super(message);
    }

    public MigrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
