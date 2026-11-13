package com.writingassistant.sources;

import java.io.Serializable;
import java.util.Objects;

/**
 * Metadata attributing an external academic work to its scholarly publisher,
 * institutional repository, or open access database.
 */
public record SourceAttribution(
        String providerId,
        String sourceRepository,
        String persistentUri,
        String license,
        boolean isOpenAccess
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public SourceAttribution {
        Objects.requireNonNull(providerId, "providerId must not be null");
    }

    public static SourceAttribution of(String providerId, String sourceRepository, String persistentUri, String license, boolean isOpenAccess) {
        return new SourceAttribution(providerId, sourceRepository, persistentUri, license, isOpenAccess);
    }
}
