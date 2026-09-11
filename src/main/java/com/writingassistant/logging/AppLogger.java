package com.writingassistant.logging;

import com.writingassistant.config.ConfigManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.*;

/**
 * Configures rotating file logging and console logging in the OS app-data folder.
 */
public class AppLogger {

    private static final int MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB per file
    private static final int ROTATING_FILE_COUNT = 5;               // Keep up to 5 rotating files
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static boolean initialized = false;
    private static FileHandler fileHandler;

    /**
     * Initializes root logger with rotating file handler in the app-data folder.
     */
    public static synchronized void init(ConfigManager configManager) {
        if (initialized) {
            return;
        }

        try {
            Path logsDir = configManager.getLogsDir();
            if (!Files.exists(logsDir)) {
                Files.createDirectories(logsDir);
            }

            String logPattern = logsDir.resolve("writing-assistant.%g.log").toString();
            fileHandler = new FileHandler(logPattern, MAX_FILE_SIZE_BYTES, ROTATING_FILE_COUNT, true);
            fileHandler.setFormatter(createFormatter());
            fileHandler.setLevel(Level.ALL);

            Logger rootLogger = Logger.getLogger("");
            // Set root logger level based on config
            Level configuredLevel = parseLogLevel(configManager.getConfig().getLogLevel());
            rootLogger.setLevel(configuredLevel);

            // Add rotating file handler
            rootLogger.addHandler(fileHandler);

            // Customize console handlers
            for (Handler handler : rootLogger.getHandlers()) {
                if (handler instanceof ConsoleHandler) {
                    handler.setFormatter(createFormatter());
                    handler.setLevel(configuredLevel);
                }
            }

            initialized = true;
            Logger.getLogger(AppLogger.class.getName()).info(
                    "Logging initialized. Rotating logs directory: " + logsDir.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Warning: Failed to initialize rotating file logger: " + e.getMessage());
        }
    }

    private static Formatter createFormatter() {
        return new Formatter() {
            @Override
            public String format(LogRecord record) {
                String timestamp = LocalDateTime.now().format(DATE_FORMAT);
                String level = record.getLevel().getName();
                String source = record.getSourceClassName() != null
                        ? record.getSourceClassName()
                        : record.getLoggerName();
                // Basename for cleaner logs
                if (source.contains(".")) {
                    source = source.substring(source.lastIndexOf('.') + 1);
                }
                String message = formatMessage(record);
                StringBuilder sb = new StringBuilder();
                sb.append(timestamp)
                  .append(" [")
                  .append(Thread.currentThread().getName())
                  .append("] ")
                  .append(String.format("%-7s", level))
                  .append(" ")
                  .append(source)
                  .append(" - ")
                  .append(message)
                  .append(System.lineSeparator());

                if (record.getThrown() != null) {
                    java.io.StringWriter sw = new java.io.StringWriter();
                    java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                    record.getThrown().printStackTrace(pw);
                    sb.append(sw);
                }
                return sb.toString();
            }
        };
    }

    private static Level parseLogLevel(String levelStr) {
        if (levelStr == null) {
            return Level.INFO;
        }
        try {
            return Level.parse(levelStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Level.INFO;
        }
    }

    public static Logger getLogger(Class<?> clazz) {
        return Logger.getLogger(clazz.getName());
    }

    public static synchronized void shutdown() {
        if (fileHandler != null) {
            fileHandler.close();
            Logger.getLogger("").removeHandler(fileHandler);
            fileHandler = null;
            initialized = false;
        }
    }
}
