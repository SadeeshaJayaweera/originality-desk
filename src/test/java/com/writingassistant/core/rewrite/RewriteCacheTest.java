package com.writingassistant.core.rewrite;

import com.writingassistant.data.DatabaseManager;
import com.writingassistant.data.SqliteDocumentDao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RewriteCacheTest {
    @Test
    void cacheNeverWritesUnchosenAlternativesToHistory(@TempDir Path tempDir) throws Exception {
        DatabaseManager manager = new DatabaseManager(tempDir.resolve("cache.db")) {
            @Override
            public Connection getConnection() throws SQLException {
                for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
                    if (frame.getClassName().equals(RewriteCache.class.getName())) {
                        fail("Session cache must not attempt database persistence");
                    }
                }
                return super.getConnection();
            }
        };
        RewriteCache cache = new RewriteCache(manager);
        RewriteResult result = new RewriteResult("Original", RewriteMode.CLARITY, List.of("Alternative"));
        assertDoesNotThrow(() -> cache.put("Original", RewriteMode.CLARITY, result));
        assertSame(result, cache.get("Original", RewriteMode.CLARITY).orElseThrow());
        assertTrue(new SqliteDocumentDao(manager).getRewriteHistory(null).isEmpty());
    }
}
