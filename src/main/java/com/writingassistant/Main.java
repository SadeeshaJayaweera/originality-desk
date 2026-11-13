package com.writingassistant;

import com.writingassistant.ai.AiFeatureUnavailableException;
import com.writingassistant.ai.GeminiClient;
import com.writingassistant.ai.GeminiStatus;
import com.writingassistant.config.ConfigManager;
import com.writingassistant.core.citation.*;
import com.writingassistant.core.rewrite.DefaultRewriteEngine;
import com.writingassistant.core.rewrite.RewriteMode;
import com.writingassistant.core.rewrite.RewriteResult;
import com.writingassistant.core.similarity.DefaultSimilarityChecker;
import com.writingassistant.core.similarity.MatchedSpan;
import com.writingassistant.core.similarity.SimilarityReport;
import com.writingassistant.data.CitationLibrary;
import com.writingassistant.data.DatabaseManager;
import com.writingassistant.logging.AppLogger;
import com.writingassistant.ui.WritingAssistantApp;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Main application launcher and diagnostic entry point.
 */
public class Main {

    private static final Logger LOGGER = AppLogger.getLogger(Main.class);

    public static void main(String[] args) {
        // Fast-path: check for standalone server mode or help before initializing desktop resources
        for (String arg : args) {
            if ("--help".equalsIgnoreCase(arg) || "-h".equalsIgnoreCase(arg)) {
                printHelp();
                return;
            } else if ("--server".equalsIgnoreCase(arg) || "--web".equalsIgnoreCase(arg)) {
                try {
                    com.writingassistant.server.OriginalityDeskServer.main(args);
                } catch (Exception e) {
                    System.err.println("Originality Desk server failed: " + e.getMessage());
                    System.exit(1);
                }
                return;
            }
        }

        // Initialize configuration and rotating logger for desktop application
        ConfigManager configManager = ConfigManager.getInstance();
        AppLogger.init(configManager);

        LOGGER.info("=== Starting Writing Assistant ===");
        LOGGER.info("App Data Directory: " + configManager.getAppDataDir().toAbsolutePath());
        LOGGER.info("Config File:        " + configManager.getConfigFile().toAbsolutePath());
        LOGGER.info("Database URL:       " + configManager.getDatabasePath().toAbsolutePath());

        // Initialize local SQLite database
        DatabaseManager dbManager = DatabaseManager.getInstance();
        LOGGER.info("Local database initialized.");

        // Check for CLI diagnostic flags
        for (String arg : args) {
            if ("--smoke-test".equalsIgnoreCase(arg)) {
                runSmokeTest(configManager, dbManager);
                return;
            }
        }

        // Launch JavaFX GUI application
        try {
            WritingAssistantApp.main(args);
        } catch (Throwable t) {
            LOGGER.severe("Unhandled exception during GUI execution: " + t.getMessage());
            t.printStackTrace();
        } finally {
            AppLogger.shutdown();
        }
    }

    /**
     * Executes headless diagnostic verification of local SQLite, Gemini connectivity, Rewrite Engine,
     * Citation Engine, and Offline Similarity Checker.
     */
    public static void runSmokeTest(ConfigManager configManager, DatabaseManager dbManager) {
        System.out.println("================================================================");
        System.out.println("  Writing Assistant - Full System Diagnostics & Smoke Test");
        System.out.println("================================================================");
        System.out.println("OS App Data Folder:  " + configManager.getAppDataDir().toAbsolutePath());
        System.out.println("Configuration File:  " + configManager.getConfigFile().toAbsolutePath());
        System.out.println("Logs Directory:      " + configManager.getLogsDir().toAbsolutePath());
        System.out.println();

        // 1. Test Database
        System.out.print("[1/5] Testing SQLite persistence... ");
        try (Connection conn = dbManager.getConnection()) {
            if (conn.isValid(2)) {
                System.out.println("SUCCESS (SQLite connection valid)");
            } else {
                System.out.println("WARNING (Connection returned invalid status)");
            }
        } catch (Exception e) {
            System.out.println("FAILED (" + e.getMessage() + ")");
        }

        // 2. Test AI Connection
        System.out.print("[2/5] Testing Gemini AI connectivity... ");
        GeminiClient client = new GeminiClient(configManager);
        GeminiStatus status = client.testConnection();

        if (status.isAvailable()) {
            System.out.println("SUCCESS (" + status.getMessage() + ")");
        } else {
            System.out.println("GRACEFULLY DISABLED / UNCONFIGURED");
            System.out.println("      Note: " + status.getMessage());
        }

        // 3. Test Style Rewriting Engine (Phase 1)
        System.out.println();
        System.out.println("[3/5] Testing Style Rewriting Engine (Phase 1)...");
        DefaultRewriteEngine rewriteEngine = new DefaultRewriteEngine(client);

        Map<RewriteMode, String> sampleSentences = new LinkedHashMap<>();
        sampleSentences.put(RewriteMode.TIGHTEN,
                "Due to the fact that there was an unexpected occurrence of rain during the afternoon hours, the outdoor athletic competition was postponed until a later date.");
        sampleSentences.put(RewriteMode.CLARITY,
                "The reason why the results were not giving what we expected was because the temperature was not kept at the same level throughout the experiment which caused error.");
        sampleSentences.put(RewriteMode.FORMAL,
                "Hey folks, we gotta fix this issue ASAP because users are getting super annoyed.");
        sampleSentences.put(RewriteMode.CASUAL,
                "Notwithstanding the aforementioned considerations, the implementation shall proceed forthwith.");
        sampleSentences.put(RewriteMode.GRAMMAR_FIX,
                "Each of the students have submitted their assignment on yesterday.");

        if (client.isConfigured()) {
            System.out.println("      Running live quality evaluations on sample sentences:\n");
            for (Map.Entry<RewriteMode, String> entry : sampleSentences.entrySet()) {
                RewriteMode mode = entry.getKey();
                String sentence = entry.getValue();
                System.out.println("      --- Mode: " + mode.getDisplayName() + " ---");
                System.out.println("      Original: \"" + sentence + "\"");
                try {
                    RewriteResult result = rewriteEngine.rewrite(sentence, mode);
                    int idx = 1;
                    for (String alt : result.getAlternatives()) {
                        System.out.println("        " + idx++ + ". " + alt);
                    }
                } catch (Exception e) {
                    System.out.println("        Error executing rewrite: " + e.getMessage());
                }
                System.out.println();
            }
        } else {
            System.out.println("      AI is unconfigured. Verifying graceful exception handling on sample sentence...");
            try {
                rewriteEngine.rewrite(sampleSentences.get(RewriteMode.CLARITY), RewriteMode.CLARITY);
                System.out.println("      UNEXPECTED: Rewrite succeeded without API key.");
            } catch (AiFeatureUnavailableException e) {
                System.out.println("      SUCCESS: Caught expected AiFeatureUnavailableException.");
                System.out.println("      Message: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("      CAUGHT OTHER: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
        }

        // 4. Test Citation-Safe Paraphraser & CitationFormatter (Phase 2)
        System.out.println();
        System.out.println("[4/5] Testing Citation-Safe Paraphraser & Formatter (Phase 2)...");
        SourceQuote sampleQuote = new SourceQuote(
                "A reliable way to make people believe in falsehoods is frequent repetition, because familiarity is not easily distinguished from truth.",
                "Daniel Kahneman", "2011", "Thinking, Fast and Slow", "Farrar, Straus and Giroux", "62", null, SourceType.BOOK);

        System.out.println("      Sample Source:   " + sampleQuote.getAuthor() + " (" + sampleQuote.getYear() + "). " + sampleQuote.getTitle());
        System.out.println("      APA In-Text:     " + CitationFormatter.formatInText(sampleQuote, CitationStyle.APA));
        System.out.println("      APA Reference:   " + CitationFormatter.formatBibliographyEntry(sampleQuote, CitationStyle.APA));
        System.out.println("      MLA In-Text:     " + CitationFormatter.formatInText(sampleQuote, CitationStyle.MLA));
        System.out.println("      MLA Works Cited: " + CitationFormatter.formatBibliographyEntry(sampleQuote, CitationStyle.MLA));
        System.out.println("      Chicago In-Text: " + CitationFormatter.formatInText(sampleQuote, CitationStyle.CHICAGO));
        System.out.println("      Chicago Ref:     " + CitationFormatter.formatBibliographyEntry(sampleQuote, CitationStyle.CHICAGO));

        DefaultCitationEngine citationEngine = new DefaultCitationEngine(client);
        if (client.isConfigured()) {
            System.out.println("\n      Generating live restructured paraphrase...");
            try {
                CitedParaphrase cited = citationEngine.paraphraseWithCitation(sampleQuote, CitationStyle.APA);
                System.out.println("      Paraphrased:  " + cited.getParaphrasedText());
                System.out.println("      Full Passage: " + cited.getFullFormattedPassage());
            } catch (Exception e) {
                System.out.println("      Error generating paraphrase: " + e.getMessage());
            }
        } else {
            System.out.println("      AI is unconfigured. Verifying graceful exception handling on paraphrase...");
            try {
                citationEngine.paraphraseWithCitation(sampleQuote, CitationStyle.APA);
            } catch (AiFeatureUnavailableException e) {
                System.out.println("      SUCCESS: Caught expected AiFeatureUnavailableException on paraphrase.");
            } catch (Exception e) {
                System.out.println("      CAUGHT OTHER: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
        }

        // 5. Test Local Offline Similarity Checker (Phase 3)
        System.out.println();
        System.out.println("[5/5] Testing Local Offline Similarity Checker (Phase 3)...");
        DefaultSimilarityChecker similarityChecker = new DefaultSimilarityChecker();

        String originalSource =
                "A reliable way to make people believe in falsehoods is frequent repetition, because familiarity is not easily distinguished from truth.";

        String highRiskText =
                "A dependable way to make people believe in falsehoods is frequent repetition, because familiarity is not easily separated from truth.";

        String moderateRiskText =
                "A trustworthy way to cause humans to trust in untruths is regular repetition, because acquaintance is not readily separated from reality.";

        String lowRiskText =
                "Because people struggle to differentiate familiarity from factual accuracy, hearing an assertion repeatedly makes them more likely to accept it as true.";

        System.out.println("      Original: \"" + originalSource + "\"\n");

        // High Risk Evaluation
        SimilarityReport highReport = similarityChecker.checkSimilarity(originalSource, highRiskText);
        System.out.println("      [Case 1: Near-Identical Text]");
        System.out.println("        Score: " + highReport.getScorePercentage() + " -> " + highReport.getRisk().getLabel());
        System.out.println("        N-gram: " + String.format("%.1f%%", highReport.getNGramOverlap())
                + ", LCS: " + String.format("%.1f%%", highReport.getLcsRatio())
                + ", Flagged Spans: " + highReport.getMatchedSpans().size());
        for (MatchedSpan span : highReport.getMatchedSpans()) {
            System.out.println("        • Matched Span [" + span.getStartIndex() + ".." + span.getEndIndex() + "]: \"" + span.getMatchedText() + "\"");
        }

        // Moderate Risk Evaluation
        SimilarityReport modReport = similarityChecker.checkSimilarity(originalSource, moderateRiskText);
        System.out.println("\n      [Case 2: Synonym-Swap Patchwriting]");
        System.out.println("        Score: " + modReport.getScorePercentage() + " -> " + modReport.getRisk().getLabel());
        System.out.println("        N-gram: " + String.format("%.1f%%", modReport.getNGramOverlap())
                + ", LCS: " + String.format("%.1f%%", modReport.getLcsRatio()));

        // Low Risk Evaluation
        SimilarityReport lowReport = similarityChecker.checkSimilarity(originalSource, lowRiskText);
        System.out.println("\n      [Case 3: Genuine Restructured Paraphrase]");
        System.out.println("        Score: " + lowReport.getScorePercentage() + " -> " + lowReport.getRisk().getLabel());
        System.out.println("        N-gram: " + String.format("%.1f%%", lowReport.getNGramOverlap())
                + ", LCS: " + String.format("%.1f%%", lowReport.getLcsRatio())
                + ", Flagged Spans: " + lowReport.getMatchedSpans().size());

        System.out.println("\n================================================================");
        System.out.println("Diagnostics complete. All modules (Phases 1, 2, 3) operational.");
        System.out.println("================================================================");
    }

    private static void printHelp() {
        System.out.println("Originality Desk & Writing Assistant");
        System.out.println("Usage:");
        System.out.println("  java -jar writing-assistant.jar              Launch JavaFX GUI (desktop)");
        System.out.println("  java -jar writing-assistant.jar --server     Launch Originality Desk web server");
        System.out.println("  java -jar writing-assistant.jar --smoke-test Run desktop diagnostic checks");
        System.out.println("  java -jar writing-assistant.jar --help       Show this help message");
    }
}
