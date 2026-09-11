package com.writingassistant.core.rewrite;

import com.writingassistant.data.DatabaseManager;
import com.writingassistant.logging.AppLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe in-memory session cache for rewritten passages with SQLite persistence.
 * Prevents redundant API calls for identical text and mode requests.
 */
public class RewriteCache {

    private static final Logger LOGGER = AppLogger.getLogger(RewriteCache.class);
    private static final int MAX_CACHE_ENTRIES = 500;

    private final Map<String, RewriteResult> memoryCache;
    private final DatabaseManager databaseManager;

    public RewriteCache() {
        this(null);
    }

    public RewriteCache(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        // LinkedHashMap based LRU cache protected by ConcurrentHashMap or synchronized access
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
     * Caches a RewriteResult and persists to SQLite history.
     */
    public void put(String text, RewriteMode mode, RewriteResult result) {
        if (text == null || result == null) {
            return;
        }
        String key = generateKey(text, mode);
        memoryCache.put(key, result);

        // Asynchronously persist to SQLite database if databaseManager is present
        if (databaseManager != null && !result.getAlternatives().isEmpty()) {
            persistHistory(text, mode, result);
        }
    }

    private void persistHistory(String originalText, RewriteMode mode, RewriteResult result) {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO rewrite_history (id, original_text, mode, result_text, created_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)")) {
            stmt.setString(1, UUID.randomUUID().toString());
            stmt.setString(2, originalText);
            stmt.setString(3, mode.name());
            stmt.setString(4, String.join(" || ", result.getAlternatives()));
            stmt.executeUpdate();
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not persist rewrite to database history: " + e.getMessage());
        }
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
