# Synthetic similarity evaluation methodology

## Purpose and non-claims

This is a reproducible **engineering demonstration of local source-review behavior**, not a scientific accuracy study, plagiarism detector validation, or authorship classifier evaluation. It calls `com.writingassistant.analysis.EvidenceEngine` from the Maven-built application JAR. It does not replace the engine with a mock, infer expected labels from engine scores, or contact any model service.

The fixtures were authored by the coding assistant during implementation. Binary labels and rationales are explicitly enumerated in the fixture file and intended for human inspection/editing; they are not independent human annotations. No expert adjudication, inter-rater agreement, representative research dataset, held-out population, or human-subject study is available. A human-reviewed evaluation corpus remains future work. No AI-authorship benchmark, model comparison, semantic model evaluation, or OCR evaluation is available or claimed.

## Reproduce

Requirements: Python **3.9+** (standard library only), a JDK **17+** with `java` and `javac`, and Maven on `PATH`. Run from the repository root:

```sh
python3 scripts/evaluation/run.py --output target/evaluation
```

The default command runs `mvn --batch-mode --no-transfer-progress -DskipTests package`, compiles the standalone evaluation classes with `javac --release 17 -Xlint:all -Werror`, then evaluates the shaded `target/writing-assistant-1.0.0-SNAPSHOT.jar`. Tests are **not** run by that packaging command. The runner uses the application's existing Gson dependency; it installs no Python packages and downloads nothing directly. Maven may resolve the existing project dependencies/plugins normally.

With a populated Maven cache, prohibit Maven network access:

```sh
python3 scripts/evaluation/run.py --offline --output target/evaluation
```

If a current parent build already produced the JAR, explicitly reuse it:

```sh
python3 scripts/evaluation/run.py --skip-build --warmup 10 --iterations 30 --output target/evaluation
```

`--skip-build` can benchmark a stale JAR: results then describe that artifact, not necessarily the current source tree. The runner records the artifact digest and checks that it did not change during measurement. Prefer the default fresh Maven build when comparing code changes. It does not start the server or desktop UI.

Audit the committed run without Java, Maven, or a new measurement:

```sh
python3 scripts/evaluation/run.py --verify-only scripts/evaluation/results
```

Audit a fresh local run:

```sh
python3 scripts/evaluation/run.py --verify-only target/evaluation
```

The auditor independently recomputes metrics from observations and timings from raw nanosecond samples, cross-checks the case CSV, and checks fixture/runner/artifact hashes. It intentionally rejects recorded results after fixture or runner changes. Re-run the benchmark to establish a new baseline; do not edit measured numbers to make the audit pass.

## Fixtures and manual policy labels

`tests/fixtures/evaluation/cases.json` contains 32 small synthetic submissions and their actual supplied source corpora. All text, labels, categories, cohort membership, and rationales are checked in. There is no random text generator or external dataset download. Labels were fixed before the initial benchmark execution; no engine thresholds or fixtures were tuned after seeing the scores.

- **PRIMARY:** 30 cases, 16 positive and 14 negative.
- **SEMANTIC_CHALLENGE:** 2 positive conceptual paraphrases with very little shared vocabulary. These are explicitly out of the lexical engine's implemented capability.
- **ALL:** all 32 cases, 18 positive and 14 negative. This additional aggregate retains, rather than conceals, the semantic misses; it must not be called lexical-only accuracy.

| Category | Cases | Positive | Negative |
|---|---:|---:|---:|
| Exact copying, excerpts, Unicode normalization, two source identities | 4 | 4 | 0 |
| Minor substitutions | 2 | 2 | 0 |
| Reordered sentences/content words | 2 | 2 | 0 |
| Lexically close paraphrases | 2 | 2 | 0 |
| Quoted passages, with/without citation patterns | 2 | 2 | 0 |
| Stock boilerplate/low-information sequences | 4 | 0 | 4 |
| Bibliography-only sharing | 2 | 0 | 2 |
| Technical vocabulary/contradictory or different procedures | 3 | 0 | 3 |
| Unrelated topics, same title, or same broad topic | 4 | 0 | 4 |
| Short fragments (5, 6, 8, 11 words and a generic transition) | 5 | 4 | 1 |
| Low-lexical-overlap semantic paraphrase challenge | 2 | 2 | 0 |

**Label target:** an illustrative review policy queues deliberately copied prose, quotations, and constructed meaning-preserving paraphrases. Quoted/cited copying is positive *overlap evidence*, never automatically an allegation of misconduct. Bibliography-only and stock material are negative policy cases even when their strings literally match. Labels are identical across profiles: otherwise reference handling would be rewarded using profile-dependent ground truth.

The technical vocabulary and contradictory-claim cases deliberately ask for a content-level judgment a lexical matcher cannot reliably make. The contradictory sentence genuinely shares wording; a policy false positive does **not** mean the engine fabricated its lexical evidence, proved borrowing, or implemented semantic contradiction detection. These policy choices are inspectable and contestable, not objective authorship ground truth. Do not relabel them automatically based on the engine result.

## Engine invocation and binary decision

Each case invokes:

```java
engine.analyze(caseId, caseId, submittedText, suppliedSources, profile)
```

Every case is evaluated under `QUICK`, `STANDARD`, and `REFERENCE_AWARE`. The engine receives source text and metadata, **not** expected labels or rationales. Sources are isolated per case; a few cases include two sources. They do not exercise a large corpus.

The fixed document-level decision is positive exactly when:

```text
matches is nonempty AND totalOverlap >= 0.10
```

`0.10` is a harness review threshold, chosen before execution, not an engine confidence score or a tuned scientific cutoff. The engine's coverage fractions are unions of submitted normalized words: exact coverage first, near only additional words, total their union. Near-window coverage does not mean each word was copied. Metrics do not evaluate exact span boundaries, rank quality, source-retrieval recall on an external collection, or calibrated confidence.

The engine's existing behavior remains unchanged:

- `QUICK`: exact shingles of at least 12 normalized words; no near matching.
- `STANDARD`: exact shingles of at least 8 words plus bounded lexical Jaccard retrieval.
- `REFERENCE_AWARE`: exact shingles of at least 6 words plus lexical retrieval; detected trailing references excluded from matching and the coverage denominator.
- Near matching is lexical content-word-set Jaccard, not semantic similarity or embeddings. Citation observations are heuristic and unvalidated.

The harness checks evidence substrings against original UTF-16 source/submission offsets, source identities/metadata, union coverage, uncertain citation coordinates, and the explicit `NOT_IMPLEMENTED` semantic/AI-writing statuses. It also requires deterministic results across measured repetitions. A missed positive is reported, not converted into an exception or relabeled.

## Metrics and denominators

The unit is **one fixture case per profile**, regardless of timing repetitions or number of matching sources.

- TP: positive label, positive decision.
- FN: positive label, negative decision.
- FP: negative label, positive decision.
- TN: negative label, negative decision.
- Precision = TP / (TP + FP).
- Recall = TP / (TP + FN).
- F1 = 2TP / (2TP + FP + FN).
- False-positive rate (FPR) = FP / (FP + TN), **not** 1 − precision.
- False-negative rate (FNR) = FN / (FN + TP) = 1 − recall when defined.

A zero denominator is recorded as JSON `null` / documentation `N/A`, not silently reported as zero. For example, the all-positive semantic challenge has no defined FPR, and precision is undefined when it produces no positive predictions. F1 is zero if FN is nonzero and TP/FP are zero. Confusion counts accompany all percentages so the small denominators remain visible.

Both the Java metric self-checks and independent Python recomputation check formulas, undefined denominators, timing units, medians and nearest-rank p95. Passing those checks establishes harness consistency, not predictive accuracy.

## Timing and JVM heap protocol

One JVM, sequential profiles in fixed order: `QUICK`, `STANDARD`, `REFERENCE_AWARE`. Flags are `-Xms64m -Xmx256m -XX:+UseG1GC -Dfile.encoding=UTF-8`.

1. Read fixtures once, perform one untimed result/provenance pass per profile.
2. Default **10 warm-up rounds** per profile: every fixture once per round, excluded from reported samples.
3. Default **30 measured rounds** per profile: 32 × 30 = 960 timed calls per profile, 2,880 total.
4. Rotate fixture order deterministically by one position each round. Source normalization/index construction occurs inside each engine call; there is no prebuilt shared corpus index.
5. Use `System.nanoTime()` immediately around `analyze`. Validation, result hashing/consumption, heap sampling, sample recording, file I/O, compilation, and Maven startup are outside the call timer.
6. Report mean, median, nearest-rank p95 (`ceil(0.95 × n)`), min and max in milliseconds, with every original sample saved in nanoseconds. Per-profile timing mixes all 32 cases, including the semantic challenges; it is not PRIMARY-only timing.

The separately reported measurement-window wall time includes loop, validation, result consumption and sampling overhead; it is not the sum of engine-only call times. There is no forced garbage collection and no subtraction of timer overhead.

JVM heap figures come from `MemoryMXBean`: configured maximum, used/committed before and after the measured loop, and the highest observed used value sampled before/after calls. This sampled high-water value can miss transient **in-call peaks** and does not attribute allocations to a single call. Later profiles inherit JVM state, JIT/class metadata effects, retained harness results, and uncollected garbage. GC collection/time deltas are recorded. Heap usage is not allocation rate, retained engine memory, process RSS, or a hard resource bound.

This is **not JMH**: one process, no independent JVM forks, no process isolation/CPU pinning, no stable-load guarantee, and limited warm-up. Very short inputs, JIT, GC, scheduler delays, power/thermal state and other editor activity can dominate latency. A p95 over repeated calls to 32 tiny cases is not production-tail latency. Timing repetitions are not new accuracy samples or independent statistical trials. Do not report significance, population confidence intervals, scientific accuracy, throughput/capacity, maximum supported document size, or cross-machine performance superiority from this run.

## Recorded artifacts

Each successful run publishes these files only after computation and independent verification succeed:

- `report.json`: fixture provenance/policy, configuration, JVM details, per-cohort confusion metrics, per-case outcomes/evidence coordinates and timing/heap summaries.
- `case-results.csv`: one actual engine observation per case/profile, including overlap, method/source ids and latency summaries.
- `timings.csv`: all raw measured call samples.
- `environment.json`: UTC start/finish, OS/CPU/RAM/tool versions, revision/dirty flag, exact invocation, application JAR SHA-256, analysis-class digest, fixture/runner digests and result-artifact digests.

The analysis-class digest hashes sorted `com/writingassistant/analysis/*.class` entry names and bytes. The runner digest hashes the Python launcher followed by the sorted Java source paths/bytes. The JAR byte hash identifies the exact measured artifact; it may change between otherwise equivalent Maven builds because of build metadata/timestamps. The dirty flag is explicit: this benchmark need not describe a clean published release.

The committed run and observed failures are documented in [BENCHMARK_RESULTS.md](BENCHMARK_RESULTS.md). Machine-readable reference output is under `scripts/evaluation/results/`. Re-running should preserve classifications for the same engine/fixtures, but exact times, GC counts, heap figures and build hashes need not be identical.
