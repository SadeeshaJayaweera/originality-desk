# Measured synthetic benchmark results

> **Engineering demonstration only.** These are measured outputs of the actual local evidence engine on 32 tiny, implementation-authored synthetic cases with explicitly enumerated review-policy labels. They are not scientific accuracy estimates, plagiarism judgments, independently human-adjudicated ground truth, or AI-authorship results. Semantic analysis and AI-writing scoring remain `NOT_IMPLEMENTED`.

See [EVALUATION_METHODOLOGY.md](EVALUATION_METHODOLOGY.md) for the labels, metric formulas, decision threshold, reproduction commands and limitations. Machine-readable results, including actual evidence offsets, are committed under [scripts/evaluation/results](../scripts/evaluation/results/).

## Execution and environment

The first successful recorded run was executed with:

```sh
python3 scripts/evaluation/run.py --offline --output scripts/evaluation/results
```

The runner used its defaults: 10 warm-up rounds and 30 measured rounds per profile. It successfully built the application with offline Maven packaging (`-DskipTests`), compiled the harness with Java 17 bytecode targeting and `-Xlint:all -Werror`, ran its metric/statistics self-checks, and independently audited the resulting files with Python. Packaging emitted Maven Shade overlap warnings but completed successfully. The packaging command did **not** execute the Maven test suite.

| Item | Observed value |
|---|---|
| Recorded JVM process interval | 2026-11-12 21:57:50.380088–21:57:52.780399 UTC (2026-11-13 03:27:50–03:27:52 at +05:30) |
| Engine | `local-evidence-1.0` |
| OS / architecture | macOS 15.8.3 / x86_64 |
| CPU | Intel Core i7-8850H @ 2.60 GHz; 12 logical CPUs visible to Python/JVM |
| Physical RAM | 17,179,869,184 bytes (16 GiB) |
| Runtime / compiler | Homebrew OpenJDK 21.0.10 / `javac` 21.0.10; harness compiled with `--release 17` |
| Maven / Python | Maven 3.9.6 / Python 3.11.4 |
| JVM flags | `-Xms64m -Xmx256m -XX:+UseG1GC -Dfile.encoding=UTF-8` |
| Revision | `ab8cac5351ac9795a341486276643f64a82a3ba2` |
| Working tree | Dirty; not a clean published-release measurement |
| Case sizes | 5–33 submitted normalized words; 24–283 submitted Java UTF-16 code units; 1–2 supplied sources per case |
| Binary review decision | Nonempty evidence and `totalOverlap >= 0.10` |
| Timed calls | 960 per profile; 2,880 total, not 2,880 independent accuracy examples |

Exact tool output, effective replay command, GC names and byte counts are in [environment.json](../scripts/evaluation/results/environment.json) and [report.json](../scripts/evaluation/results/report.json).

Artifact identifiers for this run:

```text
Application JAR SHA-256:
926c9bc42cb33b1ea8de672369178f82e6688b99175aa2cfc9c1a47f8c1867d4

Analysis class entries SHA-256:
3d116da37862b93b9e367ed91ac720c1a3fc91075484842ca5673786e2e0e276

Fixture manifest SHA-256:
0fffe9ab01158bd662906de91e9aa9f7faaef02084aa3c4cd9f9b498591614b5
```

A rebuilt JAR may have a different byte hash because of build timestamps/metadata; this digest identifies the actual measured artifact. The independent audit verifies saved artifact, fixture and runner hashes without requiring that JAR to remain on disk.

## PRIMARY policy cases

30 cases: **16 positive, 14 negative**. Metrics below are percentages, rounded to two decimal places; exact fractions and counts are in `report.json`. This table excludes the two explicitly semantic challenge cases, which are reported separately and also included in the ALL table.

| Profile | TP | FP | TN | FN | Precision % | Recall % | F1 % | FPR % | FNR % |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| QUICK | 7 | 3 | 11 | 9 | 70.00 | 43.75 | 53.85 | 21.43 | 56.25 |
| STANDARD | 14 | 4 | 10 | 2 | 77.78 | 87.50 | 82.35 | 28.57 | 12.50 |
| REFERENCE_AWARE | 14 | 2 | 12 | 2 | 87.50 | 87.50 | 87.50 | 14.29 | 12.50 |

REFERENCE_AWARE avoided the two bibliography-only policy false positives present in QUICK/STANDARD. That is a result on these two obvious English-heading fixtures, not a measured general bibliography-detection accuracy. It still flagged the shared technical vocabulary and contradictory clinical claim, illustrating why lexical overlap is not a meaning-aware verdict.

## Semantic-paraphrase challenge: explicit failures

Cases `semantic-synonym-heavy` and `semantic-concept-recast` are deliberately constructed conceptual restatements with little shared vocabulary. **Neither was detected by any profile.**

| Profile | TP | FN | Recall % | F1 % | FNR % | Precision | FPR |
|---|---:|---:|---:|---:|---:|---|---|
| QUICK | 0 | 2 | 0.00 | 0.00 | 100.00 | N/A: no positive decisions | N/A: no negative cases |
| STANDARD | 0 | 2 | 0.00 | 0.00 | 100.00 | N/A: no positive decisions | N/A: no negative cases |
| REFERENCE_AWARE | 0 | 2 | 0.00 | 0.00 | 100.00 | N/A: no positive decisions | N/A: no negative cases |

This is a direct, tiny demonstration of a known missing capability, **not** an estimate of semantic-paraphrase performance on a population. Lexical Jaccard is not embeddings, semantic similarity, or paraphrase understanding. No semantic or AI-authorship model was evaluated; those evaluations are unavailable.

## ALL cases, including the semantic misses

32 cases: **18 positive, 14 negative**. This aggregate makes the two challenge misses visible rather than folding them into an undisclosed exclusion.

| Profile | TP | FP | TN | FN | Precision % | Recall % | F1 % | FPR % | FNR % |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| QUICK | 7 | 3 | 11 | 11 | 70.00 | 38.89 | 50.00 | 21.43 | 61.11 |
| STANDARD | 14 | 4 | 10 | 4 | 77.78 | 77.78 | 77.78 | 28.57 | 22.22 |
| REFERENCE_AWARE | 14 | 2 | 12 | 4 | 87.50 | 77.78 | 82.35 | 14.29 | 22.22 |

## Scenario outcomes and observed limitations

These are actual per-category outcomes across all 32 cases. TP/TN/FP/FN use the fixed manual review policy, not a claim that the engine determines authorship or meaning.

| Scenario | QUICK | STANDARD | REFERENCE_AWARE |
|---|---|---|---|
| Exact copies/excerpts/Unicode/two source identities (4 positive) | 4 TP | 4 TP | 4 TP |
| Minor substitutions (2 positive) | 2 FN | 2 TP | 2 TP |
| Reordered sentences/words (2 positive) | 1 TP, 1 FN | 2 TP | 2 TP |
| Lexically close paraphrases (2 positive) | 2 FN | 1 TP, 1 FN | 1 TP, 1 FN |
| Quotes with/without citation patterns (2 positive) | 2 TP | 2 TP | 2 TP |
| Stock boilerplate (4 negative) | 4 TN | 4 TN | 4 TN |
| Bibliography-only sharing (2 negative) | 2 FP | 2 FP | 2 TN |
| Technical vocabulary/claims/procedures (3 negative) | 1 FP, 2 TN | 2 FP, 1 TN | 2 FP, 1 TN |
| Unrelated passages (4 negative) | 4 TN | 4 TN | 4 TN |
| Short fragments (4 positive, 1 negative) | 4 FN, 1 TN | 3 TP, 1 FN, 1 TN | 3 TP, 1 FN, 1 TN |
| Semantic challenge (2 positive) | 2 FN | 2 FN | 2 FN |

Notable cases:

- **`lexical-paraphrase-rephrased`:** missed by every profile. Even a fairly close rephrasing can evade the lexical retrieval/scoring path; one successful close-paraphrase fixture is not a general paraphrase capability.
- **`short-five-content-words`:** missed by every profile. The copied fragment is shorter than every supported exact or near window. QUICK also missed the six-, eight- and eleven-word copied fragments, consistent with its 12-word exact threshold.
- **`technical-shared-vocabulary`:** STANDARD/REFERENCE_AWARE returned total coverage 1.0 for deliberately reordered vocabulary with no substantive proposition under the policy. This is lexical word-set evidence, not semantic equivalence.
- **`technical-negated-claim`:** all profiles flagged the genuinely shared wording even though the constructed claim contradicts the source. QUICK coverage was 0.722222; STANDARD/REFERENCE_AWARE coverage was 1.0. The policy labels this negative; the underlying lexical evidence itself is real. Do not mistake a policy FP for fabricated matching text or automatic proof of plagiarism.
- **Bibliography cases:** QUICK/STANDARD coverage was 0.714286 for the shared-entry case and 1.0 for the reference-only case. REFERENCE_AWARE returned no evidence for either by excluding the detected trailing lists.
- **Quoted cases:** detected in all profiles, including the apparently cited quote. This correctly preserves overlap evidence under this policy; it does not validate the citation or imply misconduct.

Complete per-case outcomes, method/source ids, coverage and timing are in [case-results.csv](../scripts/evaluation/results/case-results.csv). `report.json` also retains actual source/submission evidence coordinates and uncertain citation observations.

## Measured engine-call timing

Milliseconds per `analyze` call; 960 samples per profile, mixing all 32 small cases. These values are from the first recorded run, not selected best timings from repeats.

| Profile | Mean ms | Median ms | p95 ms | Min ms | Max ms | Measured-loop wall ms |
|---|---:|---:|---:|---:|---:|---:|
| QUICK | 0.306124 | 0.237119 | 0.798052 | 0.036405 | 2.691648 | 351.881 |
| STANDARD | 0.401269 | 0.328737 | 0.878235 | 0.038254 | 4.617945 | 420.467 |
| REFERENCE_AWARE | 0.373734 | 0.307705 | 0.835081 | 0.040950 | 5.819041 | 400.038 |

The call timer includes the engine's normalization and per-call index work, but excludes fixture I/O, validation, hashing/consumption, heap sampling and serialization. The wall column includes measured-loop harness overhead, not Maven, compilation or warm-up. p95 uses nearest rank. Raw nanoseconds are in [timings.csv](../scripts/evaluation/results/timings.csv).

A second separate JVM run against the same JAR/fixtures produced **identical observations and evidence for all 96 case/profile combinations**, including identical confusion metrics. Its medians were QUICK 0.189960 ms, STANDARD 0.340595 ms and REFERENCE_AWARE 0.214263 ms; p95 values were 0.682655, 1.055403 and 0.690898 ms respectively. This timing variability is why no speed-ranking or production-latency claim is made. The second verification run was written to `target/evaluation-reproducibility/`, not substituted for the committed first-run measurements.

## Observed JVM heap

All profiles had an observed maximum heap of **268,435,456 bytes (256 MiB)**. MiB below means bytes / 1,048,576. Values are rounded for display; the JSON retains exact bytes.

| Profile | Used before MiB | Used after MiB | Sampled used high-water MiB | Committed before/after MiB | GC collections | GC time ms |
|---|---:|---:|---:|---:|---:|---:|
| QUICK | 8.187 | 27.288 | 39.187 | 66 / 66 | 1 | 3 |
| STANDARD | 19.332 | 21.417 | 39.457 | 66 / 66 | 2 | 4 |
| REFERENCE_AWARE | 13.346 | 15.502 | 39.454 | 66 / 66 | 2 | 9 |

**These are not true peak-memory or per-call allocation measurements.** Used heap was sampled around calls, so transient in-call peaks can be missed. Profiles ran sequentially in one JVM and inherit existing state/garbage; the figures include retained harness records and other JVM heap objects. Actual committed heap was 66 MiB, despite the 64 MiB initial-heap flag. No RSS, allocation profiler, heap-retention study or maximum-capacity benchmark was performed.

## Interpretation and validation status

- The offline Maven package build and warning-free standalone harness compilation succeeded.
- Harness formula/percentile/undefined-denominator self-checks passed.
- The Python auditor independently recomputed all metrics and timing summaries and verified saved-artifact/fixture/runner digests.
- Results were deterministic within measured repetitions and reproduced across a second JVM for all 96 observations/evidence sets.
- The Maven test suite was **not rerun as part of this benchmark**; the evaluation launcher packages with `-DskipTests`.
- The examples are small, templated, English-heavy and non-independent. They were constructed with knowledge of engine behavior, not collected as a blinded held-out corpus. Independent human adjudication and a representative external evaluation dataset are unavailable.
- No population confidence intervals, scientific accuracy, model/AI-authorship performance, semantic capability, real-world plagiarism rates, production latency/capacity, extraction/OCR performance or cross-platform performance conclusions follow from these numbers.

To reproduce and inspect rather than trust this document:

```sh
python3 scripts/evaluation/run.py --verify-only scripts/evaluation/results
python3 scripts/evaluation/run.py --offline --output target/evaluation
```
