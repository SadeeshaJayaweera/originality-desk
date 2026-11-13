package com.writingassistant.sources;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for normalizing, validating, and formatting Digital Object Identifiers (DOIs).
 */
public final class DoiNormalizer {

    private static final Pattern DOI_PATTERN = Pattern.compile("(?i)\\b(10\\.\\d{4,9}/[-._;()/:A-Za-z0-9]+)");

    private DoiNormalizer() { }

    /**
     * Normalizes a raw DOI string, URL, or citation fragment into canonical "10.xxxx/yyyy" form.
     *
     * @param raw input string containing a DOI
     * @return normalized lowercase DOI, or empty if invalid
     */
    public static Optional<String> normalize(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();

        String cleaned = raw.trim();

        // Strip common prefixes
        if (cleaned.startsWith("https://doi.org/")) cleaned = cleaned.substring(16);
        else if (cleaned.startsWith("http://doi.org/")) cleaned = cleaned.substring(15);
        else if (cleaned.startsWith("https://dx.doi.org/")) cleaned = cleaned.substring(19);
        else if (cleaned.startsWith("http://dx.doi.org/")) cleaned = cleaned.substring(18);
        else if (cleaned.toLowerCase(Locale.ROOT).startsWith("doi:")) cleaned = cleaned.substring(4).trim();

        // Strip trailing punctuation often caught in citation scraping
        while (cleaned.endsWith(".") || cleaned.endsWith(",") || cleaned.endsWith(";") || cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }

        Matcher matcher = DOI_PATTERN.matcher(cleaned);
        if (matcher.find()) {
            String found = matcher.group(1).toLowerCase(Locale.ROOT);
            while (found.endsWith(".") || found.endsWith(",") || found.endsWith(";") || found.endsWith("/") || found.endsWith(")")) {
                found = found.substring(0, found.length() - 1).trim();
            }
            return Optional.of(found);
        }

        return Optional.empty();
    }

    /**
     * Formats a normalized DOI as a standard HTTPS resolver URI.
     */
    public static String toResolverUrl(String doi) {
        return normalize(doi)
                .map(d -> "https://doi.org/" + d)
                .orElse(doi != null ? doi : "");
    }
}
