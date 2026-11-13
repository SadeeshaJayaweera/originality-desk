package com.writingassistant.analysis;

import java.util.List;

public final class EvidenceCorpus {
    public record Document(EvidenceEngine.Source source, TextNormalizer.NormalizedText normalized, int limit) { }
    public record Hit(int source, int start, int end, int sourceStart, int sourceEnd, String method, double value) { }
    public static final int MAX_MATCHES = 200;
    private EvidenceCorpus() { }

    public static void limitation(List<String> limitations, String message) {
        if (!limitations.contains(message)) limitations.add(message);
    }
}
