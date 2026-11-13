package com.writingassistant.similarity.onnx;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java BERT WordPiece tokenizer generating input_ids, attention_mask,
 * and token_type_ids compliant with HuggingFace Sentence Transformers models.
 */
public class WordPieceTokenizer {

    public static final String CLS_TOKEN = "[CLS]";
    public static final String SEP_TOKEN = "[SEP]";
    public static final String UNK_TOKEN = "[UNK]";
    public static final String PAD_TOKEN = "[PAD]";

    public static final int PAD_ID = 0;
    public static final int UNK_ID = 100;
    public static final int CLS_ID = 101;
    public static final int SEP_ID = 102;

    private static final Pattern BASIC_TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+|[\\p{Punct}]");

    private final Map<String, Integer> vocab;
    private final Map<Integer, String> invVocab;
    private final int maxInputCharsPerWord;

    public record TokenizedInput(
            long[] inputIds,
            long[] attentionMask,
            long[] tokenTypeIds,
            List<String> tokens
    ) {
        public int length() {
            return inputIds.length;
        }
    }

    public WordPieceTokenizer() {
        this(createDefaultVocab(), 100);
    }

    public WordPieceTokenizer(Map<String, Integer> vocab) {
        this(vocab, 100);
    }

    public WordPieceTokenizer(Map<String, Integer> vocab, int maxInputCharsPerWord) {
        this.vocab = Objects.requireNonNull(vocab, "vocab must not be null");
        this.maxInputCharsPerWord = maxInputCharsPerWord;
        this.invVocab = new HashMap<>();
        for (Map.Entry<String, Integer> entry : vocab.entrySet()) {
            invVocab.put(entry.getValue(), entry.getKey());
        }
    }

    /**
     * Loads a standard HuggingFace vocab.txt stream.
     */
    public static WordPieceTokenizer fromStream(InputStream in) throws IOException {
        Map<String, Integer> vocab = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int id = 0;
            while ((line = reader.readLine()) != null) {
                String token = line.trim();
                if (!token.isEmpty()) {
                    vocab.put(token, id++);
                }
            }
        }
        return new WordPieceTokenizer(vocab);
    }

    /**
     * Tokenizes an input string into token IDs, attention mask, and token type IDs,
     * adding [CLS] and [SEP] tokens and padding or truncating to maxSeqLength.
     */
    public TokenizedInput encode(String text, int maxSeqLength) {
        if (maxSeqLength < 3) throw new IllegalArgumentException("maxSeqLength must be at least 3");
        if (text == null) text = "";

        List<String> subwords = tokenizeToSubwords(text);

        // Truncate if necessary (leaving room for [CLS] and [SEP])
        int maxTokens = maxSeqLength - 2;
        if (subwords.size() > maxTokens) {
            subwords = subwords.subList(0, maxTokens);
        }

        List<String> fullTokens = new ArrayList<>(subwords.size() + 2);
        fullTokens.add(CLS_TOKEN);
        fullTokens.addAll(subwords);
        fullTokens.add(SEP_TOKEN);

        long[] inputIds = new long[maxSeqLength];
        long[] attentionMask = new long[maxSeqLength];
        long[] tokenTypeIds = new long[maxSeqLength]; // All 0s for single passage

        for (int i = 0; i < maxSeqLength; i++) {
            if (i < fullTokens.size()) {
                String token = fullTokens.get(i);
                inputIds[i] = vocab.getOrDefault(token, UNK_ID);
                attentionMask[i] = 1L;
            } else {
                inputIds[i] = PAD_ID;
                attentionMask[i] = 0L;
            }
            tokenTypeIds[i] = 0L;
        }

        return new TokenizedInput(inputIds, attentionMask, tokenTypeIds, fullTokens);
    }

    /**
     * Splits text into WordPiece subwords.
     */
    public List<String> tokenizeToSubwords(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();

        List<String> result = new ArrayList<>();
        Matcher matcher = BASIC_TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));

        while (matcher.find()) {
            String word = matcher.group();
            if (word.length() > maxInputCharsPerWord) {
                result.add(UNK_TOKEN);
                continue;
            }

            int start = 0;
            boolean isBad = false;
            List<String> subTokens = new ArrayList<>();

            while (start < word.length()) {
                int end = word.length();
                String curSubstr = null;

                while (start < end) {
                    String sub = word.substring(start, end);
                    if (start > 0) sub = "##" + sub;
                    if (vocab.containsKey(sub)) {
                        curSubstr = sub;
                        break;
                    }
                    end--;
                }

                if (curSubstr == null) {
                    isBad = true;
                    break;
                }

                subTokens.add(curSubstr);
                start = end;
            }

            if (isBad) {
                result.add(UNK_TOKEN);
            } else {
                result.addAll(subTokens);
            }
        }

        return result;
    }

    public int getVocabSize() {
        return vocab.size();
    }

    public boolean hasToken(String token) {
        return vocab.containsKey(token);
    }

    private static Map<String, Integer> createDefaultVocab() {
        Map<String, Integer> v = new HashMap<>();
        v.put(PAD_TOKEN, PAD_ID);
        v.put(UNK_TOKEN, UNK_ID);
        v.put(CLS_TOKEN, CLS_ID);
        v.put(SEP_TOKEN, SEP_ID);

        int nextId = 103;
        // Basic punctuation & symbols
        String asciiPunct = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";
        for (char c : asciiPunct.toCharArray()) {
            v.put(String.valueOf(c), nextId++);
        }

        // Single characters and digits (both initial and subword ##)
        for (char c = '0'; c <= '9'; c++) {
            v.put(String.valueOf(c), nextId++);
            v.put("##" + c, nextId++);
        }
        for (char c = 'a'; c <= 'z'; c++) {
            v.put(String.valueOf(c), nextId++);
            v.put("##" + c, nextId++);
        }

        // Common academic subwords and tokens
        String[] common = {
                "the", "of", "and", "in", "to", "a", "is", "that", "for", "it", "as", "was", "with",
                "be", "by", "on", "not", "he", "i", "this", "are", "or", "his", "from", "at", "which",
                "but", "more", "an", "they", "we", "learning", "model", "deep", "neural", "network",
                "networks", "data", "paper", "method", "methods", "analysis", "result", "results",
                "using", "study", "research", "performance", "system", "text", "document", "similarity",
                "paraphrase", "training", "academic", "university", "author", "authors", "original",
                "source", "citation", "evaluation", "algorithm", "attention", "transformer", "evidence",
                "##s", "##ed", "##ing", "##ly", "##er", "##al", "##tion", "##ation", "##ment", "##ic",
                "##ive", "##able", "##ity", "##ous", "##ize", "##ise", "##est", "##ance", "##ence"
        };
        for (String w : common) {
            v.put(w, nextId++);
        }

        return v;
    }
}
