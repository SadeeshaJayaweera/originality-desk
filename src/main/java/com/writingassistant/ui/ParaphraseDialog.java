package com.writingassistant.ui;

import com.writingassistant.ai.AiFeatureUnavailableException;
import com.writingassistant.ai.GeminiClient;
import com.writingassistant.config.ConfigManager;
import com.writingassistant.core.citation.*;
import com.writingassistant.data.CitationLibrary;
import com.writingassistant.data.DatabaseManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Window;

import org.kordamp.bootstrapfx.BootstrapFX;

import java.util.List;

/**
 * Modal dialog for paraphrasing source quotes with metadata attribution and citation library integration.
 */
public class ParaphraseDialog extends Dialog<CitedParaphrase> {

    private final DefaultCitationEngine citationEngine;
    private final CitationLibrary citationLibrary;

    private TextArea quoteArea;
    private ComboBox<SourceQuote> existingSourceCombo;
    private TextField authorField;
    private TextField yearField;
    private TextField titleField;
    private TextField pubField;
    private TextField pageField;
    private TextField urlField;
    private ComboBox<SourceType> sourceTypeCombo;
    private ComboBox<CitationStyle> styleCombo;

    private TextArea paraphraseResultArea;
    private Label inTextPreviewLabel;
    private Label bibPreviewLabel;
    private Label statusLabel;
    private Button generateBtn;

    public ParaphraseDialog(Window owner, CitationStyle defaultStyle) {
        this.citationEngine = new DefaultCitationEngine(new GeminiClient(ConfigManager.getInstance()));
        this.citationLibrary = new CitationLibrary(DatabaseManager.getInstance());

        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle("Paraphrase Source Quote with Citation");

        buildUI(defaultStyle);
        loadExistingSources();
    }

    private void buildUI(CitationStyle defaultStyle) {
        DialogPane dialogPane = getDialogPane();
        dialogPane.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());
        dialogPane.setPrefWidth(720);
        dialogPane.setPrefHeight(640);

        VBox root = new VBox(14);
        root.setPadding(new Insets(16, 20, 16, 20));

        // Header Banner
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox titleBox = new VBox(2);
        Label titleLbl = new Label("📚 Paraphrase a Source Quote");
        titleLbl.getStyleClass().addAll("h4");
        Label subtitleLbl = new Label("Restructures quotes ethically and formats in-text citations & bibliography entries.");
        subtitleLbl.getStyleClass().addAll("text-muted");
        titleBox.getChildren().addAll(titleLbl, subtitleLbl);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label badge = new Label("Phase 2 • Mandatory Citation");
        badge.getStyleClass().addAll("lbl", "lbl-success");
        header.getChildren().addAll(titleBox, spacer, badge);

        // Section 1: Source Quote Input
        VBox quoteBox = new VBox(4);
        Label quoteLbl = new Label("1. Original Quoted Passage:");
        quoteLbl.getStyleClass().add("h5");
        quoteArea = new TextArea();
        quoteArea.setPromptText("Paste the exact source quotation here...");
        quoteArea.setPrefRowCount(3);
        quoteArea.setWrapText(true);
        quoteBox.getChildren().addAll(quoteLbl, quoteArea);

        // Section 2: Metadata & Saved Sources
        VBox metaBox = new VBox(8);
        HBox sourceSelectBox = new HBox(10);
        sourceSelectBox.setAlignment(Pos.CENTER_LEFT);
        Label savedLbl = new Label("2. Citation Metadata:");
        savedLbl.getStyleClass().add("h5");

        existingSourceCombo = new ComboBox<>();
        existingSourceCombo.setPromptText("Select a previously used source (optional)...");
        existingSourceCombo.setPrefWidth(320);
        existingSourceCombo.setOnAction(e -> onSourceSelected(existingSourceCombo.getValue()));

        Button clearSourceBtn = new Button("New Source");
        clearSourceBtn.getStyleClass().addAll("btn", "btn-default", "btn-xs");
        clearSourceBtn.setOnAction(e -> clearMetadataFields());

        sourceSelectBox.getChildren().addAll(savedLbl, existingSourceCombo, clearSourceBtn);

        // Grid for fields
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);

        authorField = new TextField();
        authorField.setPromptText("e.g. Daniel Kahneman or Kahneman, D.");
        yearField = new TextField();
        yearField.setPromptText("e.g. 2011");
        yearField.setPrefWidth(80);
        titleField = new TextField();
        titleField.setPromptText("e.g. Thinking, Fast and Slow");
        pubField = new TextField();
        pubField.setPromptText("e.g. Farrar, Straus and Giroux or Journal of AI");

        sourceTypeCombo = new ComboBox<>(FXCollections.observableArrayList(SourceType.values()));
        sourceTypeCombo.setValue(SourceType.BOOK);

        pageField = new TextField();
        pageField.setPromptText("e.g. 62 or 45-48");
        urlField = new TextField();
        urlField.setPromptText("e.g. https://doi.org/... (optional)");

        styleCombo = new ComboBox<>(FXCollections.observableArrayList(CitationStyle.values()));
        styleCombo.setValue(defaultStyle != null ? defaultStyle : CitationStyle.APA);

        grid.add(new Label("Author(s):"), 0, 0);
        grid.add(authorField, 1, 0);
        grid.add(new Label("Year:"), 2, 0);
        grid.add(yearField, 3, 0);

        grid.add(new Label("Title:"), 0, 1);
        grid.add(titleField, 1, 1);
        grid.add(new Label("Type:"), 2, 1);
        grid.add(sourceTypeCombo, 3, 1);

        grid.add(new Label("Publication:"), 0, 2);
        grid.add(pubField, 1, 2);
        grid.add(new Label("Page(s):"), 2, 2);
        grid.add(pageField, 3, 2);

        grid.add(new Label("URL / DOI:"), 0, 3);
        grid.add(urlField, 1, 3);
        grid.add(new Label("Style:"), 2, 3);
        grid.add(styleCombo, 3, 3);

        ColumnConstraints col1 = new ColumnConstraints(80);
        ColumnConstraints col2 = new ColumnConstraints(260);
        ColumnConstraints col3 = new ColumnConstraints(65);
        ColumnConstraints col4 = new ColumnConstraints(210);
        grid.getColumnConstraints().addAll(col1, col2, col3, col4);

        metaBox.getChildren().addAll(sourceSelectBox, grid);

        // Section 3: Generate & Result Preview
        VBox resultBox = new VBox(6);
        HBox actionRow = new HBox(12);
        actionRow.setAlignment(Pos.CENTER_LEFT);
        generateBtn = new Button("⚡ Generate Restructured Paraphrase");
        generateBtn.getStyleClass().addAll("btn", "btn-success");
        generateBtn.setOnAction(e -> handleGenerate());

        statusLabel = new Label();
        statusLabel.getStyleClass().add("text-muted");
        actionRow.getChildren().addAll(generateBtn, statusLabel);

        paraphraseResultArea = new TextArea();
        paraphraseResultArea.setPromptText("Paraphrased result will appear here...");
        paraphraseResultArea.setPrefRowCount(3);
        paraphraseResultArea.setWrapText(true);

        VBox previewBox = new VBox(4);
        previewBox.getStyleClass().addAll("panel", "panel-default", "panel-body");
        inTextPreviewLabel = new Label("In-Text Citation: (will appear here)");
        inTextPreviewLabel.getStyleClass().add("text-primary");
        bibPreviewLabel = new Label("Reference Entry: (will appear here)");
        bibPreviewLabel.getStyleClass().add("text-muted");
        previewBox.getChildren().addAll(inTextPreviewLabel, bibPreviewLabel);

        resultBox.getChildren().addAll(actionRow, paraphraseResultArea, previewBox);

        root.getChildren().addAll(header, quoteBox, metaBox, resultBox);
        dialogPane.setContent(root);

        // Buttons
        ButtonType insertBtnType = new ButtonType("Insert into Document", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtnType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(insertBtnType, cancelBtnType);

        setResultConverter(buttonType -> {
            if (buttonType == insertBtnType) {
                return buildResult();
            }
            return null;
        });
    }

    private void loadExistingSources() {
        try {
            List<SourceQuote> sources = citationLibrary.findAllSources();
            existingSourceCombo.setItems(FXCollections.observableArrayList(sources));
            existingSourceCombo.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(SourceQuote item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(item.getAuthor() + " (" + item.getYear() + ") - " + item.getTitle());
                    }
                }
            });
            existingSourceCombo.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(SourceQuote item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText("Select a previously used source...");
                    } else {
                        setText(item.getAuthor() + " (" + item.getYear() + ")");
                    }
                }
            });
        } catch (Exception ignored) {
        }
    }

    private void onSourceSelected(SourceQuote source) {
        if (source != null) {
            authorField.setText(source.getAuthor() != null ? source.getAuthor() : "");
            yearField.setText(source.getYear() != null ? source.getYear() : "");
            titleField.setText(source.getTitle() != null ? source.getTitle() : "");
            pubField.setText(source.getPublication() != null ? source.getPublication() : "");
            pageField.setText(source.getPageNumber() != null ? source.getPageNumber() : "");
            urlField.setText(source.getUrlOrDoi() != null ? source.getUrlOrDoi() : "");
            sourceTypeCombo.setValue(source.getSourceType());
            if (source.getQuote() != null && quoteArea.getText().isBlank()) {
                quoteArea.setText(source.getQuote());
            }
        }
    }

    private void clearMetadataFields() {
        existingSourceCombo.setValue(null);
        authorField.clear();
        yearField.clear();
        titleField.clear();
        pubField.clear();
        pageField.clear();
        urlField.clear();
        sourceTypeCombo.setValue(SourceType.BOOK);
    }

    private void handleGenerate() {
        String quote = quoteArea.getText().trim();
        if (quote.isEmpty()) {
            statusLabel.setText("Please enter a source quote to paraphrase.");
            return;
        }

        SourceQuote currentSource = buildSourceQuote();
        CitationStyle style = styleCombo.getValue();

        generateBtn.setDisable(true);
        statusLabel.setText("Generating restructured paraphrase...");

        new Thread(() -> {
            try {
                CitedParaphrase result = citationEngine.paraphraseWithCitation(currentSource, style);
                Platform.runLater(() -> {
                    paraphraseResultArea.setText(result.getParaphrasedText());
                    inTextPreviewLabel.setText("In-Text Citation: " + result.getInTextCitation());
                    bibPreviewLabel.setText("Reference Entry: " + result.getBibliographyEntry());
                    statusLabel.setText("Paraphrase generated successfully.");
                    generateBtn.setDisable(false);
                });
            } catch (AiFeatureUnavailableException e) {
                Platform.runLater(() -> {
                    statusLabel.setText("AI Unavailable: Set GEMINI_API_KEY in config to paraphrase.");
                    generateBtn.setDisable(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error: " + e.getMessage());
                    generateBtn.setDisable(false);
                });
            }
        }).start();
    }

    private SourceQuote buildSourceQuote() {
        SourceQuote sq = new SourceQuote(
                quoteArea.getText().trim(),
                authorField.getText().trim(),
                yearField.getText().trim(),
                titleField.getText().trim(),
                pubField.getText().trim(),
                pageField.getText().trim(),
                urlField.getText().trim(),
                sourceTypeCombo.getValue()
        );
        if (existingSourceCombo.getValue() != null && existingSourceCombo.getValue().getId() != null) {
            sq.setId(existingSourceCombo.getValue().getId());
        }
        return sq;
    }

    private CitedParaphrase buildResult() {
        SourceQuote source = buildSourceQuote();
        CitationStyle style = styleCombo.getValue();

        // Persist source into SQLite library
        try {
            citationLibrary.save(source);
        } catch (Exception ignored) {
        }

        String inText = CitationFormatter.formatInText(source, style);
        String bib = CitationFormatter.formatBibliographyEntry(source, style);
        String paraphrase = paraphraseResultArea.getText().trim();
        if (paraphrase.isEmpty()) {
            paraphrase = quoteArea.getText().trim();
        }

        return new CitedParaphrase(source.getQuote(), paraphrase, inText, bib, style, source);
    }
}
