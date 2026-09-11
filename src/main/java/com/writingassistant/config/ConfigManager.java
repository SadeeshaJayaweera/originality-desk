package com.writingassistant.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages configuration loading, persistence, and OS-appropriate app-data folder resolution.
 */
public class ConfigManager {

    private static final Logger LOGGER = Logger.getLogger(ConfigManager.class.getName());
    private static final String CONFIG_FILE_NAME = "config.json";
    private static final String APP_DIR_NAME_UNIX = ".writing-assistant";
    private static final String APP_DIR_NAME_WIN = "WritingAssistant";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static ConfigManager instance;

    private final Path appDataDir;
    private final Path configFile;
    private AppConfig config;

    /**
     * Initializes ConfigManager with the default OS app-data directory.
     */
    public ConfigManager() {
        this(resolveDefaultAppDataDir());
    }

    /**
     * Initializes ConfigManager with a custom app-data directory (for testing or custom profiles).
     *
     * @param customAppDataDir base path for application configuration and storage
     */
    public ConfigManager(Path customAppDataDir) {
        this.appDataDir = customAppDataDir;
        this.configFile = this.appDataDir.resolve(CONFIG_FILE_NAME);
        ensureAppDataDirectoryExists();
        loadConfig();
    }

    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    public static synchronized void setInstance(ConfigManager customInstance) {
        instance = customInstance;
    }

    /**
     * Resolves the OS-appropriate application data directory.
     * - Windows: %APPDATA%\WritingAssistant or ~/.writing-assistant fallback
     * - macOS / Linux / other: ~/.writing-assistant
     */
    public static Path resolveDefaultAppDataDir() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        String userHome = System.getProperty("user.home", ".");

        if (osName.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Paths.get(appData, APP_DIR_NAME_WIN);
            }
        }
        return Paths.get(userHome, APP_DIR_NAME_UNIX);
    }

    private void ensureAppDataDirectoryExists() {
        try {
            if (!Files.exists(appDataDir)) {
                Files.createDirectories(appDataDir);
            }
            Path logsDir = getLogsDir();
            if (!Files.exists(logsDir)) {
                Files.createDirectories(logsDir);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to create app data directory at " + appDataDir, e);
        }
    }

    /**
     * Loads the configuration from config.json, or creates default configuration if absent.
     */
    public synchronized void loadConfig() {
        if (Files.exists(configFile)) {
            try (Reader reader = Files.newBufferedReader(configFile)) {
                this.config = GSON.fromJson(reader, AppConfig.class);
                if (this.config == null) {
                    this.config = new AppConfig();
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error reading config file from " + configFile + ". Using defaults.", e);
                this.config = new AppConfig();
            }
        } else {
            this.config = new AppConfig();
            saveConfig();
        }
    }

    /**
     * Persists the current configuration to config.json.
     */
    public synchronized void saveConfig() {
        try {
            ensureAppDataDirectoryExists();
            try (Writer writer = Files.newBufferedWriter(configFile)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to write config file to " + configFile, e);
        }
    }

    /**
     * Gets the effective Gemini API key, checking config.json first, then the GEMINI_API_KEY environment variable.
     *
     * @return the API key or null if not configured
     */
    public String getEffectiveApiKey() {
        if (config != null && config.getGeminiApiKey() != null && !config.getGeminiApiKey().isBlank()) {
            return config.getGeminiApiKey().trim();
        }
        String envKey = System.getenv("GEMINI_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey.trim();
        }
        return null;
    }

    /**
     * Checks if AI-powered features (rewriting, paraphrasing) can be used.
     */
    public boolean isAiAvailable() {
        String key = getEffectiveApiKey();
        return key != null && !key.isBlank();
    }

    /**
     * Explains why AI features are unavailable and clarifies that offline similarity checking remains active.
     */
    public String getAiUnavailableReason() {
        if (isAiAvailable()) {
            return null;
        }
        return "Gemini API key is not configured. Add your key to '" + configFile.toAbsolutePath()
                + "' or set the GEMINI_API_KEY environment variable to enable AI rewriting and paraphrasing. "
                + "Offline similarity checking and local citation tools remain fully functional.";
    }

    public Path getAppDataDir() {
        return appDataDir;
    }

    public Path getConfigFile() {
        return configFile;
    }

    public Path getLogsDir() {
        return appDataDir.resolve("logs");
    }

    public Path getDatabasePath() {
        return appDataDir.resolve("writing_assistant.db");
    }

    public AppConfig getConfig() {
        return config;
    }
}
