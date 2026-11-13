package com.writingassistant.analysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class EvidenceSegments {
    static final int MAX_PASSAGE = 2_000;
    static final int MAX_WORDS = 160;
    record Segment(int source, int start, int end, Set<String> terms) { }
    private EvidenceSegments() { }

    static List<Segment> segments(int source, String original, List<TextNormalizer.Token> tokens, int limit) {
        List<Segment> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        // Both sentence and paragraph windows; windows are split, not silently truncated.
        for (boolean sentences : new boolean[]{true, false}) {
            int first = 0;
            for (int i = 0; i < tokens.size() && tokens.get(i).end() <= limit; i++) {
                boolean last = i + 1 == tokens.size() || tokens.get(i + 1).end() > limit;
                boolean split = last || i - first + 1 >= 80;
                if (!split) {
                    int gapStart = tokens.get(i).end();
                    int gapEnd = tokens.get(i + 1).start();
                    // A compatibility glyph may expand to several words sharing one original span.
                    String gap = gapEnd > gapStart ? original.substring(gapStart, gapEnd) : "";
                    split = gap.indexOf('\n') >= 0 || gap.indexOf('\r') >= 0 || gap.indexOf('\f') >= 0
                            || sentences && (gap.indexOf('.') >= 0 || gap.indexOf('!') >= 0 || gap.indexOf('?') >= 0 || gap.indexOf(';') >= 0)
                            || tokens.get(i + 1).end() - tokens.get(first).start() > MAX_PASSAGE;
                }
                if (split) {
                    int start = tokens.get(first).start();
                    int end = tokens.get(i).end();
                    if (i - first + 1 >= 6 && end - start <= MAX_PASSAGE && seen.add(((long) start << 32) | end)) {
                        Set<String> terms = new LinkedHashSet<>();
                        for (int j = first; j <= i; j++) {
                            String value = tokens.get(j).value();
                            if (Fingerprints.contentWord(value)) terms.add(value);
                        }
                        String normalizedPassage = String.join(" ", tokens.subList(first, i + 1).stream().map(TextNormalizer.Token::value).toList());
                        if (terms.size() >= 4 && Fingerprints.informative(normalizedPassage)) result.add(new Segment(source, start, end, Set.copyOf(terms)));
                    }
                    first = i + 1;
                }
            }
        }
        return result;
    }
}
