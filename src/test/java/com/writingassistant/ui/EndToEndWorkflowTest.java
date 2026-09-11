package com.writingassistant.ui;

import com.writingassistant.ai.AiResponse;
import com.writingassistant.ai.GeminiClient;
import com.writingassistant.ai.GeminiException;
import com.writingassistant.config.ConfigManager;
import com.writingassistant.core.citation.*;
import com.writingassistant.core.rewrite.DefaultRewriteEngine;
import com.writingassistant.core.rewrite.RewriteCache;
import com.writingassistant.core.rewrite.RewriteMode;
import com.writingassistant.core.rewrite.RewriteResult;
import com.writingassistant.core.similarity.DefaultSimilarityChecker;
import com.writingassistant.core.similarity.SimilarityReport;
import com.writingassistant.core.similarity.SimilarityRisk;
import com.writingassistant.data.CitationLibrary;
import com.writingassistant.data.DatabaseManager;
import com.writingassistant.data.DocxService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EndToEndWorkflowTest {

    static class E2EMockGeminiClient extends GeminiClient {
        E2EMockGeminiClient(ConfigManager configManager) {
            super(configManager);
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public AiResponse generateText(String prompt) throws GeminiException {
            return generateText(null, prompt);
        }

        @Override
        public AiResponse generateText(String systemInstruction, String prompt) throws GeminiException {
            if (prompt.contains("TIGHTEN")) {
                return AiResponse.success(
                        "1. Because the setup was inadequate, the results were inconclusive.\n" +
                        "2. Inadequate experimental setup led to inconclusive results.", "test-model");
            }
            if (prompt.toLowerCase().contains("do not simply substitute synonyms")) {
                return AiResponse.success(
                        "Because people struggle to differentiate familiarity from factual accuracy, hearing an assertion repeatedly makes them more likely to accept it as true.",
                        "test-model");
            }
            return AiResponse.success("Default mock response.", "test-model");
        }
    }

    @Test
    @DisplayName("End-to-End: Draft -> Rewrite -> Cited Paraphrase -> Similarity Check -> .docx Export")
    void testCompleteUserWorkflowEndToEnd(@TempDir Path tempDir) throws Exception {
        // 1. Initialize SQLite Database in temp directory
        Path dbPath = tempDir.resolve("e2e_writing_assistant.db");
        DatabaseManager dbManager = new DatabaseManager("jdbc:sqlite:" + dbPath.toAbsolutePath());
        CitationLibrary citationLibrary = new CitationLibrary(dbManager);

        ConfigManager configManager = new ConfigManager(tempDir);
        E2EMockGeminiClient mockAiClient = new E2EMockGeminiClient(configManager);

        DefaultRewriteEngine rewriteEngine = new DefaultRewriteEngine(mockAiClient, new RewriteCache());
        QuoteParaphraser paraphraser = new QuoteParaphraser(mockAiClient);
        DefaultCitationEngine citationEngine = new DefaultCitationEngine(paraphraser);
        DefaultSimilarityChecker similarityChecker = new DefaultSimilarityChecker();

        // -------------------------------------------------------------
        // Step 1: User drafts initial paper
        // -------------------------------------------------------------
        StringBuilder documentBody = new StringBuilder();
        documentBody.append("Introduction\n\n");
        String clunkySentence = "Due to the fact that the setup was inadequate, the results were inconclusive.";
        documentBody.append(clunkySentence).append("\n\n");

        // -------------------------------------------------------------
        // Step 2: User executes Phase 1 Rewrite (Tighten)
        // -------------------------------------------------------------
        RewriteResult rewriteResult = rewriteEngine.rewrite(clunkySentence, RewriteMode.TIGHTEN);
        assertFalse(rewriteResult.getAlternatives().isEmpty(), "Rewrite must return alternatives");
        String chosenAlternative = rewriteResult.getAlternatives().get(0);
        assertTrue(chosenAlternative.contains("Because the setup was inadequate"));

        // Replace in document
        String updatedDoc = documentBody.toString().replace(clunkySentence, chosenAlternative);
        documentBody = new StringBuilder(updatedDoc);

        // -------------------------------------------------------------
        // Step 3: User paraphrases a quoted source (Phase 2)
        // -------------------------------------------------------------
        SourceQuote kahnemanQuote = new SourceQuote(
                "A reliable way to make people believe in falsehoods is frequent repetition, because familiarity is not easily distinguished from truth.",
                "Daniel Kahneman", "2011", "Thinking, Fast and Slow", "Farrar, Straus and Giroux", "62", null, SourceType.BOOK
        );

        CitedParaphrase citedParaphrase = citationEngine.paraphraseWithCitation(kahnemanQuote, CitationStyle.APA);
        assertNotNull(citedParaphrase);
        assertEquals("(Kahneman, 2011, p. 62)", citedParaphrase.getInTextCitation());
        assertEquals("Kahneman, D. (2011). *Thinking, Fast and Slow*. Farrar, Straus and Giroux.", citedParaphrase.getBibliographyEntry());

        // Insert cited passage into document & save to library
        documentBody.append(citedParaphrase.getFullFormattedPassage()).append("\n\n");
        citationLibrary.save(kahnemanQuote);

        // -------------------------------------------------------------
        // Step 4: User runs Phase 3 Local Similarity Checker
        // -------------------------------------------------------------
        SimilarityReport similarityReport = similarityChecker.checkSimilarity(
                kahnemanQuote.getQuote(), citedParaphrase.getParaphrasedText()
        );

        assertEquals(SimilarityRisk.LOW, similarityReport.getRisk(),
                "Quality paraphrase must score LOW risk: " + similarityReport.getSummary());
        assertTrue(similarityReport.getSimilarityScore() < 25.0,
                "Similarity score must be < 25.0%, got: " + similarityReport.getSimilarityScore());
        assertTrue(similarityReport.isOffline(), "Similarity check must be 100% offline");

        // -------------------------------------------------------------
        // Step 5: User exports to Word .docx with generated bibliography
        // -------------------------------------------------------------
        Path exportPath = tempDir.resolve("final_academic_paper.docx");
        List<String> bibliography = citationLibrary.generateBibliography(CitationStyle.APA);
        assertEquals(1, bibliography.size());

        DocxService.exportDocx(exportPath, documentBody.toString(), bibliography, CitationStyle.APA);
        assertTrue(exportPath.toFile().exists());
        assertTrue(exportPath.toFile().length() > 0);

        // -------------------------------------------------------------
        // Step 6: Verify imported .docx contents
        // -------------------------------------------------------------
        String importedDocx = DocxService.importDocx(exportPath);
        assertTrue(importedDocx.contains("Introduction"));
        assertTrue(importedDocx.contains("Because the setup was inadequate"));
        assertTrue(importedDocx.contains("hearing an assertion repeatedly"));
        assertTrue(importedDocx.contains("(Kahneman, 2011, p. 62)"));
        assertTrue(importedDocx.contains("References"));
        assertTrue(importedDocx.contains("Thinking, Fast and Slow"));

        System.out.println("=== End-to-End Workflow Verification Succeeded ===");
        System.out.println("Exported File Size: " + exportPath.toFile().length() + " bytes");
        System.out.println("Imported Document Preview:\n" + importedDocx);
    }
}
