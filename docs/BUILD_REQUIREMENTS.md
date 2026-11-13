# Originality Desk — Build & Dependency Specifications

**Specification Version:** 1.0.0  
**Phase:** 1 (Repository Quality, Architecture and Security Foundation)  
**PR:** 02 (`feature/pr-02-maven-build-reliability`)  

---

## 1. System Requirements

To ensure deterministic, reproducible builds and execution across developer machines, CI runners, and production containers, the following toolchains are required:

| Component | Minimum Version | Recommended Version | Verification Command |
|---|---|---|---|
| **Java Development Kit (JDK)** | OpenJDK 17.0.0 | Eclipse Temurin 17.0.14 | `java -version` |
| **Apache Maven** | Maven 3.8.0 | Maven 3.9.6 | `mvn -version` |
| **Node.js** (Frontend tests & E2E) | Node.js 18.0.0 | Node.js 20.x LTS | `node -v` |
| **Python** (Synthetic Benchmarks) | Python 3.9 | Python 3.11+ | `python3 --version` |

---

## 2. Pinned Properties & Dependency Versions

All third-party library versions are centrally managed in `pom.xml` properties:

```xml
<properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
    <project.build.outputTimestamp>2026-11-13T00:00:00Z</project.build.outputTimestamp>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <maven.compiler.release>17</maven.compiler.release>
    <javafx.version>21.0.2</javafx.version>
    <poi.version>5.2.5</poi.version>
    <pdfbox.version>3.0.5</pdfbox.version>
    <sqlite.version>3.45.1.0</sqlite.version>
    <gson.version>2.10.1</gson.version>
    <slf4j.version>1.7.36</slf4j.version>
    <bootstrapfx.version>0.4.0</bootstrapfx.version>
    <junit.version>5.10.2</junit.version>
</properties>
```

---

## 3. Dependency License & Security Matrix

All direct dependencies use permissive open-source licenses compatible with institutional deployment:

| Group / Artifact | Version | Declared License | Redistribution & Institutional Use |
|---|---|---|---|
| `org.openjfx:javafx-controls` | 21.0.2 | GPL v2 with Classpath Exception | Permitted (Standalone binary or modular runtime) |
| `org.openjfx:javafx-fxml` | 21.0.2 | GPL v2 with Classpath Exception | Permitted |
| `org.apache.poi:poi-ooxml` | 5.2.5 | Apache License 2.0 | Permitted without restriction |
| `org.apache.pdfbox:pdfbox` | 3.0.5 | Apache License 2.0 | Permitted without restriction |
| `org.xerial:sqlite-jdbc` | 3.45.1.0 | Apache License 2.0 | Permitted without restriction |
| `com.google.code.gson:gson` | 2.10.1 | Apache License 2.0 | Permitted without restriction |
| `org.slf4j:slf4j-jdk14` | 1.7.36 | MIT License | Permitted without restriction |
| `org.kordamp.bootstrapfx:bootstrapfx-core` | 0.4.0 | MIT License | Permitted without restriction |
| `org.junit.jupiter:junit-jupiter` | 5.10.2 | Eclipse Public License 2.0 | Test scope only |

---

## 4. Reproducible Build Configuration

Originality Desk enforces byte-for-byte build reproducibility via Maven's standard `project.build.outputTimestamp` property (`2026-11-13T00:00:00Z`). This guarantees identical JAR checksums across clean rebuilds regardless of filesystem modification timestamps or geographic timezone differences.

