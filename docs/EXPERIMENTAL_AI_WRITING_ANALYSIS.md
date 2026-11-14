# Optional Experimental AI-Writing Analysis Architecture

## 1. Executive Summary and Ethical Stance

Originality Desk provides an **optional, experimental statistical stylometry module** designed strictly for exploratory linguistic observation.

Consistent with empirical research (Liang et al., Stanford University, 2023; Sadasivan et al., University of Maryland, 2023; Weber-Wulff et al., 2023), automated AI detection tools suffer from insurmountable scientific limitations:
1. **Severe False-Positive Bias against ESL/EFL Authors**: Writers whose native language is not English rely on more regular grammatical structures and smaller lexical sets, causing statistical detectors to flag their genuine prose as AI-generated at rates exceeding 61%.
2. **Vulnerability to Paraphrasing and Perturbations**: Light paraphrase passes, intentional character swaps, or minor prompting variations defeat detector classifiers with trivial effort.
3. **Absence of Ground Truth**: Statistical heuristics (perplexity, entropy, burstiness) measure stylistic regularity—they do not provide forensic proof of machine authorship.

Consequently, Originality Desk:
- **Disables AI analysis by default** in all configurations.
- **Strictly prohibits binary accusations or opaque "AI percentage scores"**.
- **Requires explicit institutional acknowledgment of disparate impact** before any statistical profiles can be computed.
- **Mandates clear architectural separation** between deterministic evidence-based similarity matching (exact, near-duplicate, semantic chunk embeddings) and non-deterministic statistical stylometry indicators.
- **Prohibits the use of stylometry outputs in academic disciplinary or misconduct hearings**.

---

## 2. Component Design

The implementation is located under `com.writingassistant.ai.analysis`:

```
com.writingassistant.ai.analysis
├── AiWritingConfig.java              # Immutable opt-in configuration with ESL impact acknowledgment
├── AiWritingAnalysisResult.java      # Structured descriptive statistical observation and ethical warnings
└── ExperimentalAiWritingAnalyzer.java# Genuine statistical stylometry analyzer (entropy, burstiness, TTR)
```

### 2.1 Configuration Controls (`AiWritingConfig`)

To activate the module, an institutional administrator or researcher must supply an explicit opt-in configuration:

```java
// Disabled by default
AiWritingConfig config = AiWritingConfig.disabled();

// Explicit activation with mandatory disparate impact acknowledgment
AiWritingConfig experimental = AiWritingConfig.experimental(/* acknowledgeDisparateImpact */ true);
```

If `enabled == true` but `acknowledgeDisparateImpact == false`, the analyzer immediately halts and returns status `UNACKNOWLEDGED_DISPARATE_IMPACT`, refusing to compute metrics.

### 2.2 Descriptive Stylometry Profiling (`ExperimentalAiWritingAnalyzer`)

Instead of computing a fabricated "87% AI probability", `ExperimentalAiWritingAnalyzer` derives transparent, reproducible linguistic measures from `PerplexityMetricsCalculator`:
- **Lexical Entropy ($H$)**: Shannon entropy over token unigram probability distributions.
  - $\ge 5.0$ bits: `HIGH_LEXICAL_DIVERSITY`
  - $3.8 - 5.0$ bits: `MODERATE_LEXICAL_DIVERSITY`
  - $< 3.8$ bits: `CONSTRAINED_LEXICAL_DIVERSITY`
- **Sentence Burstiness ($B$)**: Normalized variance of sentence lengths ($\sigma_L / \mu_L$).
  - $\ge 0.50$: `HIGH_RHYTHMIC_VARIANCE`
  - $0.25 - 0.50$: `MODERATE_RHYTHMIC_VARIANCE`
  - $< 0.25$: `UNIFORM_SENTENCE_CADENCE`
- **Minimum Text Length Constraint**: Texts shorter than 150 words are rejected with status `INSUFFICIENT_TEXT_LENGTH`, because statistical variance on small samples is mathematically invalid.

---

## 3. Disciplinary Prohibition and Warnings

Every `AiWritingAnalysisResult` carries an immutable ethical warning:

> **DISCIPLINARY PROHIBITION**: Statistical stylometry metrics (lexical entropy, sentence burstiness) are non-deterministic and must NEVER be used to penalize students, deduct marks, or accuse authors of AI misconduct. Empirical research (Stanford Liang et al., 2023) proves these metrics suffer from severe false-positive bias against non-native English (ESL/EFL) and neurodivergent writers.

---

## 4. Verification and Test Suite

Verified via `ExperimentalAiWritingAnalysisTest`:
1. `defaultAnalyzerIsDisabledByPolicy`: Confirms default analyzer is disabled by policy.
2. `analyzerRequiresExplicitAcknowledgmentOfDisparateImpact`: Rejects execution without explicit ESL acknowledgment.
3. `analyzerRejectsShortPassagesAsStatisticallyInvalid`: Enforces 150-word minimum threshold.
4. `experimentalAnalyzerProducesDescriptiveProfilesWithoutFakeScores`: Verifies genuine Shannon entropy, sentence burstiness coefficient, type-token ratio, descriptive categories, and prominent ethical safeguards.
