package com.writingassistant.sources;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Standardized scholarly citation formatter producing formatted reference citations
 * in APA 7th, IEEE, MLA 9th, and BibTeX styles directly from ExternalScholarlyWork metadata.
 */
public final class ScholarlyCitationFormatter {

    private ScholarlyCitationFormatter() {}

    /**
     * Formats an academic work according to APA 7th edition rules.
     * Format: Authors (Year). Title. Venue. DOI/LandingURL
     */
    public static String formatApa(ExternalScholarlyWork work) {
        if (work == null) return "";
        StringBuilder sb = new StringBuilder();
        List<String> authors = work.authors();
        if (authors != null && !authors.isEmpty()) {
            sb.append(formatApaAuthors(authors)).append(" ");
        } else {
            sb.append(cleanTitle(work.title())).append(". ");
        }

        String year = work.publicationYear() != null ? String.valueOf(work.publicationYear()) : "n.d.";
        sb.append("(").append(year).append("). ");

        if (authors != null && !authors.isEmpty()) {
            sb.append(cleanTitle(work.title())).append(". ");
        }

        if (work.venueOrJournal() != null && !work.venueOrJournal().isBlank()) {
            sb.append(work.venueOrJournal().trim()).append(". ");
        }

        String resolver = getPersistentResolver(work);
        if (!resolver.isBlank()) {
            sb.append(resolver);
        }

        return sb.toString().trim();
    }

    /**
     * Formats an academic work according to IEEE citation style.
     * Format: [1] Authors, "Title," Venue, Year, doi: DOI.
     */
    public static String formatIeee(ExternalScholarlyWork work) {
        if (work == null) return "";
        StringBuilder sb = new StringBuilder();
        List<String> authors = work.authors();
        if (authors != null && !authors.isEmpty()) {
            sb.append(formatIeeeAuthors(authors)).append(", ");
        }

        sb.append("\"").append(cleanTitle(work.title())).append(",\" ");

        if (work.venueOrJournal() != null && !work.venueOrJournal().isBlank()) {
            sb.append(work.venueOrJournal().trim()).append(", ");
        }

        if (work.publicationYear() != null) {
            sb.append(work.publicationYear());
        } else {
            sb.append("n.d.");
        }

        if (work.doi() != null && !work.doi().isBlank()) {
            sb.append(", doi: ").append(DoiNormalizer.normalize(work.doi()).orElse(work.doi()));
        } else if (work.landingPageUrl() != null && !work.landingPageUrl().isBlank()) {
            sb.append(", ").append(work.landingPageUrl().trim());
        }
        sb.append(".");

        return sb.toString().trim();
    }

    /**
     * Formats an academic work according to MLA 9th edition rules.
     * Format: Authors. "Title." Venue, Year, URL/DOI.
     */
    public static String formatMla(ExternalScholarlyWork work) {
        if (work == null) return "";
        StringBuilder sb = new StringBuilder();
        List<String> authors = work.authors();
        if (authors != null && !authors.isEmpty()) {
            sb.append(formatMlaAuthors(authors)).append(". ");
        }

        sb.append("\"").append(cleanTitle(work.title())).append(".\" ");

        if (work.venueOrJournal() != null && !work.venueOrJournal().isBlank()) {
            sb.append(work.venueOrJournal().trim()).append(", ");
        }

        if (work.publicationYear() != null) {
            sb.append(work.publicationYear()).append(", ");
        }

        String resolver = getPersistentResolver(work);
        if (!resolver.isBlank()) {
            sb.append(resolver);
        } else if (sb.toString().endsWith(", ")) {
            sb.setLength(sb.length() - 2);
            sb.append(".");
        }

        return sb.toString().trim();
    }

    /**
     * Generates a complete BibTeX bibliography entry.
     */
    public static String formatBibtex(ExternalScholarlyWork work) {
        if (work == null) return "";
        String citeKey = generateCiteKey(work);
        StringBuilder sb = new StringBuilder();
        sb.append("@article{").append(citeKey).append(",\n");
        sb.append("  title = {").append(cleanTitle(work.title())).append("},\n");

        if (work.authors() != null && !work.authors().isEmpty()) {
            sb.append("  author = {").append(String.join(" and ", work.authors())).append("},\n");
        }

        if (work.venueOrJournal() != null && !work.venueOrJournal().isBlank()) {
            sb.append("  journal = {").append(work.venueOrJournal().trim()).append("},\n");
        }

        if (work.publicationYear() != null) {
            sb.append("  year = {").append(work.publicationYear()).append("},\n");
        }

        if (work.doi() != null && !work.doi().isBlank()) {
            String normDoi = DoiNormalizer.normalize(work.doi()).orElse(work.doi());
            sb.append("  doi = {").append(normDoi).append("},\n");
            sb.append("  url = {").append(DoiNormalizer.toResolverUrl(normDoi)).append("}\n");
        } else if (work.landingPageUrl() != null && !work.landingPageUrl().isBlank()) {
            sb.append("  url = {").append(work.landingPageUrl().trim()).append("}\n");
        } else {
            // Trim trailing comma and newline
            int lastComma = sb.lastIndexOf(",");
            if (lastComma > 0) sb.deleteCharAt(lastComma);
        }

        sb.append("}");
        return sb.toString();
    }

    public static String getPersistentResolver(ExternalScholarlyWork work) {
        if (work == null) return "";
        if (work.doi() != null && !work.doi().isBlank()) {
            return DoiNormalizer.toResolverUrl(work.doi());
        }
        if (work.landingPageUrl() != null && !work.landingPageUrl().isBlank()) {
            return work.landingPageUrl().trim();
        }
        return "";
    }

    private static String cleanTitle(String title) {
        if (title == null || title.isBlank()) return "Untitled Work";
        String trimmed = title.trim();
        if (trimmed.endsWith(".")) {
            return trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private static String formatApaAuthors(List<String> authors) {
        if (authors.isEmpty()) return "";
        List<String> formatted = new ArrayList<>();
        for (String author : authors) {
            formatted.add(toInvertedAuthor(author));
        }
        if (formatted.size() == 1) {
            return formatted.get(0);
        } else if (formatted.size() == 2) {
            return formatted.get(0) + ", & " + formatted.get(1);
        } else if (formatted.size() <= 20) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < formatted.size() - 1; i++) {
                sb.append(formatted.get(i)).append(", ");
            }
            sb.append("& ").append(formatted.get(formatted.size() - 1));
            return sb.toString();
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 19; i++) {
                sb.append(formatted.get(i)).append(", ");
            }
            sb.append("... ").append(formatted.get(formatted.size() - 1));
            return sb.toString();
        }
    }

    private static String formatIeeeAuthors(List<String> authors) {
        if (authors.isEmpty()) return "";
        List<String> formatted = new ArrayList<>();
        for (String author : authors) {
            formatted.add(toInitialFirstAuthor(author));
        }
        if (formatted.size() == 1) {
            return formatted.get(0);
        } else if (formatted.size() == 2) {
            return formatted.get(0) + " and " + formatted.get(1);
        } else {
            return formatted.get(0) + " et al.";
        }
    }

    private static String formatMlaAuthors(List<String> authors) {
        if (authors.isEmpty()) return "";
        if (authors.size() == 1) {
            return toInvertedAuthor(authors.get(0));
        } else if (authors.size() == 2) {
            return toInvertedAuthor(authors.get(0)) + ", and " + authors.get(1).trim();
        } else {
            return toInvertedAuthor(authors.get(0)) + ", et al.";
        }
    }

    private static String toInvertedAuthor(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        String trimmed = fullName.trim();
        if (trimmed.contains(",")) return trimmed; // Already inverted
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 1) return parts[0];
        String surname = parts[parts.length - 1];
        StringBuilder initials = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (!parts[i].isEmpty()) {
                initials.append(Character.toUpperCase(parts[i].charAt(0))).append(". ");
            }
        }
        return surname + ", " + initials.toString().trim();
    }

    private static String toInitialFirstAuthor(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        String trimmed = fullName.trim();
        if (trimmed.contains(",")) {
            String[] parts = trimmed.split(",", 2);
            String surname = parts[0].trim();
            String given = parts[1].trim();
            String initial = given.isEmpty() ? "" : Character.toUpperCase(given.charAt(0)) + ". ";
            return initial + surname;
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 1) return parts[0];
        String surname = parts[parts.length - 1];
        StringBuilder initials = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (!parts[i].isEmpty()) {
                initials.append(Character.toUpperCase(parts[i].charAt(0))).append(". ");
            }
        }
        return initials.toString() + surname;
    }

    private static String generateCiteKey(ExternalScholarlyWork work) {
        String authorKey = "work";
        if (work.authors() != null && !work.authors().isEmpty()) {
            String first = work.authors().get(0).trim();
            String[] parts = first.contains(",") ? first.split(",") : first.split("\\s+");
            authorKey = parts[first.contains(",") ? 0 : parts.length - 1].replaceAll("[^a-zA-Z]", "").toLowerCase(Locale.ROOT);
            if (authorKey.isBlank()) authorKey = "author";
        }
        String yearKey = work.publicationYear() != null ? String.valueOf(work.publicationYear()) : "nd";
        String titleKey = "";
        if (work.title() != null && !work.title().isBlank()) {
            String clean = work.title().replaceAll("[^a-zA-Z0-9\\s]", " ").trim();
            String[] words = clean.split("\\s+");
            if (words.length > 0 && !words[0].isBlank()) {
                titleKey = words[0].toLowerCase(Locale.ROOT);
            }
        }
        return authorKey + yearKey + titleKey;
    }
}
