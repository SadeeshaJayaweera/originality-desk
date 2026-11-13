package com.writingassistant.ui;

import com.writingassistant.ai.GeminiClient;
import com.writingassistant.ai.GeminiStatus;
import com.writingassistant.config.AppConfig;
import com.writingassistant.config.ConfigManager;
import com.writingassistant.core.citation.CitationStyle;
import com.writingassistant.core.rewrite.RewriteMode;
import com.writingassistant.core.similarity.SimilarityConfig;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.kordamp.bootstrapfx.BootstrapFX;

/**
 * Settings dialog for configuring API keys, AI models, writing defaults, and similarity thresholds.
 */
public class SettingsDialog extends Dialog<Boolean> {

    private final ConfigManager configManager;

    private PasswordField apiKeyField;
    private TextField apiKeyVisibleField;
    private Button toggleApiKeyVisibilityBtn;
    private ComboBox<String> modelCombo;
    private Label connectionStatusLabel;
    private Button testConnectionBtn;
    private CheckBox offlineModeCheckBox;

    private ComboBox<CitationStyle> defaultStyleCombo;
    private ComboBox<RewriteMode> defaultModeCombo;

    private Spinner<Double> moderateThresholdSpinner;
    private Spinner<Double> highThresholdSpinner;
    private Spinner<Double> weightNGramSpinner;
    private Spinner<Double> weightLcsSpinner;
    private Spinner<Double> weightLevSpinner;

    private boolean isApiKeyRevealed = false;

    public SettingsDialog(Window owner) {
        this.configManager = ConfigManager.getInstance();
        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle("Settings - Writing Assistant");

        buildUI();
        loadCurrentSettings();
    }

    private void buildUI() {
        DialogPane dialogPane = getDialogPane();
        dialogPane.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());
        dialogPane.setPrefWidth(640);
        dialogPane.setPrefHeight(620);

        VBox root = new VBox(14);
        root.setPadding(new Insets(16, 20, 16, 20));

        // Header
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label titleLbl = new Label("⚙️ Application Settings");
        titleLbl.getStyleClass().addAll("dialog-title", "h4");
        header.getChildren().add(titleLbl);

        TabPane settingsTabs = new TabPane();
        settingsTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        settingsTabs.getStyleClass().add("tools-tabpane");

        // Tab 1: AI & API Configuration
        Tab aiTab = new Tab("🤖 AI & Gemini API");
        VBox aiBox = new VBox(12);
        aiBox.setPadding(new Insets(14));
        aiBox.getStyleClass().add("tab-content-container");

        Label apiSectionLbl = new Label("Gemini API Key (Required for AI Rewriting & Paraphrasing)");
        apiSectionLbl.getStyleClass().addAll("form-label-bold", "h5");

        HBox keyBox = new HBox(8);
        keyBox.setAlignment(Pos.CENTER_LEFT);
        apiKeyField = new PasswordField();
        apiKeyField.setPromptText("Enter your Gemini API key (AIzaSy...)");
        apiKeyField.setPrefWidth(380);

        apiKeyVisibleField = new TextField();
        apiKeyVisibleField.setPromptText("Enter your Gemini API key (AIzaSy...)");
        apiKeyVisibleField.setPrefWidth(380);
        apiKeyVisibleField.setVisible(false);
        apiKeyVisibleField.setManaged(false);

        // Keep fields synchronized
        apiKeyField.textProperty().bindBidirectional(apiKeyVisibleField.textProperty());

        toggleApiKeyVisibilityBtn = new Button("👁");
        toggleApiKeyVisibilityBtn.getStyleClass().addAll("btn", "btn-default", "btn-sm");
        toggleApiKeyVisibilityBtn.setOnAction(e -> toggleApiKeyVisibility());

        keyBox.getChildren().addAll(apiKeyField, apiKeyVisibleField, toggleApiKeyVisibilityBtn);

        HBox modelBox = new HBox(12);
        modelBox.setAlignment(Pos.CENTER_LEFT);
        Label modelLbl = new Label("Model:");
        modelCombo = new ComboBox<>(FXCollections.observableArrayList(
                "gemini-1.5-flash", "gemini-1.5-pro", "gemini-2.0-flash"));
        modelCombo.setPrefWidth(220);
        modelBox.getChildren().addAll(modelLbl, modelCombo);

        HBox testBox = new HBox(10);
        testBox.setAlignment(Pos.CENTER_LEFT);
        testConnectionBtn = new Button("⚡ Test Connection");
        testConnectionBtn.getStyleClass().addAll("btn", "btn-info", "btn-sm");
        testConnectionBtn.setOnAction(e -> handleTestConnection());
        connectionStatusLabel = new Label("Status: Not checked");
        connectionStatusLabel.getStyleClass().add("text-muted");
        testBox.getChildren().addAll(testConnectionBtn, connectionStatusLabel);

        Label offlineNote = new Label("Note: If unconfigured, the app runs in Offline Mode with full access to the local similarity checker and citation library.");
        offlineNote.setWrapText(true);
        offlineNote.getStyleClass().add("text-muted");

        offlineModeCheckBox = new CheckBox("Offline mode (never contact Gemini, even when a key is configured)");
        aiBox.getChildren().addAll(apiSectionLbl, keyBox, modelBox, offlineModeCheckBox, testBox, offlineNote);
        aiTab.setContent(aiBox);

        // Tab 2: Writing & Citation Defaults
        Tab generalTab = new Tab("📝 Writing Defaults");
        VBox genBox = new VBox(14);
        genBox.setPadding(new Insets(14));
        genBox.getStyleClass().add("tab-content-container");

        GridPane genGrid = new GridPane();
        genGrid.setHgap(12);
        genGrid.setVgap(10);

        defaultStyleCombo = new ComboBox<>(FXCollections.observableArrayList(CitationStyle.values()));
        defaultModeCombo = new ComboBox<>(FXCollections.observableArrayList(RewriteMode.values()));

        genGrid.add(new Label("Default Citation Style:"), 0, 0);
        genGrid.add(defaultStyleCombo, 1, 0);
        genGrid.add(new Label("Default Rewrite Mode:"), 0, 1);
        genGrid.add(defaultModeCombo, 1, 1);

        genBox.getChildren().addAll(new Label("General Preferences"), genGrid);
        generalTab.setContent(genBox);

        // Tab 3: Similarity Checker Sensitivity (Power Users)
        Tab simTab = new Tab("🔍 Similarity Sensitivity");
        VBox simBox = new VBox(12);
        simBox.setPadding(new Insets(14));
        simBox.getStyleClass().add("tab-content-container");

        Label simTitle = new Label("Tuning Local Paraphrase Similarity Thresholds & Weights");
        simTitle.getStyleClass().addAll("form-label-bold", "h5");

        GridPane simGrid = new GridPane();
        simGrid.setHgap(12);
        simGrid.setVgap(8);

        moderateThresholdSpinner = new Spinner<>(10.0, 50.0, 25.0, 1.0);
        moderateThresholdSpinner.setEditable(true);
        moderateThresholdSpinner.setPrefWidth(100);

        highThresholdSpinner = new Spinner<>(40.0, 90.0, 55.0, 1.0);
        highThresholdSpinner.setEditable(true);
        highThresholdSpinner.setPrefWidth(100);

        weightNGramSpinner = new Spinner<>(0.0, 1.0, 0.35, 0.05);
        weightNGramSpinner.setEditable(true);
        weightNGramSpinner.setPrefWidth(100);

        weightLcsSpinner = new Spinner<>(0.0, 1.0, 0.40, 0.05);
        weightLcsSpinner.setEditable(true);
        weightLcsSpinner.setPrefWidth(100);

        weightLevSpinner = new Spinner<>(0.0, 1.0, 0.25, 0.05);
        weightLevSpinner.setEditable(true);
        weightLevSpinner.setPrefWidth(100);

        simGrid.add(new Label("Moderate Risk Threshold (%):"), 0, 0);
        simGrid.add(moderateThresholdSpinner, 1, 0);
        simGrid.add(new Label("High Risk Threshold (%):"), 0, 1);
        simGrid.add(highThresholdSpinner, 1, 1);
        simGrid.add(new Label("N-Gram Overlap Weight:"), 0, 2);
        simGrid.add(weightNGramSpinner, 1, 2);
        simGrid.add(new Label("Word LCS Ratio Weight:"), 0, 3);
        simGrid.add(weightLcsSpinner, 1, 3);
        simGrid.add(new Label("Edit Distance Weight:"), 0, 4);
        simGrid.add(weightLevSpinner, 1, 4);

        Button resetDefaultsBtn = new Button("↺ Reset Sensitivity Defaults");
        resetDefaultsBtn.getStyleClass().addAll("btn", "btn-default", "btn-sm");
        resetDefaultsBtn.setOnAction(e -> resetSimilarityDefaults());

        simBox.getChildren().addAll(simTitle, simGrid, resetDefaultsBtn);
        simTab.setContent(simBox);

        settingsTabs.getTabs().addAll(aiTab, generalTab, simTab);
        root.getChildren().addAll(header, settingsTabs);
        dialogPane.setContent(root);

        // Buttons
        ButtonType saveBtnType = new ButtonType("Save Changes", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtnType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(saveBtnType, cancelBtnType);

        dialogPane.lookupButton(saveBtnType).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                validateSimilaritySettings();
                saveSettings();
            } catch (IllegalArgumentException | IllegalStateException e) {
                event.consume();
                Alert alert = new Alert(Alert.AlertType.ERROR, e.getMessage(), ButtonType.OK);
                alert.initOwner(getDialogPane().getScene().getWindow());
                alert.showAndWait();
            }
        });

        setResultConverter(buttonType -> buttonType == saveBtnType);
    }

    private void toggleApiKeyVisibility() {
        isApiKeyRevealed = !isApiKeyRevealed;
        if (isApiKeyRevealed) {
            apiKeyField.setVisible(false);
            apiKeyField.setManaged(false);
            apiKeyVisibleField.setVisible(true);
            apiKeyVisibleField.setManaged(true);
            toggleApiKeyVisibilityBtn.setText("🔒");
        } else {
            apiKeyVisibleField.setVisible(false);
            apiKeyVisibleField.setManaged(false);
            apiKeyField.setVisible(true);
            apiKeyField.setManaged(true);
            toggleApiKeyVisibilityBtn.setText("👁");
        }
    }

    private void loadCurrentSettings() {
        AppConfig config = configManager.getConfig();
        if (config.getGeminiApiKey() != null) {
            apiKeyField.setText(config.getGeminiApiKey());
        }
        modelCombo.setValue(config.getGeminiModel());
        offlineModeCheckBox.setSelected(config.isOfflineMode());

        try {
            defaultStyleCombo.setValue(CitationStyle.valueOf(config.getDefaultCitationStyle().toUpperCase()));
        } catch (Exception e) {
            defaultStyleCombo.setValue(CitationStyle.APA);
        }

        try {
            defaultModeCombo.setValue(RewriteMode.valueOf(config.getDefaultRewriteMode().toUpperCase()));
        } catch (Exception e) {
            defaultModeCombo.setValue(RewriteMode.CLARITY);
        }

        moderateThresholdSpinner.getValueFactory().setValue(config.getSimilarityModerateThreshold());
        highThresholdSpinner.getValueFactory().setValue(config.getSimilarityHighThreshold());
        weightNGramSpinner.getValueFactory().setValue(config.getSimilarityWeightNGram());
        weightLcsSpinner.getValueFactory().setValue(config.getSimilarityWeightLcs());
        weightLevSpinner.getValueFactory().setValue(config.getSimilarityWeightLevenshtein());
    }

    private void resetSimilarityDefaults() {
        moderateThresholdSpinner.getValueFactory().setValue(25.0);
        highThresholdSpinner.getValueFactory().setValue(55.0);
        weightNGramSpinner.getValueFactory().setValue(0.35);
        weightLcsSpinner.getValueFactory().setValue(0.40);
        weightLevSpinner.getValueFactory().setValue(0.25);
    }

    private void handleTestConnection() {
        if (offlineModeCheckBox.isSelected()) {
            connectionStatusLabel.setText("Offline mode is enabled; no network request was made.");
            return;
        }
        String enteredKey = apiKeyField.getText().trim();
        String chosenModel = modelCombo.getValue();

        testConnectionBtn.setDisable(true);
        connectionStatusLabel.setText("Connecting to Gemini API...");
        connectionStatusLabel.setStyle("-fx-text-fill: #38bdf8;");

        ConfigManager testConfig = new ConfigManager(configManager.getAppDataDir());
        testConfig.getConfig().setGeminiApiKey(enteredKey.isEmpty() ? null : enteredKey);
        testConfig.getConfig().setGeminiModel(chosenModel);
        testConfig.getConfig().setOfflineMode(false);
        new Thread(() -> {
            GeminiClient testClient = new GeminiClient(testConfig);
            GeminiStatus status = testClient.testConnection();
            Platform.runLater(() -> {
                if (status.isAvailable()) {
                    connectionStatusLabel.setText("✓ Connected successfully!");
                    connectionStatusLabel.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
                } else {
                    connectionStatusLabel.setText("✗ " + status.getMessage());
                    connectionStatusLabel.setStyle("-fx-text-fill: #ef4444;");
                }
                testConnectionBtn.setDisable(false);
            });
        }).start();
    }

    private void validateSimilaritySettings() {
        moderateThresholdSpinner.commitValue();
        highThresholdSpinner.commitValue();
        weightNGramSpinner.commitValue();
        weightLcsSpinner.commitValue();
        weightLevSpinner.commitValue();
        new SimilarityConfig(weightNGramSpinner.getValue(), weightLcsSpinner.getValue(), weightLevSpinner.getValue(),
                moderateThresholdSpinner.getValue(), highThresholdSpinner.getValue());
    }

    private void saveSettings() {
        AppConfig config = configManager.getConfig();
        String apiKey = apiKeyField.getText().trim();
        config.setGeminiApiKey(apiKey.isEmpty() ? null : apiKey);
        config.setOfflineMode(offlineModeCheckBox.isSelected());
        config.setGeminiModel(modelCombo.getValue());
        config.setDefaultCitationStyle(defaultStyleCombo.getValue().name());
        config.setDefaultRewriteMode(defaultModeCombo.getValue().name());

        config.setSimilarityModerateThreshold(moderateThresholdSpinner.getValue());
        config.setSimilarityHighThreshold(highThresholdSpinner.getValue());
        config.setSimilarityWeightNGram(weightNGramSpinner.getValue());
        config.setSimilarityWeightLcs(weightLcsSpinner.getValue());
        config.setSimilarityWeightLevenshtein(weightLevSpinner.getValue());

        configManager.saveConfig();
    }
}
