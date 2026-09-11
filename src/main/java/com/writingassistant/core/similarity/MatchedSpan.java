package com.writingassistant.core.similarity;

/**
 * Represents a highlighted text span in a candidate paraphrase where close textual similarity was detected.
 */
public class MatchedSpan {

    private final int startIndex;
    private final int endIndex;
    private final String matchedText;
    private final String matchType; // e.g., "NGRAM", "LCS_BLOCK", "VERBATIM"

    public MatchedSpan(int startIndex, int endIndex, String matchedText, String matchType) {
        this.startIndex = Math.max(0, startIndex);
        this.endIndex = Math.max(this.startIndex, endIndex);
        this.matchedText = matchedText != null ? matchedText : "";
        this.matchType = matchType != null ? matchType : "NGRAM";
    }

    public int getStartIndex() {
        return startIndex;
    }

    public int getEndIndex() {
        return endIndex;
    }

    public String getMatchedText() {
        return matchedText;
    }

    public String getMatchType() {
        return matchType;
    }

    public int getLength() {
        return endIndex - startIndex;
    }

    @Override
    public String toString() {
        return "MatchedSpan{" +
                "[" + startIndex + ".." + endIndex + "]: '" + matchedText + '\'' +
                " (" + matchType + ")}";
    }
}
