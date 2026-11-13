package com.writingassistant.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

final class CitationDetector {
    static final int MAX_CITATIONS = 300;
    private static final Pattern REFERENCES = Pattern.compile("(?im)^[\\t ]{0,80}(?:references|bibliography|works cited)[\\t ]{0,80}:?[\\t ]{0,80}\\r?$");
    private static final Pattern AUTHOR_YEAR = Pattern.compile("\\([^()\\r\\n]{1,180}\\b(?:18|19|20)\\d{2}[a-z]?(?:[^()\\r\\n]{0,40})\\)");
    private static final Pattern NARRATIVE = Pattern.compile("\\b\\p{Lu}[\\p{L}'’\\-]{1,40}(?: et al\\.)? \\((?:18|19|20)\\d{2}[a-z]?\\)");
    private static final Pattern NUMERIC = Pattern.compile("\\[\\d{1,4}(?:[\\t ]*[,;–-][\\t ]*\\d{1,4}){0,15}\\]");
    private static final Pattern QUOTES = Pattern.compile("\"[^\"\\f]{1,1998}\"|“[^”\\f]{1,1998}”|‘[^’\\f]{1,1998}’");

    private CitationDetector() { }
    record Scan(List<EvidenceEngine.Citation> citations, int referenceStart, boolean capped) { }

    static Scan scan(String text) {
        List<EvidenceEngine.Citation> citations = new ArrayList<>();
        var heading = REFERENCES.matcher(text);
        int referenceStart = text.length();
        if (heading.find()) {
            referenceStart = heading.start();
            citations.add(new EvidenceEngine.Citation(heading.group(), "REFERENCE_LIST", heading.start(), heading.end(), true));
        }
        boolean capped = false;
        Pattern[] patterns = {AUTHOR_YEAR, NARRATIVE, NUMERIC, QUOTES};
        String[] kinds = {"AUTHOR_YEAR", "AUTHOR_YEAR", "NUMERIC", "QUOTATION"};
        for (int i = 0; i < patterns.length; i++) {
            String kind = kinds[i];
            var matcher = patterns[i].matcher(text);
            while (matcher.find()) {
                if (citations.size() == MAX_CITATIONS) { capped = true; break; }
                int start = matcher.start();
                int end = matcher.end();
                boolean duplicate = citations.stream().anyMatch(c -> c.kind().equals(kind) && c.start() >= start && c.end() <= end);
                if (!duplicate) citations.add(new EvidenceEngine.Citation(matcher.group(), kind, start, end, true));
            }
        }
        citations.sort(Comparator.comparingInt(EvidenceEngine.Citation::start).thenComparingInt(EvidenceEngine.Citation::end));
        return new Scan(List.copyOf(citations), referenceStart, capped);
    }


    static String observation(int start, int end, Scan scan) {
        if (start >= scan.referenceStart()) return "Reference-list region detected; heuristic, not validated";
        boolean quote = false;
        boolean citation = false;
        for (var c : scan.citations()) {
            if (c.kind().equals("QUOTATION")) quote |= c.start() <= start && c.end() >= end;
            else if (!c.kind().equals("REFERENCE_LIST")) citation |= c.start() <= end + 160 && c.end() >= start - 160;
        }
        if (quote && citation) return "Quotation and nearby citation pattern detected; attribution not validated";
        if (quote) return "Quotation detected; attribution not validated";
        if (citation) return "Nearby citation pattern detected; attribution not validated";
        return "No nearby citation pattern detected; absence is uncertain";
    }
}
