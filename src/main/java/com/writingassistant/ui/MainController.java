package com.writingassistant.ui;

import com.writingassistant.ai.AiFeatureUnavailableException;
import com.writingassistant.ai.GeminiClient;
import com.writingassistant.config.AppConfig;
import com.writingassistant.config.ConfigManager;
import com.writingassistant.core.citation.*;
import com.writingassistant.core.rewrite.DefaultRewriteEngine;
import com.writingassistant.core.rewrite.RewriteMode;
import com.writingassistant.core.rewrite.RewriteResult;
import com.writingassistant.core.similarity.DefaultSimilarityChecker;
import com.writingassistant.core.similarity.MatchedSpan;
import com.writingassistant.core.similarity.SimilarityReport;
import com.writingassistant.core.similarity.SimilarityRisk;
import com.writingassistant.data.*;
import com.writingassistant.logging.AppLogger;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main window controller coordinating the editor, rewriting engine, citation-safe paraphraser,
 * offline similarity safeguard, and persistent SQLite document / bibliography tracking.
 */
public class MainController {

    private static final Logger LOGGER = AppLogger.getLogger(MainController.class);

    private final DefaultRewriteEngine rewriteEngine;
    private final DefaultSimilarityChecker similarityChecker;
    private final CitationLibrary citationLibrary;
    private final DocumentDao documentDao;
    private final ConfigManager configManager;

    private DocumentRecord currentDocument;

    // --- FXML Controls ---
    @FXML private TextArea documentEditor;
    @FXML private Label wordCountLabel;
    @FXML private Label charCountLabel;
    @FXML private Label aiStatusIndicator;
    @FXML private Label currentDocLabel;
    @FXML private ComboBox<CitationStyle> globalStyleCombo;
    @FXML private MenuButton recentDocsMenuBtn;

    // --- Rewrite Panel (Phase 1) ---
    @FXML private ToggleGroup rewriteModeGroup;
    @FXML private RadioButton radioTighten;
    @FXML private RadioButton radioClarity;
    @FXML private RadioButton radioFormal;
    @FXML private RadioButton radioCasual;
    @FXML private RadioButton radioGrammar;
    @FXML private VBox rewriteAlternativesContainer;
    @FXML private Button executeRewriteBtn;
    @FXML private Label rewriteStatusLabel;

    // --- Similarity Panel (Phase 3) ---
    @FXML private TextArea simSourceArea;
    @FXML private TextArea simParaphraseArea;
    @FXML private Button checkSimilarityBtn;
    @FXML private VBox simReportCard;
    @FXML private Label simScoreLabel;
    @FXML private Label simRiskBadge;
    @FXML private Label simNgramLabel;
    @FXML private Label simLcsLabel;
    @FXML private Label simEditLabel;
    @FXML private Label simGuidanceLabel;
    @FXML private TextFlow simHighlightedPreview;

    // --- Bibliography Panel ---
    @FXML private ListView<SourceQuote> bibSourcesListView;
    @FXML private TextArea bibFormattedPreview;
    @FXML private Button appendBibToDocBtn;

    public MainController() {
        this.configManager = ConfigManager.getInstance();
        this.rewriteEngine = new DefaultRewriteEngine(new GeminiClient(configManager));
        this.similarityChecker = new DefaultSimilarityChecker();
        this.citationLibrary = new CitationLibrary(DatabaseManager.getInstance());
        this.documentDao = new SqliteDocumentDao(DatabaseManager.getInstance());
        this.currentDocument = new DocumentRecord("Untitled Document", null);
    }

    @FXML
    public void initialize() {
        setupGlobalControls();
        setupEditorListeners();
        setupRewritePanel();
        setupBibliographyPanel();
        updateAiStatusIndicator();
        saveCurrentDocumentRecord();
        refreshRecentDocumentsMenu();
    }

    private void setupGlobalControls() {
        globalStyleCombo.setItems(FXCollections.observableArrayList(CitationStyle.values()));
        String defaultStyle = configManager.getConfig().getDefaultCitationStyle();
        try {
            globalStyleCombo.setValue(CitationStyle.valueOf(defaultStyle.toUpperCase()));
        } catch (Exception e) {
            globalStyleCombo.setValue(CitationStyle.APA);
        }
        globalStyleCombo.setOnAction(e -> onCitationStyleChanged());
        updateDocLabel();
    }

    private void setupEditorListeners() {
        documentEditor.textProperty().addListener((obs, oldVal, newVal) -> updateCounts(newVal));
        updateCounts(documentEditor.getText());

        documentEditor.selectionProperty().addListener((obs, oldSel, newSel) -> {
            String selected = documentEditor.getSelectedText();
            if (selected != null && !selected.isBlank() && simParaphraseArea != null && simParaphraseArea.getText().isBlank()) {
                simParaphraseArea.setText(selected.trim());
            }
        });
    }

    private void updateCounts(String text) {
        if (text == null || text.isBlank()) {
            wordCountLabel.setText("0 words");
            charCountLabel.setText("0 chars");
            return;
        }
        int chars = text.length();
        String[] words = text.trim().split("\\s+");
        int wordCount = words.length;
        wordCountLabel.setText(wordCount + " words");
        charCountLabel.setText(chars + " chars");
    }

    private void setupRewritePanel() {
        if (rewriteModeGroup != null && rewriteModeGroup.getSelectedToggle() == null) {
            radioClarity.setSelected(true);
        }
    }

    private void setupBibliographyPanel() {
        refreshBibliographyList();
        bibSourcesListView.setCellFactory(lv -> new ListCell<>() {
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
    }

    private void updateAiStatusIndicator() {
        if (configManager.isAiAvailable()) {
            aiStatusIndicator.setText("● AI Engine: Active (" + configManager.getConfig().getGeminiModel() + ")");
            aiStatusIndicator.setStyle("-fx-text-fill: #10b981;");
        } else {
            aiStatusIndicator.setText("○ AI Engine: Disabled (Offline mode only)");
            aiStatusIndicator.setStyle("-fx-text-fill: #f59e0b;");
        }
    }

    private void updateDocLabel() {
        if (currentDocLabel != null) {
            currentDocLabel.setText(currentDocument.getTitle() + (currentDocument.getFilePath() != null ? " • " + currentDocument.getFilePath() : ""));
        }
    }

    private void saveCurrentDocumentRecord() {
        try {
            currentDocument.setLastOpened(System.currentTimeMillis());
            documentDao.save(currentDocument);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to persist document state", e);
        }
    }

    private void refreshRecentDocumentsMenu() {
        if (recentDocsMenuBtn == null) return;
        try {
            List<DocumentRecord> recent = documentDao.findAllRecent(8);
            recentDocsMenuBtn.getItems().clear();
            if (recent.isEmpty()) {
                MenuItem noneItem = new MenuItem("No recent documents");
                noneItem.setDisable(true);
                recentDocsMenuBtn.getItems().add(noneItem);
                return;
            }
            for (DocumentRecord doc : recent) {
                MenuItem item = new MenuItem(doc.getTitle() + (doc.getFilePath() != null ? " (" + doc.getFilePath() + ")" : ""));
                item.setOnAction(e -> loadDocumentRecord(doc));
                recentDocsMenuBtn.getItems().add(item);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load recent documents", e);
        }
    }

    private void loadDocumentRecord(DocumentRecord doc) {
        if (doc == null) return;
        this.currentDocument = doc;
        this.currentDocument.setLastOpened(System.currentTimeMillis());
        saveCurrentDocumentRecord();
        updateDocLabel();

        if (doc.getFilePath() != null && new File(doc.getFilePath()).exists()) {
            try {
                String content = DocxService.importDocx(Path.of(doc.getFilePath()));
                documentEditor.setText(content);
            } catch (Exception e) {
                showError("Open Failed", "Could not load document: " + e.getMessage());
            }
        }
        refreshBibliographyList();
        refreshRecentDocumentsMenu();
    }

    // =========================================================================
    // Phase 1 Actions: Style Rewriting
    // =========================================================================

    @FXML
    private void handleExecuteRewrite() {
        String selection = documentEditor.getSelectedText();
        if (selection == null || selection.isBlank()) {
            rewriteStatusLabel.setText("Please select a sentence in the editor to rewrite.");
            return;
        }

        RewriteMode mode = getSelectedRewriteMode();
        executeRewriteBtn.setDisable(true);
        rewriteStatusLabel.setText("Generating " + mode.getDisplayName() + " alternatives...");
        rewriteAlternativesContainer.getChildren().clear();

        new Thread(() -> {
            try {
                RewriteResult result = rewriteEngine.rewrite(selection, mode);
                Platform.runLater(() -> {
                    displayRewriteAlternatives(selection, mode, result);
                    rewriteStatusLabel.setText("Click an option to replace selected text in the document.");
                    executeRewriteBtn.setDisable(false);
                });
            } catch (AiFeatureUnavailableException e) {
                Platform.runLater(() -> {
                    rewriteStatusLabel.setText("AI Offline: API key not set in config.json.");
                    executeRewriteBtn.setDisable(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    rewriteStatusLabel.setText("Error: " + e.getMessage());
                    executeRewriteBtn.setDisable(false);
                });
            }
        }).start();
    }

    private void displayRewriteAlternatives(String originalSelection, RewriteMode mode, RewriteResult result) {
        rewriteAlternativesContainer.getChildren().clear();
        int idx = 1;

        for (String alt : result.getAlternatives()) {
            VBox card = new VBox(8);
            card.getStyleClass().addAll("panel", "panel-default", "panel-body");

            HBox header = new HBox(8);
            header.setAlignment(Pos.CENTER_LEFT);
            Label numLbl = new Label("Option " + idx++);
            numLbl.getStyleClass().addAll("lbl", "lbl-primary");
            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            Button replaceBtn = new Button("Use this ↵");
            replaceBtn.getStyleClass().addAll("btn", "btn-primary", "btn-xs");
            replaceBtn.setOnAction(e -> {
                replaceSelectionWith(alt);
                logRewriteHistory(originalSelection, mode, alt);
            });
            header.getChildren().addAll(numLbl, sp, replaceBtn);

            Label textLbl = new Label(alt);
            textLbl.setWrapText(true);

            card.getChildren().addAll(header, textLbl);
            rewriteAlternativesContainer.getChildren().add(card);
        }
    }

    private void logRewriteHistory(String originalText, RewriteMode mode, String chosenAlternative) {
        try {
            RewriteHistoryEntry entry = new RewriteHistoryEntry(
                    currentDocument.getId(), originalText, mode.name(), chosenAlternative);
            documentDao.logRewrite(entry);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to log rewrite history", e);
        }
    }

    private void replaceSelectionWith(String replacement) {
        IndexRange range = documentEditor.getSelection();
        if (range.getLength() > 0) {
            documentEditor.replaceText(range, replacement);
            rewriteStatusLabel.setText("Selection replaced successfully.");
        }
    }

    private RewriteMode getSelectedRewriteMode() {
        if (radioTighten.isSelected()) return RewriteMode.TIGHTEN;
        if (radioFormal.isSelected()) return RewriteMode.FORMAL;
        if (radioCasual.isSelected()) return RewriteMode.CASUAL;
        if (radioGrammar.isSelected()) return RewriteMode.GRAMMAR_FIX;
        return RewriteMode.CLARITY;
    }

    // =========================================================================
    // Phase 2 Actions: Paraphrase Quote with Citation
    // =========================================================================

    @FXML
    private void handleOpenParaphraseDialog() {
        ParaphraseDialog dialog = new ParaphraseDialog(documentEditor.getScene().getWindow(), globalStyleCombo.getValue());
        Optional<CitedParaphrase> result = dialog.showAndWait();

        result.ifPresent(cited -> {
            String fullPassage = cited.getFullFormattedPassage();
            int caretPos = documentEditor.getCaretPosition();
            documentEditor.insertText(caretPos, fullPassage + " ");

            // Link citation to the current document
            if (cited.getSourceQuote() != null && cited.getSourceQuote().getId() != null) {
                try {
                    documentDao.linkCitation(currentDocument.getId(), cited.getSourceQuote().getId());
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to link citation to document", e);
                }
            }
            refreshBibliographyList();
        });
    }

    // =========================================================================
    // Phase 3 Actions: Local Similarity Safeguard
    // =========================================================================

    @FXML
    private void handleRunSimilarityCheck() {
        String source = simSourceArea.getText().trim();
        String paraphrase = simParaphraseArea.getText().trim();

        if (source.isEmpty() || paraphrase.isEmpty()) {
            simGuidanceLabel.setText("Please enter both the original source passage and candidate paraphrase.");
            return;
        }

        SimilarityReport report = similarityChecker.checkSimilarity(source, paraphrase);
        renderSimilarityReport(paraphrase, report);
    }

    private void renderSimilarityReport(String rawParaphrase, SimilarityReport report) {
        simReportCard.setVisible(true);
        simReportCard.setManaged(true);

        simScoreLabel.setText(report.getScorePercentage());
        simNgramLabel.setText(String.format("N-Gram Overlap: %.1f%%", report.getNGramOverlap()));
        simLcsLabel.setText(String.format("Word LCS Ratio: %.1f%%", report.getLcsRatio()));
        simEditLabel.setText(String.format("Edit Distance: %.1f%%", report.getEditSimilarity()));
        simGuidanceLabel.setText(report.getRisk().getAdvice());

        // Bootstrap color badge
        simRiskBadge.setText(report.getRisk().getLabel());
        simRiskBadge.getStyleClass().removeAll("lbl-success", "lbl-warning", "lbl-danger", "badge-risk-low", "badge-risk-mod", "badge-risk-high");
        if (report.getRisk() == SimilarityRisk.LOW) {
            simRiskBadge.getStyleClass().addAll("lbl", "lbl-success");
        } else if (report.getRisk() == SimilarityRisk.MODERATE) {
            simRiskBadge.getStyleClass().addAll("lbl", "lbl-warning");
        } else {
            simRiskBadge.getStyleClass().addAll("lbl", "lbl-danger");
        }

        renderHighlightedSpans(rawParaphrase, report.getMatchedSpans());
    }

    private void renderHighlightedSpans(String rawText, List<MatchedSpan> spans) {
        simHighlightedPreview.getChildren().clear();
        if (spans.isEmpty()) {
            Text regular = new Text(rawText);
            simHighlightedPreview.getChildren().add(regular);
            return;
        }

        List<MatchedSpan> sorted = new ArrayList<>(spans);
        sorted.sort((s1, s2) -> Integer.compare(s1.getStartIndex(), s2.getStartIndex()));

        int lastIdx = 0;
        for (MatchedSpan span : sorted) {
            int start = Math.min(rawText.length(), span.getStartIndex());
            int end = Math.min(rawText.length(), span.getEndIndex());

            if (start > lastIdx) {
                Text unhighlighted = new Text(rawText.substring(lastIdx, start));
                simHighlightedPreview.getChildren().add(unhighlighted);
            }

            if (end > start) {
                Text highlighted = new Text(rawText.substring(start, end));
                highlighted.setUnderline(true);
                simHighlightedPreview.getChildren().add(highlighted);
            }
            lastIdx = Math.max(lastIdx, end);
        }

        if (lastIdx < rawText.length()) {
            Text trailing = new Text(rawText.substring(lastIdx));
            simHighlightedPreview.getChildren().add(trailing);
        }
    }

    @FXML
    private void handleUseSelectionForParaphrase() {
        String selection = documentEditor.getSelectedText();
        if (selection != null && !selection.isBlank()) {
            simParaphraseArea.setText(selection.trim());
        }
    }

    // =========================================================================
    // Bibliography Management
    // =========================================================================

    private void refreshBibliographyList() {
        try {
            List<SourceQuote> docSources = documentDao.getCitationsForDocument(currentDocument.getId());
            if (docSources.isEmpty()) {
                docSources = citationLibrary.findAllSources();
            }
            bibSourcesListView.setItems(FXCollections.observableArrayList(docSources));
            updateBibliographyPreview(docSources);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error loading bibliography sources", e);
        }
    }

    private void updateBibliographyPreview(List<SourceQuote> sources) {
        CitationStyle style = globalStyleCombo.getValue();
        List<String> entries = CitationLibrary.generateBibliography(sources, style);
        String bibText = String.join("\n\n", entries);
        bibFormattedPreview.setText(bibText.isBlank() ? "(No citations in this document)" : bibText);
    }

    private void onCitationStyleChanged() {
        refreshBibliographyList();
    }

    @FXML
    private void handleAppendBibliographyToDoc() {
        try {
            CitationStyle style = globalStyleCombo.getValue();
            String heading = switch (style) {
                case APA -> "\n\nReferences\n\n";
                case MLA -> "\n\nWorks Cited\n\n";
                case CHICAGO -> "\n\nBibliography\n\n";
            };
            List<SourceQuote> docSources = documentDao.getCitationsForDocument(currentDocument.getId());
            if (docSources.isEmpty()) {
                docSources = citationLibrary.findAllSources();
            }
            List<String> entries = CitationLibrary.generateBibliography(docSources, style);
            if (!entries.isEmpty()) {
                documentEditor.appendText(heading + String.join("\n\n", entries));
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error appending bibliography", e);
        }
    }

    @FXML
    private void handleDeleteSelectedSource() {
        SourceQuote selected = bibSourcesListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            try {
                documentDao.unlinkCitation(currentDocument.getId(), selected.getId());
                citationLibrary.deleteById(selected.getId());
                refreshBibliographyList();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error deleting source", e);
            }
        }
    }

    // =========================================================================
    // File I/O: Open / Save .docx
    // =========================================================================

    @FXML
    private void handleNewDocument() {
        this.currentDocument = new DocumentRecord("Untitled Document " + UUID.randomUUID().toString().substring(0, 5), null);
        saveCurrentDocumentRecord();
        documentEditor.clear();
        updateDocLabel();
        refreshBibliographyList();
        refreshRecentDocumentsMenu();
    }

    @FXML
    private void handleOpenDocx() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Word Document (.docx)");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Word Document (*.docx)", "*.docx"));
        File file = fileChooser.showOpenDialog(documentEditor.getScene().getWindow());

        if (file != null) {
            try {
                String content = DocxService.importDocx(file.toPath());
                documentEditor.setText(content);

                this.currentDocument = new DocumentRecord(file.getName(), file.getAbsolutePath());
                saveCurrentDocumentRecord();
                updateDocLabel();
                refreshBibliographyList();
                refreshRecentDocumentsMenu();
            } catch (Exception e) {
                showError("Failed to open document", e.getMessage());
            }
        }
    }

    @FXML
    private void handleExportDocx() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Document as Word (.docx)");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Word Document (*.docx)", "*.docx"));
        fileChooser.setInitialFileName(currentDocument.getTitle().endsWith(".docx") ? currentDocument.getTitle() : currentDocument.getTitle() + ".docx");
        File file = fileChooser.showSaveDialog(documentEditor.getScene().getWindow());

        if (file != null) {
            try {
                CitationStyle style = globalStyleCombo.getValue();
                List<SourceQuote> docSources = documentDao.getCitationsForDocument(currentDocument.getId());
                if (docSources.isEmpty()) {
                    docSources = citationLibrary.findAllSources();
                }
                List<String> bibliography = CitationLibrary.generateBibliography(docSources, style);
                DocxService.exportDocx(file.toPath(), documentEditor.getText(), bibliography, style);

                this.currentDocument.setFilePath(file.getAbsolutePath());
                this.currentDocument.setTitle(file.getName());
                saveCurrentDocumentRecord();
                updateDocLabel();
                refreshRecentDocumentsMenu();

                showInfo("Export Complete", "Document exported successfully to:\n" + file.getAbsolutePath());
            } catch (Exception e) {
                showError("Export Failed", e.getMessage());
            }
        }
    }

    @FXML
    private void handleOpenSettings() {
        SettingsDialog dialog = new SettingsDialog(documentEditor.getScene().getWindow());
        Optional<Boolean> result = dialog.showAndWait();
        if (result.isPresent() && result.get()) {
            updateAiStatusIndicator();
            applySettingsDefaults();
        }
    }

    private void applySettingsDefaults() {
        AppConfig config = configManager.getConfig();
        // Update similarity checker config
        similarityChecker.getConfig().setModerateThreshold(config.getSimilarityModerateThreshold());
        similarityChecker.getConfig().setHighThreshold(config.getSimilarityHighThreshold());
        similarityChecker.getConfig().setWeightNGram(config.getSimilarityWeightNGram());
        similarityChecker.getConfig().setWeightLcs(config.getSimilarityWeightLcs());
        similarityChecker.getConfig().setWeightLevenshtein(config.getSimilarityWeightLevenshtein());

        try {
            globalStyleCombo.setValue(CitationStyle.valueOf(config.getDefaultCitationStyle().toUpperCase()));
        } catch (Exception ignored) {
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
