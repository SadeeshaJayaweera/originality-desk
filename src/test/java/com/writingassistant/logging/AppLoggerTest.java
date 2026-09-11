package com.writingassistant.logging;

import com.writingassistant.config.ConfigManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AppLoggerTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        AppLogger.shutdown();
    }

    @Test
    @DisplayName("AppLogger initializes rotating file handler in app-data logs directory")
    void testLoggerCreatesRotatingLogFile() throws IOException {
        ConfigManager configManager = new ConfigManager(tempDir);
        AppLogger.init(configManager);

        Logger logger = AppLogger.getLogger(AppLoggerTest.class);
        logger.info("Test log entry for rotating file handler verification.");

        AppLogger.shutdown();

        Path logsDir = configManager.getLogsDir();
        assertTrue(Files.exists(logsDir), "Logs directory should exist");

        boolean hasLogFiles = false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(logsDir, "*.log*")) {
            for (Path entry : stream) {
                if (Files.size(entry) > 0) {
                    hasLogFiles = true;
                    break;
                }
            }
        }
        assertTrue(hasLogFiles, "Log file should be created with content in the logs folder");
    }
}
