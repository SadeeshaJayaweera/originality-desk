package com.writingassistant.analysis;

import java.util.BitSet;
import java.util.List;

/** Word coverage, not character similarity; near windows contribute only beyond the exact union. */
final class OverlapCoverage {
    record Coverage(double exact, double near, double total) { }
    private OverlapCoverage() { }

    static Coverage calculate(List<TextNormalizer.Token> tokens, int limit, List<EvidenceCorpus.Hit> hits) {
        BitSet exact = new BitSet(tokens.size());
        BitSet near = new BitSet(tokens.size());
        int denominator = 0;
        for (var token : tokens) if (token.end() <= limit) denominator++;
        if (denominator == 0) return new Coverage(0, 0, 0);
        for (var hit : hits) {
            BitSet target = hit.method().equals("EXACT") ? exact : near;
            int first = lowerBound(tokens, hit.start());
            for (int i = first; i < tokens.size() && tokens.get(i).end() <= hit.end(); i++) {
                if (tokens.get(i).end() <= limit) target.set(i);
            }
        }
        near.andNot(exact);
        int covered = exact.cardinality() + near.cardinality();
        return new Coverage((double) exact.cardinality() / denominator, (double) near.cardinality() / denominator,
                (double) covered / denominator);
    }

    private static int lowerBound(List<TextNormalizer.Token> tokens, int start) {
        int low = 0;
        int high = tokens.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (tokens.get(middle).start() < start) low = middle + 1;
            else high = middle;
        }
        return low;
    }
}
