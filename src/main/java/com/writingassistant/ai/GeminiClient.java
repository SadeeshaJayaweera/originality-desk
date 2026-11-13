package com.writingassistant.ai;

import com.google.gson.*;
import com.writingassistant.config.ConfigManager;
import com.writingassistant.logging.AppLogger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * Client for interacting with the Google Gemini API using java.net.http.HttpClient.
 * Shared across rewriting and citation-safe paraphrasing modules.
 */
public class GeminiClient {

    private static final Logger LOGGER = AppLogger.getLogger(GeminiClient.class);
    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final Gson GSON = new Gson();

    private final HttpClient httpClient;
    private final ConfigManager configManager;
    private final Duration timeout;

    public GeminiClient() {
        this(ConfigManager.getInstance(), HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build(), Duration.ofSeconds(30));
    }

    public GeminiClient(ConfigManager configManager) {
        this(configManager, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build(), Duration.ofSeconds(30));
    }

    public GeminiClient(ConfigManager configManager, HttpClient httpClient, Duration timeout) {
        this.configManager = configManager;
        this.httpClient = httpClient;
        this.timeout = timeout;
    }

    public GeminiClient(String apiKey, String model) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.timeout = Duration.ofSeconds(30);
        this.configManager = new ConfigManager();
        if (apiKey != null && !apiKey.isBlank()) {
            this.configManager.getConfig().setGeminiApiKey(apiKey.trim());
        }
        if (model != null && !model.isBlank()) {
            this.configManager.getConfig().setGeminiModel(model.trim());
        }
    }

    /**
     * Checks if a Gemini API key is currently configured.
     */
    public boolean isConfigured() {
        return configManager.isAiAvailable();
    }

    /**
     * Tests connectivity to the Gemini API.
     * Returns a GeminiStatus object indicating success or failure.
     */
    public GeminiStatus testConnection() {
        if (!isConfigured()) {
            return GeminiStatus.unconfigured(configManager.getAiUnavailableReason());
        }

        try {
            String model = getModel();
            AiResponse response = generateText("Respond with only the word 'OK' to verify connectivity.");
            if (response.isSuccessful()) {
                return GeminiStatus.connected(model);
            } else {
                return GeminiStatus.failed(response.getErrorMessage(), 0);
            }
        } catch (GeminiException e) {
            return GeminiStatus.failed(e.getMessage(), e.getStatusCode());
        } catch (Exception e) {
            LOGGER.warning("Connection test failed unexpectedly.");
            return GeminiStatus.failed("Failed to connect to Gemini API. Check network and model settings.", 0);
        }
    }

    /**
     * Generates text using the configured Gemini model.
     *
     * @param prompt User prompt text
     * @return AiResponse containing the generated text
     * @throws GeminiException if the API call fails or key is unconfigured
     */
    public AiResponse generateText(String prompt) throws GeminiException {
        return generateText(null, prompt);
    }

    /**
     * Generates text with an optional system instruction.
     *
     * @param systemInstruction System instruction context (optional)
     * @param prompt            User prompt text
     * @return AiResponse containing generated text
     * @throws GeminiException if the API call fails or key is unconfigured
     */
    public AiResponse generateText(String systemInstruction, String prompt) throws GeminiException {
        if (!isConfigured()) {
            String reason = configManager.getAiUnavailableReason();
            LOGGER.warning("Attempted AI generation while unconfigured: " + reason);
            throw new AiFeatureUnavailableException(reason);
        }

        String apiKey = configManager.getEffectiveApiKey();
        String model = getModel();
        if (!model.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw new GeminiException("Invalid Gemini model name. Check model settings.");
        }
        String endpoint = API_BASE_URL + model + ":generateContent";

        JsonObject requestBody = buildRequestBody(systemInstruction, prompt);
        String jsonPayload = GSON.toJson(requestBody);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String responseBody = response.body();

            if (statusCode >= 200 && statusCode < 300) {
                String generatedText = extractTextFromResponse(responseBody);
                return AiResponse.success(generatedText, model);
            } else {
                LOGGER.warning("Gemini API request failed (HTTP " + statusCode + ").");
                throw new GeminiException("Gemini API request failed (HTTP " + statusCode
                        + "). Check credentials, model availability, quota, and service status.", statusCode);
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.warning("Network error during Gemini API request.");
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new GeminiException("Network error communicating with Gemini API. Check your connection and retry.");
        } catch (RuntimeException e) {
            throw new GeminiException("Could not send Gemini request. Check API key and model settings.");
        }
    }

    private JsonObject buildRequestBody(String systemInstruction, String prompt) {
        JsonObject root = new JsonObject();

        if (systemInstruction != null && !systemInstruction.isBlank()) {
            JsonObject sysInstObj = new JsonObject();
            JsonArray sysParts = new JsonArray();
            JsonObject sysPart = new JsonObject();
            sysPart.addProperty("text", systemInstruction);
            sysParts.add(sysPart);
            sysInstObj.add("parts", sysParts);
            root.add("system_instruction", sysInstObj);
        }

        JsonArray contents = new JsonArray();
        JsonObject contentObj = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", prompt);
        parts.add(part);
        contentObj.add("parts", parts);
        contents.add(contentObj);
        root.add("contents", contents);

        return root;
    }

    private String extractTextFromResponse(String responseJson) throws GeminiException {
        try {
            JsonObject root = JsonParser.parseString(responseJson).getAsJsonObject();
            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates != null && candidates.size() > 0) {
                JsonObject candidate = candidates.get(0).getAsJsonObject();
                JsonObject content = candidate.getAsJsonObject("content");
                if (content != null) {
                    JsonArray parts = content.getAsJsonArray("parts");
                    if (parts != null && parts.size() > 0) {
                        return parts.get(0).getAsJsonObject().get("text").getAsString();
                    }
                }
            }
            throw new GeminiException("Empty or malformed candidate response from Gemini API.");
        } catch (Exception e) {
            if (e instanceof GeminiException) {
                throw (GeminiException) e;
            }
            throw new GeminiException("Failed to parse Gemini API response.");
        }
    }

    private String getModel() {
        if (configManager != null && configManager.getConfig() != null) {
            return configManager.getConfig().getGeminiModel();
        }
        return "gemini-1.5-flash";
    }
}
