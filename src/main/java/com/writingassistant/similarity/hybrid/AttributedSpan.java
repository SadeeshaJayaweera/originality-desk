package com.writingassistant.similarity.hybrid;

import com.writingassistant.similarity.SimilaritySpan;

import java.io.Serializable;
import java.util.Objects;

/**
 * An individual matched overlap span enriched with contextual attribution observations.
 */
public record AttributedSpan(
        SimilaritySpan span,
        boolean isQuoted,
        boolean isCited,
        String attributionCategory,
        String observationNotes
) implements Serializable, Comparable<AttributedSpan> {

    private static final long serialVersionUID = 1L;

    public static final String CATEGORY_QUOTED_AND_CITED = "QUOTED_AND_CITED";
    public static final String CATEGORY_QUOTED_UNASSIGNED = "QUOTED_WITHOUT_CITATION";
    public static final String CATEGORY_CITED_PARAPHRASE = "CITED_PARAPHRASE";
    public static final String CATEGORY_UNATTRIBUTED_EXACT = "UNATTRIBUTED_EXACT";
    public static final String CATEGORY_UNATTRIBUTED_PARAPHRASE = "UNATTRIBUTED_PARAPHRASE";

    public AttributedSpan {
        Objects.requireNonNull(span, "span must not be null");
        Objects.requireNonNull(attributionCategory, "attributionCategory must not be null");
        Objects.requireNonNull(observationNotes, "observationNotes must not be null");
    }

    public int targetStart() {
        return span.targetStart();
    }

    public int targetEnd() {
        return span.targetEnd();
    }

    public String sourceId() {
        return span.sourceId();
    }

    public String method() {
        return span.method();
    }

    public double score() {
        return span.score();
    }

    public String targetPassage() {
        return span.targetPassage();
    }

    public String sourcePassage() {
        return span.sourcePassage();
    }

    @Override
    public int compareTo(AttributedSpan o) {
        int cmp = Integer.compare(this.targetStart(), o.targetStart());
        if (cmp != 0) return cmp;
        return Integer.compare(this.targetEnd(), o.targetEnd());
    }
}
