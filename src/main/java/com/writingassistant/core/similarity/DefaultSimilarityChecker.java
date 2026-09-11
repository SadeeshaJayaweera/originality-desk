package com.writingassistant.core.similarity;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local, deterministic similarity checker implementing N-gram overlap, Word-LCS,
 * and Levenshtein edit distance with phrase-level span highlighting.
 * Operates 100% offline with zero network calls or API keys.
 */
public class DefaultSimilarityChecker implements SimilarityChecker {

    private static final Pattern WORD_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+");

    private final SimilarityConfig config;

    public DefaultSimilarityChecker() {
        this(SimilarityConfig.defaultConfig());
    }

    public DefaultSimilarityChecker(SimilarityConfig config) {
        this.config = (config != null) ? config : SimilarityConfig.defaultConfig();
    }

    @Override
    public SimilarityReport checkSimilarity(String sourceText, String paraphraseText) {
        if (sourceText == null) sourceText = "";
        if (paraphraseText == null) paraphraseText = "";

        String trimmedSource = sourceText.trim();
        String trimmedPara = paraphraseText.trim();

        // Edge case: empty inputs
        if (trimmedSource.isEmpty() || trimmedPara.isEmpty()) {
            return new SimilarityReport(0.0, SimilarityRisk.LOW, Collections.emptyList(), 0.0, 0.0, 0.0);
        }

        // Tokenize with character offset tracking in the paraphrase text
        List<Token> sourceTokens = tokenizeWithOffsets(trimmedSource);
        List<Token> paraTokens = tokenizeWithOffsets(trimmedPara);

        if (sourceTokens.isEmpty() || paraTokens.isEmpty()) {
            return new SimilarityReport(0.0, SimilarityRisk.LOW, Collections.emptyList(), 0.0, 0.0, 0.0);
        }

        List<String> sourceWords = sourceTokens.stream().map(Token::word).toList();
        List<String> paraWords = paraTokens.stream().map(Token::word).toList();

        // 1. Compute N-Gram Overlap (Trigrams & 4-grams)
        double nGramOverlapRatio = computeAverageNGramOverlap(sourceWords, paraWords);

        // 2. Compute Word Longest Common Subsequence (LCS) Ratio
        double lcsRatio = computeLcsRatio(sourceWords, paraWords);

        // 3. Compute Normalized Levenshtein Edit Similarity
        double editSimilarity = computeLevenshteinSimilarity(trimmedSource.toLowerCase(Locale.ROOT),
                trimmedPara.toLowerCase(Locale.ROOT));

        // 4. Compute Weighted Composite Score (0 - 100%)
        double compositeScore = (config.getWeightNGram() * nGramOverlapRatio
                + config.getWeightLcs() * lcsRatio
                + config.getWeightLevenshtein() * editSimilarity) * 100.0;

        compositeScore = Math.max(0.0, Math.min(100.0, compositeScore));

        // 5. Determine Risk Level based on Configurable Thresholds
        SimilarityRisk risk;
        if (compositeScore >= config.getHighThreshold()) {
            risk = SimilarityRisk.HIGH;
        } else if (compositeScore >= config.getModerateThreshold()) {
            risk = SimilarityRisk.MODERATE;
        } else {
            risk = SimilarityRisk.LOW;
        }

        // 6. Extract Flagged Spans for UI Highlighting
        List<MatchedSpan> matchedSpans = extractOverlappingSpans(sourceWords, paraTokens, trimmedPara);

        return new SimilarityReport(
                compositeScore,
                risk,
                matchedSpans,
                nGramOverlapRatio * 100.0,
                lcsRatio * 100.0,
                editSimilarity * 100.0
        );
    }

    /**
     * Computes average n-gram overlap across configured n-gram sizes (e.g. 3-grams and 4-grams).
     */
    public double computeAverageNGramOverlap(List<String> sourceWords, List<String> paraWords) {
        if (sourceWords.isEmpty() || paraWords.isEmpty()) {
            return 0.0;
        }

        int minN = config.getMinNGramSize();
        int maxN = config.getMaxNGramSize();

        // If passage is very short, adapt n
        if (paraWords.size() < minN) {
            return computeSingleNGramOverlap(sourceWords, paraWords, Math.max(1, paraWords.size()));
        }

        double totalOverlap = 0.0;
        int count = 0;
        for (int n = minN; n <= maxN; n++) {
            if (paraWords.size() >= n) {
                totalOverlap += computeSingleNGramOverlap(sourceWords, paraWords, n);
                count++;
            }
        }
        return count > 0 ? (totalOverlap / count) : 0.0;
    }

    /**
     * Computes n-gram overlap for a specific n-gram size:
     * Overlap = |ParaphraseNGrams ∩ SourceNGrams| / |ParaphraseNGrams|
     */
    public double computeSingleNGramOverlap(List<String> sourceWords, List<String> paraWords, int n) {
        if (paraWords.size() < n || sourceWords.size() < n) {
            return 0.0;
        }

        Set<String> sourceNGrams = new HashSet<>();
        for (int i = 0; i <= sourceWords.size() - n; i++) {
            sourceNGrams.add(String.join(" ", sourceWords.subList(i, i + n)));
        }

        int paraNGramCount = paraWords.size() - n + 1;
        int matches = 0;
        for (int i = 0; i < paraNGramCount; i++) {
            String nGram = String.join(" ", paraWords.subList(i, i + n));
            if (sourceNGrams.contains(nGram)) {
                matches++;
            }
        }

        return (double) matches / paraNGramCount;
    }

    /**
     * Computes Longest Common Subsequence (LCS) length of words relative to candidate length.
     * Catches preserved structural chains and contiguous chunks.
     */
    public double computeLcsRatio(List<String> sourceWords, List<String> paraWords) {
        if (sourceWords.isEmpty() || paraWords.isEmpty()) {
            return 0.0;
        }

        int m = sourceWords.size();
        int n = paraWords.size();

        // Memory-efficient 2-row DP
        int[][] dp = new int[2][n + 1];

        for (int i = 1; i <= m; i++) {
            int curr = i % 2;
            int prev = 1 - curr;
            String sWord = sourceWords.get(i - 1);

            for (int j = 1; j <= n; j++) {
                if (sWord.equals(paraWords.get(j - 1))) {
                    dp[curr][j] = dp[prev][j - 1] + 1;
                } else {
                    dp[curr][j] = Math.max(dp[prev][j], dp[curr][j - 1]);
                }
            }
        }

        int lcsLength = dp[m % 2][n];
        return (double) lcsLength / n;
    }

    /**
     * Computes normalized Levenshtein edit similarity between two text strings:
     * Similarity = 1.0 - (LevenshteinDistance / max(len(text1), len(text2)))
     */
    public double computeLevenshteinSimilarity(String text1, String text2) {
        if (text1.equals(text2)) {
            return 1.0;
        }
        if (text1.isEmpty() || text2.isEmpty()) {
            return 0.0;
        }

        int maxLen = Math.max(text1.length(), text2.length());
        int distance = computeLevenshteinDistance(text1, text2);
        return 1.0 - ((double) distance / maxLen);
    }

    private int computeLevenshteinDistance(String s1, String s2) {
        int m = s1.length();
        int n = s2.length();

        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];

        for (int j = 0; j <= n; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            char c1 = s1.charAt(i - 1);
            for (int j = 1; j <= n; j++) {
                char c2 = s2.charAt(j - 1);
                int cost = (c1 == c2) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            System.arraycopy(curr, 0, prev, 0, n + 1);
        }

        return prev[n];
    }

    /**
     * Extracts exact character offsets of matching word sequences (>= minSpanWords) in the candidate paraphrase.
     */
    private List<MatchedSpan> extractOverlappingSpans(List<String> sourceWords, List<Token> paraTokens, String rawParaphrase) {
        int minSpan = config.getMinSpanWords();
        if (paraTokens.size() < minSpan || sourceWords.size() < minSpan) {
            return Collections.emptyList();
        }

        // Build source word index for quick lookup
        Set<String> sourceNGrams = new HashSet<>();
        for (int i = 0; i <= sourceWords.size() - minSpan; i++) {
            sourceNGrams.add(String.join(" ", sourceWords.subList(i, i + minSpan)));
        }

        boolean[] matchedIndices = new boolean[paraTokens.size()];
        for (int i = 0; i <= paraTokens.size() - minSpan; i++) {
            List<String> window = new ArrayList<>(minSpan);
            for (int j = i; j < i + minSpan; j++) {
                window.add(paraTokens.get(j).word());
            }
            String nGram = String.join(" ", window);
            if (sourceNGrams.contains(nGram)) {
                for (int j = i; j < i + minSpan; j++) {
                    matchedIndices[j] = true;
                }
            }
        }

        // Merge contiguous marked word tokens into character spans
        List<MatchedSpan> spans = new ArrayList<>();
        int startToken = -1;

        for (int i = 0; i < matchedIndices.length; i++) {
            if (matchedIndices[i]) {
                if (startToken == -1) {
                    startToken = i;
                }
            } else {
                if (startToken != -1) {
                    int startChar = paraTokens.get(startToken).startIndex();
                    int endChar = paraTokens.get(i - 1).endIndex();
                    String matchedText = rawParaphrase.substring(startChar, Math.min(rawParaphrase.length(), endChar));
                    spans.add(new MatchedSpan(startChar, endChar, matchedText, "PHRASE_OVERLAP"));
                    startToken = -1;
                }
            }
        }

        if (startToken != -1) {
            int startChar = paraTokens.get(startToken).startIndex();
            int endChar = paraTokens.get(paraTokens.size() - 1).endIndex();
            String matchedText = rawParaphrase.substring(startChar, Math.min(rawParaphrase.length(), endChar));
            spans.add(new MatchedSpan(startChar, endChar, matchedText, "PHRASE_OVERLAP"));
        }

        return spans;
    }

    private List<Token> tokenizeWithOffsets(String text) {
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = WORD_PATTERN.matcher(text);
        while (matcher.find()) {
            String word = matcher.group().toLowerCase(Locale.ROOT);
            tokens.add(new Token(word, matcher.start(), matcher.end()));
        }
        return tokens;
    }

    public SimilarityConfig getConfig() {
        return config;
    }

    private record Token(String word, int startIndex, int endIndex) {
    }
}
