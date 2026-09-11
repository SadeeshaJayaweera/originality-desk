package com.writingassistant.core.citation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-accuracy citation formatter implementing APA 7th, MLA 9th, and Chicago 17th (Author-Date) standards.
 * Traceable to official academic style guide conventions.
 */
public final class CitationFormatter {

    private CitationFormatter() {
    }

    /**
     * Formats an in-text parenthetical citation.
     *
     * @param source Source quote metadata
     * @param style  Citation standard (APA, MLA, Chicago)
     * @return Formatted in-text citation, e.g. "(Kahneman, 2011, p. 45)"
     */
    public static String formatInText(SourceQuote source, CitationStyle style) {
        if (source == null) {
            return "";
        }
        CitationStyle targetStyle = (style != null) ? style : CitationStyle.APA;
        List<ParsedAuthor> authors = parseAuthors(source.getAuthor());
        String year = (source.getYear() != null && !source.getYear().isBlank()) ? source.getYear().trim() : "n.d.";
        String page = (source.getPageNumber() != null && !source.getPageNumber().isBlank()) ? source.getPageNumber().trim() : null;

        return switch (targetStyle) {
            case APA -> formatApaInText(authors, year, page, source.getTitle());
            case MLA -> formatMlaInText(authors, page, source.getTitle());
            case CHICAGO -> formatChicagoInText(authors, year, page, source.getTitle());
        };
    }

    /**
     * Formats a complete bibliography / reference-list entry.
     *
     * @param source Source quote metadata
     * @param style  Citation standard (APA, MLA, Chicago)
     * @return Formatted bibliography entry
     */
    public static String formatBibliographyEntry(SourceQuote source, CitationStyle style) {
        if (source == null) {
            return "";
        }
        CitationStyle targetStyle = (style != null) ? style : CitationStyle.APA;
        return switch (targetStyle) {
            case APA -> formatApaBibliography(source);
            case MLA -> formatMlaBibliography(source);
            case CHICAGO -> formatChicagoBibliography(source);
        };
    }

    // =========================================================================
    // APA 7th Edition Formatting
    // Conventions based on: Publication Manual of the American Psychological Association (7th ed., 2020)
    // =========================================================================

    /**
     * APA 7th In-Text Citation (APA Manual §8.11–8.17):
     * - 1 Author: (Smith, 2020) or (Smith, 2020, p. 15)
     * - 2 Authors: (Smith & Jones, 2020)
     * - 3+ Authors: (Smith et al., 2020)
     * - Group/No Author: ("Short Title", 2020)
     */
    private static String formatApaInText(List<ParsedAuthor> authors, String year, String page, String title) {
        StringBuilder sb = new StringBuilder("(");
        if (authors.isEmpty()) {
            sb.append("\"").append(getShortTitle(title)).append("\"");
        } else if (authors.size() == 1) {
            sb.append(authors.get(0).getSurname());
        } else if (authors.size() == 2) {
            sb.append(authors.get(0).getSurname()).append(" & ").append(authors.get(1).getSurname());
        } else {
            // APA 7th uses "et al." for 3 or more authors even on first citation (§8.17)
            sb.append(authors.get(0).getSurname()).append(" et al.");
        }

        sb.append(", ").append(year);
        if (page != null) {
            String pagePrefix = page.contains("-") || page.contains(",") ? "pp. " : "p. ";
            sb.append(", ").append(pagePrefix).append(page);
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * APA 7th Bibliography Entry (APA Manual Chapter 9 & 10):
     * - Book (§10.2): Author, A. A. (Year). *Title of book*. Publisher.
     * - Journal (§10.1): Author, A. A., & Author, B. B. (Year). Title of article. *Title of Periodical*, *vol*(issue), pp–pp. https://doi.org/...
     * - Website (§10.16): Author, A. A. (Year). *Title of work*. Website Name. URL
     */
    private static String formatApaBibliography(SourceQuote source) {
        StringBuilder sb = new StringBuilder();
        List<ParsedAuthor> authors = parseAuthors(source.getAuthor());

        // 1. Authors
        if (!authors.isEmpty()) {
            sb.append(formatApaAuthors(authors)).append(" ");
        } else if (source.getTitle() != null && !source.getTitle().isBlank()) {
            sb.append(source.getTitle().trim()).append(". ");
        }

        // 2. Year (APA §9.13–9.17)
        String year = (source.getYear() != null && !source.getYear().isBlank()) ? source.getYear().trim() : "n.d.";
        sb.append("(").append(year).append("). ");

        // 3. Title & Source details by SourceType
        SourceType type = source.getSourceType();
        String title = (source.getTitle() != null) ? source.getTitle().trim() : "Untitled";
        String pub = (source.getPublication() != null) ? source.getPublication().trim() : "";

        if (type == SourceType.JOURNAL) {
            // Article title not italicized in APA; journal name italicized
            sb.append(title);
            if (!title.endsWith(".")) sb.append(".");
            sb.append(" ");
            if (!pub.isEmpty()) {
                sb.append("*").append(pub).append("*");
                if (source.getVolume() != null && !source.getVolume().isBlank()) {
                    sb.append(", *").append(source.getVolume().trim()).append("*");
                    if (source.getIssue() != null && !source.getIssue().isBlank()) {
                        sb.append("(").append(source.getIssue().trim()).append(")");
                    }
                }
                if (source.getPageNumber() != null && !source.getPageNumber().isBlank()) {
                    sb.append(", ").append(source.getPageNumber().trim());
                }
                sb.append(".");
            }
        } else if (type == SourceType.BOOK) {
            // Book title italicized in APA
            sb.append("*").append(title).append("*.");
            if (!pub.isEmpty()) {
                sb.append(" ").append(pub).append(".");
            }
        } else {
            // Website / General: Title italicized, site name normal
            sb.append("*").append(title).append("*.");
            if (!pub.isEmpty()) {
                sb.append(" ").append(pub).append(".");
            }
        }

        // 4. URL / DOI (APA §9.34–9.36)
        if (source.getUrlOrDoi() != null && !source.getUrlOrDoi().isBlank()) {
            sb.append(" ").append(source.getUrlOrDoi().trim());
        }

        return sb.toString().trim();
    }

    private static String formatApaAuthors(List<ParsedAuthor> authors) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < authors.size(); i++) {
            ParsedAuthor a = authors.get(i);
            sb.append(a.toApaFormat());
            if (i < authors.size() - 2) {
                sb.append(", ");
            } else if (i == authors.size() - 2) {
                sb.append(", & ");
            }
        }
        if (!sb.toString().endsWith(".")) {
            sb.append(".");
        }
        return sb.toString();
    }

    // =========================================================================
    // MLA 9th Edition Formatting
    // Conventions based on: MLA Handbook (9th ed., 2021)
    // =========================================================================

    /**
     * MLA 9th In-Text Citation (MLA Handbook §6.1–6.9):
     * - 1 Author: (Smith 45) or (Smith)
     * - 2 Authors: (Smith and Jones 45)
     * - 3+ Authors: (Smith et al. 45)
     * - No Author: ("Short Title" 45)
     */
    private static String formatMlaInText(List<ParsedAuthor> authors, String page, String title) {
        StringBuilder sb = new StringBuilder("(");
        if (authors.isEmpty()) {
            sb.append("\"").append(getShortTitle(title)).append("\"");
        } else if (authors.size() == 1) {
            sb.append(authors.get(0).getSurname());
        } else if (authors.size() == 2) {
            sb.append(authors.get(0).getSurname()).append(" and ").append(authors.get(1).getSurname());
        } else {
            sb.append(authors.get(0).getSurname()).append(" et al.");
        }

        if (page != null) {
            sb.append(" ").append(page);
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * MLA 9th Works Cited Entry (MLA Handbook Chapter 5):
     * - Book: Author. *Title of Book*. Publisher, Year.
     * - Journal: Author. "Title of Article." *Title of Journal*, vol. X, no. Y, Year, pp. XX–YY. URL.
     * - Website: Author. "Title of Page." *Website Name*, Day Month Year, URL.
     */
    private static String formatMlaBibliography(SourceQuote source) {
        StringBuilder sb = new StringBuilder();
        List<ParsedAuthor> authors = parseAuthors(source.getAuthor());

        // 1. Authors (MLA §5.3–5.14)
        if (!authors.isEmpty()) {
            sb.append(formatMlaAuthors(authors)).append(" ");
        }

        String title = (source.getTitle() != null) ? source.getTitle().trim() : "Untitled";
        String pub = (source.getPublication() != null) ? source.getPublication().trim() : "";
        String year = (source.getYear() != null && !source.getYear().isBlank()) ? source.getYear().trim() : "";
        SourceType type = source.getSourceType();

        if (type == SourceType.JOURNAL) {
            sb.append("\"").append(title);
            if (!title.endsWith(".")) sb.append(".");
            sb.append("\" ");
            if (!pub.isEmpty()) {
                sb.append("*").append(pub).append("*");
                if (source.getVolume() != null && !source.getVolume().isBlank()) {
                    sb.append(", vol. ").append(source.getVolume().trim());
                }
                if (source.getIssue() != null && !source.getIssue().isBlank()) {
                    sb.append(", no. ").append(source.getIssue().trim());
                }
                if (!year.isEmpty()) {
                    sb.append(", ").append(year);
                }
                if (source.getPageNumber() != null && !source.getPageNumber().isBlank()) {
                    String pagePrefix = source.getPageNumber().contains("-") ? ", pp. " : ", p. ";
                    sb.append(pagePrefix).append(source.getPageNumber().trim());
                }
                sb.append(".");
            }
        } else if (type == SourceType.BOOK) {
            sb.append("*").append(title).append("*.");
            if (!pub.isEmpty()) {
                sb.append(" ").append(pub);
                if (!year.isEmpty()) {
                    sb.append(", ").append(year);
                }
                sb.append(".");
            } else if (!year.isEmpty()) {
                sb.append(" ").append(year).append(".");
            }
        } else {
            // Website
            sb.append("\"").append(title);
            if (!title.endsWith(".")) sb.append(".");
            sb.append("\" ");
            if (!pub.isEmpty()) {
                sb.append("*").append(pub).append("*");
                if (!year.isEmpty()) {
                    sb.append(", ").append(year);
                }
                sb.append(".");
            } else if (!year.isEmpty()) {
                sb.append(year).append(".");
            }
        }

        if (source.getUrlOrDoi() != null && !source.getUrlOrDoi().isBlank()) {
            sb.append(" ").append(source.getUrlOrDoi().trim()).append(".");
        }

        return sb.toString().trim();
    }

    private static String formatMlaAuthors(List<ParsedAuthor> authors) {
        StringBuilder sb = new StringBuilder();
        if (authors.size() == 1) {
            sb.append(authors.get(0).toMlaFirstAuthorFormat());
        } else if (authors.size() == 2) {
            sb.append(authors.get(0).toMlaFirstAuthorFormat())
              .append(", and ")
              .append(authors.get(1).toNormalFormat());
        } else {
            sb.append(authors.get(0).toMlaFirstAuthorFormat()).append(", et al.");
        }
        if (!sb.toString().endsWith(".")) {
            sb.append(".");
        }
        return sb.toString();
    }

    // =========================================================================
    // Chicago 17th Edition (Author-Date) Formatting
    // Conventions based on: The Chicago Manual of Style (17th ed., 2017 Chapter 15)
    // =========================================================================

    /**
     * Chicago 17th Author-Date In-Text Citation (CMOS §15.21–15.30):
     * - 1 Author: (Smith 2020) or (Smith 2020, 45)
     * - 2 Authors: (Smith and Jones 2020, 45)
     * - 3 Authors: (Smith, Jones, and Lee 2020, 45)
     * - 4+ Authors: (Smith et al. 2020, 45)
     */
    private static String formatChicagoInText(List<ParsedAuthor> authors, String year, String page, String title) {
        StringBuilder sb = new StringBuilder("(");
        if (authors.isEmpty()) {
            sb.append("\"").append(getShortTitle(title)).append("\"");
        } else if (authors.size() == 1) {
            sb.append(authors.get(0).getSurname());
        } else if (authors.size() == 2) {
            sb.append(authors.get(0).getSurname()).append(" and ").append(authors.get(1).getSurname());
        } else if (authors.size() == 3) {
            sb.append(authors.get(0).getSurname()).append(", ")
              .append(authors.get(1).getSurname()).append(", and ")
              .append(authors.get(2).getSurname());
        } else {
            sb.append(authors.get(0).getSurname()).append(" et al.");
        }

        sb.append(" ").append(year);
        if (page != null) {
            sb.append(", ").append(page);
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Chicago 17th Reference List Entry (CMOS §15.32–15.53):
     * - Book: Author. Year. *Title of Book*. Publisher.
     * - Journal: Author. Year. "Title of Article." *Title of Journal* volume (issue): pages. URL/DOI.
     * - Website: Author. Year. "Title of Page." Website Name. URL.
     */
    private static String formatChicagoBibliography(SourceQuote source) {
        StringBuilder sb = new StringBuilder();
        List<ParsedAuthor> authors = parseAuthors(source.getAuthor());

        // 1. Authors (CMOS §15.9–15.16)
        if (!authors.isEmpty()) {
            sb.append(formatChicagoAuthors(authors)).append(" ");
        }

        // 2. Year directly follows author in Author-Date style
        String year = (source.getYear() != null && !source.getYear().isBlank()) ? source.getYear().trim() : "n.d.";
        sb.append(year).append(". ");

        String title = (source.getTitle() != null) ? source.getTitle().trim() : "Untitled";
        String pub = (source.getPublication() != null) ? source.getPublication().trim() : "";
        SourceType type = source.getSourceType();

        if (type == SourceType.JOURNAL) {
            sb.append("\"").append(title);
            if (!title.endsWith(".")) sb.append(".");
            sb.append("\" ");
            if (!pub.isEmpty()) {
                sb.append("*").append(pub).append("*");
                if (source.getVolume() != null && !source.getVolume().isBlank()) {
                    sb.append(" ").append(source.getVolume().trim());
                    if (source.getIssue() != null && !source.getIssue().isBlank()) {
                        sb.append(" (").append(source.getIssue().trim()).append(")");
                    }
                }
                if (source.getPageNumber() != null && !source.getPageNumber().isBlank()) {
                    sb.append(": ").append(source.getPageNumber().trim());
                }
                sb.append(".");
            }
        } else if (type == SourceType.BOOK) {
            sb.append("*").append(title).append("*.");
            if (!pub.isEmpty()) {
                sb.append(" ").append(pub).append(".");
            }
        } else {
            // Website
            sb.append("\"").append(title);
            if (!title.endsWith(".")) sb.append(".");
            sb.append("\" ");
            if (!pub.isEmpty()) {
                sb.append(pub).append(".");
            }
        }

        if (source.getUrlOrDoi() != null && !source.getUrlOrDoi().isBlank()) {
            sb.append(" ").append(source.getUrlOrDoi().trim()).append(".");
        }

        return sb.toString().trim();
    }

    private static String formatChicagoAuthors(List<ParsedAuthor> authors) {
        StringBuilder sb = new StringBuilder();
        if (authors.size() == 1) {
            sb.append(authors.get(0).toMlaFirstAuthorFormat());
        } else if (authors.size() == 2) {
            sb.append(authors.get(0).toMlaFirstAuthorFormat())
              .append(", and ")
              .append(authors.get(1).toNormalFormat());
        } else if (authors.size() <= 10) {
            for (int i = 0; i < authors.size(); i++) {
                if (i == 0) {
                    sb.append(authors.get(0).toMlaFirstAuthorFormat());
                } else if (i == authors.size() - 1) {
                    sb.append(", and ").append(authors.get(i).toNormalFormat());
                } else {
                    sb.append(", ").append(authors.get(i).toNormalFormat());
                }
            }
        } else {
            sb.append(authors.get(0).toMlaFirstAuthorFormat()).append(", et al.");
        }
        if (!sb.toString().endsWith(".")) {
            sb.append(".");
        }
        return sb.toString();
    }

    // =========================================================================
    // Author Parsing & Formatting Helpers
    // =========================================================================

    public static List<ParsedAuthor> parseAuthors(String authorStr) {
        List<ParsedAuthor> result = new ArrayList<>();
        if (authorStr == null || authorStr.isBlank()) {
            return result;
        }

        String raw = authorStr.trim();

        // Check if semicolon separated (e.g. "Kahneman, D.; Tversky, A.")
        if (raw.contains(";")) {
            for (String part : raw.split(";")) {
                if (!part.isBlank()) result.add(ParsedAuthor.parse(part.trim()));
            }
            return result;
        }

        // Normalize ", and ", ", & ", " and ", " & "
        String normalized = raw.replaceAll(",\\s+(?:and|&)\\s+", ", ")
                               .replaceAll("\\s+(?:and|&)\\s+", ", ");

        // Check for list of authors: e.g. "Kaiming He, Xiangyu Zhang, Shaoqing Ren, Jian Sun"
        // or "Vaswani, A., Shazeer, N."
        String[] commaParts = normalized.split(",\\s*");
        if (commaParts.length == 1) {
            result.add(ParsedAuthor.parse(commaParts[0].trim()));
        } else if (commaParts.length == 2 && !commaParts[1].contains(" ") && commaParts[1].length() <= 3) {
            // E.g. "Kahneman, D." or "Smith, J." -> single author with "Last, First/Initial"
            result.add(new ParsedAuthor(commaParts[0].trim(), commaParts[1].trim()));
        } else {
            // Check if parts alternate between Last and Initials: "Vaswani, A., Shazeer, N."
            boolean arePairs = true;
            for (int i = 1; i < commaParts.length; i += 2) {
                String p = commaParts[i].trim();
                // If odd index is short/initial (<= 4 chars or ends with .), it's likely "Last, Initials"
                if (p.length() > 5 && !p.endsWith(".")) {
                    arePairs = false;
                    break;
                }
            }

            if (arePairs && commaParts.length % 2 == 0) {
                for (int i = 0; i < commaParts.length; i += 2) {
                    result.add(new ParsedAuthor(commaParts[i].trim(), commaParts[i + 1].trim()));
                }
            } else {
                for (String cp : commaParts) {
                    if (!cp.isBlank()) {
                        result.add(ParsedAuthor.parse(cp.trim()));
                    }
                }
            }
        }

        return result;
    }

    private static String getShortTitle(String fullTitle) {
        if (fullTitle == null || fullTitle.isBlank()) {
            return "Untitled";
        }
        String trimmed = fullTitle.trim();
        String[] words = trimmed.split("\\s+");
        if (words.length <= 4) {
            return trimmed;
        }
        return words[0] + " " + words[1] + " " + words[2] + "...";
    }

    /**
     * Parsed individual author details.
     */
    public static class ParsedAuthor {
        private final String surname;
        private final String givenName;

        public ParsedAuthor(String surname, String givenName) {
            this.surname = surname != null ? surname.trim() : "";
            this.givenName = givenName != null ? givenName.trim() : "";
        }

        public static ParsedAuthor parse(String raw) {
            if (raw.contains(",")) {
                String[] parts = raw.split(",", 2);
                return new ParsedAuthor(parts[0], parts[1]);
            }
            int lastSpace = raw.lastIndexOf(' ');
            if (lastSpace != -1) {
                return new ParsedAuthor(raw.substring(lastSpace + 1), raw.substring(0, lastSpace));
            }
            return new ParsedAuthor(raw, "");
        }

        public String getSurname() {
            return surname;
        }

        public String getGivenName() {
            return givenName;
        }

        public String toApaFormat() {
            if (givenName.isEmpty()) {
                return surname;
            }
            StringBuilder initials = new StringBuilder();
            for (String part : givenName.split("\\s+")) {
                if (!part.isEmpty()) {
                    initials.append(part.charAt(0)).append(". ");
                }
            }
            return surname + ", " + initials.toString().trim();
        }

        public String toMlaFirstAuthorFormat() {
            if (givenName.isEmpty()) {
                return surname;
            }
            return surname + ", " + givenName;
        }

        public String toNormalFormat() {
            if (givenName.isEmpty()) {
                return surname;
            }
            return givenName + " " + surname;
        }
    }
}
