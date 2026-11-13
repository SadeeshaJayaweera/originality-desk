#!/usr/bin/env python3
"""Build/run the real engine benchmark, or independently audit recorded results. Stdlib only."""

import argparse
from collections import Counter
from datetime import datetime, timezone
import csv
import hashlib
import json
import math
import os
from pathlib import Path
import platform
import re
import shutil
import statistics
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[2]
FIXTURES = ROOT / "tests/fixtures/evaluation/cases.json"
JAR = ROOT / "target/writing-assistant-1.0.0-SNAPSHOT.jar"
JAVA_SOURCES = sorted((ROOT / "scripts/evaluation/java").glob("*.java"))
ARTIFACTS = ("report.json", "case-results.csv", "timings.csv")


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def capture(command):
    result = subprocess.run(command, cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=True)
    return re.sub(r"\x1b\[[0-9;]*m", "", result.stdout).strip()


def optional(command):
    try:
        return capture(command)
    except (OSError, subprocess.CalledProcessError):
        return "unavailable"


def execute(command):
    print("+ " + " ".join(map(str, command)), flush=True)
    subprocess.run(list(map(str, command)), cwd=ROOT, check=True)


def runner_digest():
    digest = hashlib.sha256()
    for path in [Path(__file__).resolve(), *JAVA_SOURCES]:
        digest.update(path.relative_to(ROOT).as_posix().encode("utf-8") + b"\0" + path.read_bytes())
    return digest.hexdigest()


def engine_digest():
    digest = hashlib.sha256()
    with zipfile.ZipFile(JAR) as archive:
        names = sorted(n for n in archive.namelist() if n.startswith("com/writingassistant/analysis/") and n.endswith(".class"))
        if not names or "com/google/gson/Gson.class" not in archive.namelist():
            raise ValueError("Expected the Maven shaded application JAR containing the engine and existing Gson dependency")
        for name in names:
            digest.update(name.encode("utf-8") + b"\0" + archive.read(name))
    return digest.hexdigest()


def classification(observations):
    counts = Counter(o["outcome"] for o in observations)
    tp, fp, tn, fn = (counts[k] for k in ("TP", "FP", "TN", "FN"))

    def ratio(a, b):
        return a / b if b else None

    return dict(cases=len(observations), positives=tp + fn, negatives=tn + fp, tp=tp, fp=fp, tn=tn, fn=fn,
                precision=ratio(tp, tp + fp), recall=ratio(tp, tp + fn), f1=ratio(2 * tp, 2 * tp + fp + fn),
                falsePositiveRate=ratio(fp, fp + tn), falseNegativeRate=ratio(fn, fn + tp))


def timing(samples):
    values = sorted(samples)
    return dict(samples=len(values), meanMs=statistics.mean(values) / 1e6, medianMs=statistics.median(values) / 1e6,
                p95Ms=values[math.ceil(len(values) * 0.95) - 1] / 1e6, minMs=values[0] / 1e6, maxMs=values[-1] / 1e6)


def same(actual, expected, context):
    if actual is None or expected is None:
        if actual is not expected:
            raise ValueError(f"{context}: {actual!r} != {expected!r}")
    elif isinstance(expected, (int, float)) and not isinstance(expected, bool):
        if not math.isclose(actual, expected, rel_tol=1e-9, abs_tol=1e-9):
            raise ValueError(f"{context}: {actual!r} != {expected!r}")
    elif actual != expected:
        raise ValueError(f"{context}: {actual!r} != {expected!r}")


def verify(directory, check_environment=True):
    fixtures = json.loads(FIXTURES.read_text(encoding="utf-8"))
    cases = {c["id"]: c for c in fixtures["cases"]}
    if len(cases) != len(fixtures["cases"]):
        raise ValueError("Duplicate fixture ids")
    report = json.loads((directory / "report.json").read_text(encoding="utf-8"))
    same(report["dataset"], fixtures["name"], "dataset")
    same(report["overlapThreshold"], 0.10, "fixed prediction threshold")
    same([p["profile"] for p in report["profiles"]], ["QUICK", "STANDARD", "REFERENCE_AWARE"], "profiles")
    with (directory / "timings.csv").open(newline="", encoding="utf-8") as stream:
        samples = list(csv.DictReader(stream))
    raw = {}
    keys = set()
    for sample in samples:
        key = (sample["profile"], sample["case_id"], int(sample["round"]))
        if key in keys or key[1] not in cases or not 0 <= key[2] < report["measuredRounds"]:
            raise ValueError("Duplicate/invalid raw timing sample")
        keys.add(key)
        value = int(sample["elapsed_ns"])
        if value < 0:
            raise ValueError("Negative timing")
        raw.setdefault(key[:2], []).append(value)
    same(len(samples), len(cases) * 3 * report["measuredRounds"], "sample count")
    with (directory / "case-results.csv").open(newline="", encoding="utf-8") as stream:
        csv_rows = list(csv.DictReader(stream))
    indexed_csv = {(row["profile"], row["case_id"]): row for row in csv_rows}
    same(len(indexed_csv), len(cases) * 3, "case CSV count")
    same(len(csv_rows), len(indexed_csv), "case CSV uniqueness")
    for profile in report["profiles"]:
        observations = [c["observation"] for c in profile["cases"]]
        same(len(observations), len(cases), "classification count")
        same({o["caseId"] for o in observations}, set(cases), "case identities")
        for item in profile["cases"]:
            o = item["observation"]
            fixture = cases[o["caseId"]]
            for field in ("category", "cohort", "label"):
                same(o[field], fixture[field], "fixed manual label/stratum")
            predicted = bool(o["evidence"]) and o["totalOverlap"] >= report["overlapThreshold"]
            same(o["predicted"], predicted, "prediction")
            outcome = ("TP" if predicted else "FN") if fixture["label"] == "POSITIVE" else ("FP" if predicted else "TN")
            same(o["outcome"], outcome, "outcome")
            same(o["semanticStatus"], "NOT_IMPLEMENTED", "semantic status")
            same(o["aiWritingStatus"], "NOT_IMPLEMENTED", "AI status")
            row = indexed_csv[(profile["profile"], o["caseId"])]
            for field in ("category", "cohort", "label", "outcome"):
                same(row[field], o[field], "case CSV")
            same(row["predicted"], str(predicted).lower(), "case CSV prediction")
            for field, column in (("exactOverlap", "exact_overlap"), ("nearOverlap", "near_overlap"), ("totalOverlap", "total_overlap")):
                same(float(row[column]), o[field], "case CSV overlap")
            values = raw[(profile["profile"], o["caseId"])]
            same(len(values), report["measuredRounds"], "per-case repetitions")
            for field, value in timing(values).items():
                same(item["timing"][field], value, "per-case timing")
            same(float(row["median_ms"]), item["timing"]["medianMs"], "CSV median")
            same(float(row["p95_ms"]), item["timing"]["p95Ms"], "CSV p95")
        for cohort in ("PRIMARY", "SEMANTIC_CHALLENGE", "ALL"):
            selected = [o for o in observations if cohort == "ALL" or o["cohort"] == cohort]
            for field, value in classification(selected).items():
                same(profile["metrics"][cohort][field], value, "classification metric")
        values = [int(s["elapsed_ns"]) for s in samples if s["profile"] == profile["profile"]]
        for field, value in timing(values).items():
            same(profile["timing"][field], value, "profile timing")
    if check_environment:
        environment = json.loads((directory / "environment.json").read_text(encoding="utf-8"))
        same(environment["fixtureSha256"], sha256(FIXTURES), "fixture digest")
        same(environment["runnerSha256"], runner_digest(), "runner digest")
        for name in ARTIFACTS:
            same(environment["artifactSha256"][name], sha256(directory / name), "recorded artifact digest")
    print("Verified fixed labels, all confusion metrics, per-case outcomes, raw sample counts, and timing summaries.", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "target/evaluation")
    parser.add_argument("--skip-build", action="store_true", help="Explicitly reuse the existing Maven shaded JAR")
    parser.add_argument("--offline", action="store_true", help="Pass --offline to Maven; requires cached Maven dependencies")
    parser.add_argument("--warmup", type=int, default=10, help="Whole-case rounds per profile, excluded from timing")
    parser.add_argument("--iterations", type=int, default=30, help="Measured whole-case rounds per profile")
    parser.add_argument("--verify-only", type=Path, help="Audit saved output with Python only; does not build or run Java")
    args = parser.parse_args()
    if args.verify_only:
        verify(args.verify_only.resolve())
        return
    if not 0 <= args.warmup <= 1000 or not 1 <= args.iterations <= 1000:
        parser.error("--warmup must be 0..1000 and --iterations 1..1000")
    for tool in ("java", "javac", "mvn"):
        if shutil.which(tool) is None:
            parser.error(f"Required tool not found on PATH: {tool}")
    if not args.skip_build:
        command = ["mvn", "--batch-mode", "--no-transfer-progress", "-DskipTests", "package"]
        if args.offline:
            command.insert(1, "--offline")
        execute(command)
    if not JAR.is_file():
        parser.error(f"Missing {JAR.relative_to(ROOT)}; run Maven package or omit --skip-build")
    artifact_digest = sha256(JAR)
    classes = ROOT / "target/evaluation-runner/classes"
    classes.mkdir(parents=True, exist_ok=True)
    execute(["javac", "--release", "17", "-encoding", "UTF-8", "-Xlint:all", "-Werror", "-cp", JAR, "-d", classes, *JAVA_SOURCES])
    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".evaluation-", dir=output.parent) as temporary:
        staging = Path(temporary)
        started = datetime.now(timezone.utc).isoformat()
        execute(["java", "-Xms64m", "-Xmx256m", "-XX:+UseG1GC", "-Dfile.encoding=UTF-8",
                 "-cp", str(classes) + os.pathsep + str(JAR), "com.writingassistant.evaluation.SyntheticBenchmark",
                 "--fixtures", FIXTURES, "--output", staging, "--warmup", args.warmup, "--iterations", args.iterations])
        finished = datetime.now(timezone.utc).isoformat()
        verify(staging, check_environment=False)
        same(sha256(JAR), artifact_digest, "application JAR changed during run")
        cpu_model = optional(["sysctl", "-n", "machdep.cpu.brand_string"]) if sys.platform == "darwin" else platform.processor()
        if sys.platform == "darwin":
            physical_memory = optional(["sysctl", "-n", "hw.memsize"])
        else:
            try:
                physical_memory = str(os.sysconf("SC_PHYS_PAGES") * os.sysconf("SC_PAGE_SIZE"))
            except (ValueError, OSError, AttributeError):
                physical_memory = "unavailable"
        environment = dict(startedUtc=started, finishedUtc=finished, platform=platform.platform(),
                           operatingSystemVersion=platform.mac_ver()[0] if sys.platform == "darwin" else platform.release(),
                           cpuModel=cpu_model, logicalCpuCount=os.cpu_count(), physicalMemoryBytes=physical_memory,
                           pythonVersion=platform.python_version(), javaVersion=capture(["java", "-version"]),
                           javacVersion=capture(["javac", "-version"]), mavenVersion=capture(["mvn", "--version"]),
                           gitRevision=optional(["git", "--no-pager", "rev-parse", "HEAD"]),
                           gitDirty=bool(optional(["git", "--no-pager", "--no-optional-locks", "status", "--porcelain"])),
                           applicationJar=JAR.relative_to(ROOT).as_posix(), applicationJarSha256=artifact_digest,
                           analysisClassesSha256=engine_digest(), fixtureSha256=sha256(FIXTURES), runnerSha256=runner_digest(),
                           buildRequested=not args.skip_build, offlineBuild=args.offline,
                           command=["python3", "scripts/evaluation/run.py", "--warmup", str(args.warmup), "--iterations", str(args.iterations)],
                           artifactSha256={name: sha256(staging / name) for name in ARTIFACTS})
        environment["command"].extend(["--output", output.relative_to(ROOT).as_posix() if output.is_relative_to(ROOT) else str(output)])
        if args.skip_build:
            environment["command"].append("--skip-build")
        if args.offline:
            environment["command"].append("--offline")
        (staging / "environment.json").write_text(json.dumps(environment, indent=2) + "\n", encoding="utf-8")
        verify(staging)
        output.mkdir(parents=True, exist_ok=True)
        for name in (*ARTIFACTS, "environment.json"):
            (staging / name).replace(output / name)
    print(f"Complete benchmark: {output}")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, subprocess.CalledProcessError) as error:
        print(f"Evaluation failed: {error}", file=sys.stderr)
        sys.exit(1)
