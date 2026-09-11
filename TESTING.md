# Testing Guide for Writing Assistant

This guide provides testing procedures for both automated test suites and manual validation of Writing Assistant features.

---

## 1. Automated Test Suite Execution

Run the complete JUnit 5 test suite across all modules:

```bash
mvn clean test
```

### Expected Output:
- **57 tests executed**, **0 failures**, **0 errors**.
- Coverage includes:
  - `ConfigManagerTest`: Configuration loading, OS directory resolution, fallback handling.
  - `GeminiConnectivityTest`: API connectivity check & graceful fallback on unconfigured keys.
  - `DefaultRewriteEngineTest`, `RewritePromptTemplatesTest`, `RewriteResponseParserTest`: 5 rewrite modes, multi-pass parsing, session caching.
  - `QuoteParaphraserTest`, `CitationFormatterTest`: Prompt construction, APA 7th, MLA 9th, and Chicago 17th standards.
  - `DefaultSimilarityCheckerTest`, `SimilaritySmokeTest`: Word N-gram overlap, Word LCS, Levenshtein distance, span offset extraction, and calibrated risk levels.
  - `SqliteDocumentDaoTest`, `CitationLibraryTest`, `SessionPersistenceSmokeTest`: SQLite CRUD, many-to-many document-citation linking, rewrite history logging, and restart continuity.
  - `DocxServiceTest`: Apache POI `.docx` import/export, italic run preservation, 0.5-inch hanging indent formatting.
  - `EndToEndWorkflowTest`: Complete integrated workflow test from drafting to export.

---

## 2. Command-Line Smoke Test & Diagnostics

Verify system health, SQLite connection, AI status, and algorithm outputs:

```bash
java -jar target/writing-assistant-1.0.0-SNAPSHOT.jar --smoke-test
```

---

## 3. Manual Testing Scenarios

### Scenario A: Style Rewriting (Phase 1)
1. **Launch App**: Launch GUI via `java -jar target/writing-assistant-1.0.0-SNAPSHOT.jar` or `mvn javafx:run`.
2. **Input Text**: Type or paste into the main document editor:
   > *"Due to the fact that there was an unexpected occurrence of rain during the afternoon hours, the outdoor athletic competition was postponed until a later date."*
3. **Select & Choose Mode**: Highlight the sentence in the editor, click the **"✍️ Rewrite"** tab, select **Tighten**, and click **"⚡ Generate Rewrite Alternatives"**.
4. **Verify Alternatives**:
   - Ensure 2–3 alternatives appear in clean cards.
   - Example expected output: *"Because it rained unexpectedly this afternoon, the outdoor athletic competition was postponed."*
5. **1-Click Replacement**: Click **"Use this ↵"** on an alternative and verify the highlighted sentence in the document editor is replaced cleanly.
6. **Test Other Modes**: Test **Clarity** (untangling convoluted phrasing), **Formal** (academic tone), **Casual** (friendly tone), and **Grammar Fix** (syntax correction).

---

### Scenario B: Citation-Safe Quote Paraphrasing (Phase 2)
1. **Open Paraphrase Dialog**: Click the **"📚 Paraphrase Quote"** tab and click **"📚 Open Paraphrase & Citation Dialog"**.
2. **Enter Quote & Metadata**:
   - **Quote**: *"A reliable way to make people believe in falsehoods is frequent repetition, because familiarity is not easily distinguished from truth."*
   - **Author**: `Daniel Kahneman`
   - **Year**: `2011`
   - **Title**: `Thinking, Fast and Slow`
   - **Publication**: `Farrar, Straus and Giroux`
   - **Page**: `62`
   - **Type**: `BOOK`
   - **Style**: `APA`
3. **Generate Paraphrase**: Click **"⚡ Generate Restructured Paraphrase"**.
   - Verify generated paraphrase genuinely restructures sentence clauses (e.g. starting with *"Because people struggle to differentiate..."*).
   - Verify in-text citation: `(Kahneman, 2011, p. 62)`
   - Verify reference entry: `Kahneman, D. (2011). *Thinking, Fast and Slow*. Farrar, Straus and Giroux.`
4. **Insert into Document**: Click **"Insert into Document"**.
   - Verify the full passage + citation is inserted at the cursor.
   - Verify Daniel Kahneman appears in the **Bibliography** tab.

---

### Scenario C: Local Similarity Safeguard (Phase 3)
1. **Open Similarity Tab**: Switch to the **"🔍 Similarity Safeguard"** tab.
2. **Test High Risk (Near-Identical Text)**:
   - **Source**: *"A reliable way to make people believe in falsehoods is frequent repetition, because familiarity is not easily distinguished from truth."*
   - **Paraphrase**: *"A dependable way to make people believe in falsehoods is frequent repetition, because familiarity is not easily separated from truth."*
   - Click **"🔍 Run Offline Similarity Check"**.
   - **Expected**: Score $\ge 55\%$ (`HIGH RISK` red badge), and the overlapping phrase *"way to make people believe in falsehoods is frequent repetition..."* is highlighted with a red underline.
3. **Test Moderate Risk (Synonym-Swap Patchwriting)**:
   - **Paraphrase**: *"A trustworthy way to cause humans to trust in untruths is regular repetition, because acquaintance is not readily separated from reality."*
   - **Expected**: Score $25\% - 55\%$ (`MODERATE RISK` amber badge).
4. **Test Low Risk (Genuine Restructuring)**:
   - **Paraphrase**: *"Because people struggle to differentiate familiarity from factual accuracy, hearing an assertion repeatedly makes them more likely to accept it as true."*
   - **Expected**: Score $< 25\%$ (`LOW RISK` green badge) with 0 flagged verbatim spans.

---

### Scenario D: Apache POI Word (.docx) Import & Export (Phase 4)
1. **Export Document**: With body text and citations present, click **"💾 Export .docx"** in the top toolbar. Save as `test_paper.docx`.
2. **Verify in Word Processor**: Open `test_paper.docx` in Microsoft Word, LibreOffice, or Pages.
   - Confirm body paragraphs are clean.
   - Confirm heading is `References` (or `Works Cited` / `Bibliography`).
   - Confirm book/journal titles are styled in **italics**.
   - Confirm bibliography entries have a standard **0.5-inch hanging indent**.
3. **Import Document**: Click **"📄 New"** to clear the editor, then click **"📂 Open .docx"** and select `test_paper.docx`.
   - Confirm all text and citations load back into the editor accurately.

---

### Scenario E: Offline Graceful Degradation (No API Key)
1. Open **"⚙️ Settings"** and clear the Gemini API Key, then click **"Save Changes"**.
2. Verify toolbar shows: `○ AI Engine: Disabled (Offline mode only)`.
3. Try **Rewrite**: Displays friendly prompt *"AI Offline: API key not set in config.json"*. No crash.
4. Try **Paraphrase**: Displays notice *"AI Unavailable: Set GEMINI_API_KEY in Settings"*.
5. Test **Local Similarity Checker**: Runs instantly with 100% functionality and zero network access.
6. Test **Citation Formatter & Library**: Generates APA, MLA, Chicago references and persists to SQLite with 100% functionality.

---

### Scenario F: Session Persistence Across Restarts
1. Open a document, add citations, and accept a rewrite.
2. Close the application completely (`Cmd+Q` or window close).
3. Re-launch the application.
4. Check **"🕒 Recent Docs"** menu button in the top toolbar.
5. Select your previous document and confirm all text, citations, and rewrite logs are restored.
