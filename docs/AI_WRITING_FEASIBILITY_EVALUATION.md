# AI-Writing Analysis: Scientific Feasibility Evaluation and Institutional Ethics Framework

**Originality Desk Engineering & Academic Integrity Report**  
**Document ID:** OD-FEASIBILITY-AI-2026-V1  
**Authors:** Senior ML Engineer, Application Security Engineer, Academic Integrity Architect  
**Classification:** Public Engineering Guidance  

---

## 1. Executive Summary & Core Institutional Recommendation

Originality Desk has conducted an empirical and scientific literature evaluation into the feasibility, reliability, statistical validity, and ethical implications of automated AI-generated text detection in higher education.

### Core Recommendation:
> **Originality Desk formally advises educational institutions against utilizing automated AI-detection scores as sole or primary evidence in academic integrity proceedings, student disciplinary actions, or grade deductions.**

Commercial AI detectors claiming "99% accuracy" consistently fail independent empirical verification. The statistical reality is that statistical AI text detection is:
1. **Fundamentally brittle** against paraphrasing, prompting nuances, and translation round-trips.
2. **Severely biased** against non-native English (ESL/EFL) students and neurodivergent writers.
3. **Information-theoretically bounded** as language model quality approaches human distributions.

Any AI-writing capability within Originality Desk is strictly categorized as **EXPERIMENTAL**, accompanied by non-suppressible uncertainty disclosures and prohibited from generating definitive accusations of misconduct.

---

## 2. Comprehensive Scientific Literature Review

### 2.1 Bias Against Non-Native English Writers (Liang et al., Stanford, 2023)
In the landmark Stanford University study *"GPT detectors are biased against non-native English writers"* (Liang, Yuksekgonul, Mao, Wu, & Zou; PNAS / arXiv:2304.02819), researchers evaluated seven leading commercial and open-source AI detectors across:
- **US native 8th-grade essays (Hewlett Foundation dataset)**.
- **Non-native English university essays (TOEFL writing exam)**.

**Key Findings:**
- Commercial detectors correctly classified **97%** of native US 8th-grade essays as human.
- The exact same detectors incorrectly flagged **over 61.3%** of genuine human TOEFL essays as AI-generated!
- In some detectors, **97.8%** of human TOEFL essays were falsely flagged.
- **Root Cause:** Non-native writers naturally employ simpler grammatical constructions, more restricted lexicons, and lower perplexity—the exact statistical signature that detectors naively associate with Large Language Models.

### 2.2 Information-Theoretic Upper Bounds (Sadasivan et al., UMD, 2023)
In *"Can AI-Generated Text be Reliably Detected?"* (Sadasivan, Kumar, Balasubramanian, Wang, & Feizi; University of Maryland, arXiv:2303.11156), the authors proved that as generative language models improve, their output distributions $P_{\text{AI}}$ converge to human distributions $P_{\text{Human}}$, making detector AUROC mathematically upper-bounded:
$$\text{AUROC} \le \frac{1}{2} + \frac{1}{4} \|P_{\text{AI}} - P_{\text{Human}}\|_{TV}$$
Furthermore, the authors showed that **light recursive paraphrasing** using off-the-shelf paraphrase models (e.g. DIPPER) reduces the detection accuracy of even state-of-the-art detectors to random guessing (AUROC $\approx 0.50$), while preserving semantic meaning.

### 2.3 Independent Multicenter University Evaluation (Weber-Wulff et al., 2023)
In *"Testing of Detection Tools for AI-Generated Text"* (Weber-Wulff, Anohina-Naumeca, Bjelobaba, et al.; International Journal for Educational Integrity, 2023), a European consortium of integrity researchers tested 12 commercial tools and 2 free engines against real student submissions, machine-generated text, and human-edited machine text:
- Overall tool accuracy ranged from **38% to 70%**.
- None of the tools proved capable of distinguishing human text from human-edited AI text.
- The authors concluded that current tools are *unsuitable for determining academic misconduct*.

### 2.4 OpenAI's Own Classifier Retirement (July 2023)
In January 2023, OpenAI released their official AI Text Classifier trained specifically to distinguish GPT text. In July 2023, OpenAI **quietly shut down and decommissioned the classifier** due to an unacceptable **26% True Positive Rate** and a **9% False Positive Rate on human text**, openly acknowledging that automated detection is scientifically intractable.

---

## 3. Evaluation of Technical Detection Mechanisms

| Detection Methodology | Theoretical Basis | Failure Modes | Empirical Efficacy |
|---|---|---|---|
| **Perplexity (Cross-Entropy)** | Measures how "surprised" a reference model is by a sequence. LLMs favor low-perplexity tokens. | Formal academic prose naturally has low perplexity. ESL writers produce low perplexity. Prompting with rare words trivially defeats it. | **Unreliable** ($\text{FPR} > 30\%$ on academic prose). |
| **Burstiness (Perplexity Variance)** | Measures sentence-to-sentence fluctuation in complexity. Humans vary rhythm; LLMs historically had flatter distributions. | RLHF models and modern chain-of-thought prompting produce highly bursty sentence distributions. | **Weak signal** (easily gamed by punctuation or stylistic prompts). |
| **Stylometry & N-Gram Frequencies** | Type-Token Ratio (TTR), Hapax Legomena, stop-word distributions, syntactic depth. | Stylometry identifies author identity, not artificiality. Overlaps heavily with personal writing style and discipline-specific conventions. | **Insufficient** for forensic attribution. |
| **Supervised Neural Classifiers (e.g. RoBERTa-large)** | Fine-tuned encoder predicting binary label $[0, 1]$. | Severe out-of-distribution failure. A detector trained on GPT-3 fails on Claude 3.5, GPT-4o, or LLaMA 3. Brittle to adversarial prompt injections. | **High False Positive Rate** on academic writing. |
| **Statistical Watermarking (Kirchenbauer et al.)** | Green/Red token partitioning during generation. | Requires control over the generation engine. Academic platforms have no access to students' generation environments; watermarks are lost upon light paraphrasing. | **Not applicable** to passive academic evaluation. |

---

## 4. Empirical Vulnerability & Adversarial Fragility

AI detectors exhibit complete fragility under standard student drafting practices:

1. **Academic Paraphrasing:** Reorganizing a paragraph or swapping 3-4 nouns with discipline synonyms drops detection probabilities from $>90\%$ to $<10\%$.
2. **Translation Round-Tripping:** Translating text from English to French and back to English using DeepL or Google Translate destroys n-gram perplexity signatures while preserving academic arguments.
3. **Prompt Conditioning:** Prompts such as *"Write with varied sentence structures, incorporating occasional conversational idioms and rhetorical questions"* eliminate the flat-entropy signature entirely.
4. **Iterative AI-Assisted Editing:** When a student drafts an outline, uses an LLM to polish grammar, and then manually edits the output (a common, legitimate workflow), the resulting hybrid text cannot be mathematically attributed to either entity.

---

## 5. Disparate Impact and Human Rights Considerations

Automating academic disciplinary referrals through algorithmic AI detectors introduces profound equity and civil rights violations:

1. **Discrimination Against ESL/EFL Students:**
   International students studying in English-medium universities are statistically 5 to 10 times more likely to be falsely accused of cheating due to restricted vocabulary profiles.
2. **Discrimination Against Neurodivergent Learners:**
   Students with Autism Spectrum Disorder (ASD) often exhibit systematic, literal, and highly repetitive syntax that statistical detectors routinely categorize as "robotic" and artificial.
3. **Reversal of the Presumption of Innocence:**
   Students accused of using AI cannot "prove a negative." Because LLMs leave no deterministic forensic artifacts, falsely accused students have no evidentiary mechanism to exonerate themselves.
4. **Chilling Effect on Learning:**
   Fear of false accusations drives students to deliberately write with worse grammar, awkward vocabulary, or artificial errors to "fool" university surveillance tools.

---

## 6. Originality Desk Architectural Policy

Based on this evaluation, Originality Desk adopts the following permanent architectural policies:

1. **No Universal "AI Percentage":**
   Originality Desk will never display a aggregate badge stating "XX% AI-Generated" or "Plagiarism Likelihood: High".
2. **Decoupling from Similarity Evidence:**
   Textual similarity (which provides verifiable, side-by-side exact coordinates to published literature) is completely separated from statistical AI indicators.
3. **Explicit Uncertainty Disclosures:**
   Any experimental feature analyzing text statistics (such as perplexity variance) must explicitly display the Stanford bias findings, document that scores are non-deterministic, and include a mandatory warning that scores cannot be used for punitive proceedings.
4. **Promotion of Pedagogical Alternatives:**
   Institutions are guided toward oral defenses, revision history verification, and process-based writing evaluation rather than algorithmic policing.
