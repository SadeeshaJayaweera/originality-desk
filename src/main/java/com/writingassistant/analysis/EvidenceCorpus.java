package com.writingassistant.analysis;

import java.util.List;

final class EvidenceCorpus {
    record Document(EvidenceEngine.Source source, TextNormalizer.NormalizedText normalized, int limit) { }
    record Hit(int source, int start, int end, int sourceStart, int sourceEnd, String method, double value) { }
    static final int MAX_MATCHES = 200;
    private EvidenceCorpus() { }

    static void limitation(List<String> limitations, String message) {
        if (!limitations.contains(message)) limitations.add(message);
    }
}
