package com.writingassistant.core.rewrite;

import com.writingassistant.data.DatabaseManager;
import com.writingassistant.logging.AppLogger;


import java.util.*;

import java.util.logging.Logger;

/**
 * Thread-safe in-memory session cache for rewritten passages.
 * Prevents redundant API calls for identical text and mode requests.
 */
public class RewriteCache {

    private static final Logger LOGGER = AppLogger.getLogger(RewriteCache.class);
    private static final int MAX_CACHE_ENTRIES = 500;

    private final Map<String, RewriteResult> memoryCache;


    public RewriteCache() {
        this(null);
    }

    public RewriteCache(DatabaseManager databaseManager) {
        // Retain the legacy constructor; only accepted rewrites belong in document history.
        this.memoryCache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, RewriteResult> eldest) {
                return size() > MAX_CACHE_ENTRIES;
            }
        });
    }

    /**
     * Generates a normalized cache key for text and mode.
     */
    public static String generateKey(String text, RewriteMode mode) {
        if (text == null) {
            text = "";
        }
        String modeKey = (mode != null) ? mode.name() : "CLARITY";
        // Normalize whitespace and trim
        String normalizedText = text.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        return modeKey + ":" + normalizedText;
    }

    /**
     * Retrieves a cached RewriteResult if available.
     */
    public Optional<RewriteResult> get(String text, RewriteMode mode) {
        String key = generateKey(text, mode);
        RewriteResult result = memoryCache.get(key);
        if (result != null) {
            LOGGER.fine("Cache hit for mode=" + mode);
            return Optional.of(result);
        }
        return Optional.empty();
    }

    /**
     * Caches generated alternatives without recording an unchosen rewrite in document history.
     */
    public void put(String text, RewriteMode mode, RewriteResult result) {
        if (text == null || result == null) {
            return;
        }
        String key = generateKey(text, mode);
        memoryCache.put(key, result);

    }


    public boolean contains(String text, RewriteMode mode) {
        return memoryCache.containsKey(generateKey(text, mode));
    }

    public void clear() {
        memoryCache.clear();
    }

    public int size() {
        return memoryCache.size();
    }
}
