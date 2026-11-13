# Originality Desk — Local Development & Setup Guide

This guide covers building, testing, and running Originality Desk on a local developer workstation.

---

## 1. System Requirements

Ensure the following tools are installed and accessible on your system `PATH`:

| Requirement | Minimum Version | Purpose |
|-------------|-----------------|---------|
| **JDK** | 17 (Java 17 LTS or 21 LTS) | Application compilation and runtime (Eclipse Temurin, OpenJDK) |
| **Apache Maven** | 3.9.0+ | Dependency management and build packaging |
| **Node.js** | 20.0.0+ | Frontend unit testing, markup validation, and browser E2E tests |
| **Python** | 3.10+ | Scientific evaluation harness and statistical verification |

Verify your environment:
```sh
java -version
mvn -version
node -v
python3 --version
```

---

## 2. Cloning & Initial Build

Clone the repository and compile all components:

```sh
git clone https://github.com/SadeeshaJayaweera/originality-desk.git
cd originality-desk

# Compile all main and test classes
mvn clean test-compile

# Package the production shaded JAR (includes all dependencies)
mvn package -DskipTests
```

The resulting executable fat JAR is generated at:
```
target/writing-assistant-1.0.0-SNAPSHOT.jar
```

---

## 3. Running Originality Desk

Originality Desk supports multiple execution modes:

### Mode A: Originality Desk Web Server (Primary Academic Platform)

The web server serves the responsive browser interface, REST API (`/api/v1/*`), health probes, and background document extraction/similarity workers.

```sh
# Option 1: Using the unified entry point flag
java -jar target/writing-assistant-1.0.0-SNAPSHOT.jar --server

# Option 2: Direct server class execution
java -cp target/writing-assistant-1.0.0-SNAPSHOT.jar com.writingassistant.server.OriginalityDeskServer
```

Once started:
1. Open your browser and navigate to: **[http://localhost:8080](http://localhost:8080)**
2. Click **Create workspace** to register your initial institution administrator account.
3. Access the dashboard, upload submissions and comparison reference documents, start analyses, and inspect evidence reports.

To configure server port or data storage directory, set environment variables before running:
```sh
export DESK_PORT=9090
export DESK_PUBLIC_ORIGIN=http://localhost:9090
export DESK_DATA_DIR=./custom-data-dir
java -jar target/writing-assistant-1.0.0-SNAPSHOT.jar --server
```

### Mode B: Preserved Desktop Writing Assistant (JavaFX GUI)

The repository preserves the desktop editor for offline writing style rewriting, paraphrase generation, and citation formatting.

```sh
# Run the JavaFX desktop application
java -jar target/writing-assistant-1.0.0-SNAPSHOT.jar

# Or run via Maven JavaFX plugin
mvn javafx:run
```

*Note: Requires a graphical desktop environment with display server (X11 / Wayland / macOS Quartz).*

### Mode C: Headless Desktop Smoke Test

Executes a command-line diagnostic verifying local SQLite database persistence, similarity tokenization, and citation formatting:

```sh
java -jar target/writing-assistant-1.0.0-SNAPSHOT.jar --smoke-test
```

---

## 4. Running the Automated Test Suite

Originality Desk includes automated testing across Java backend services, APIs, security boundaries, frontend JavaScript, and evaluation fixtures.

### 4.1 Java Unit & Integration Tests

```sh
# Run all unit and integration tests
mvn test

# Run specific test classes:
mvn test -Dtest=OriginalityDeskServerTest      # Server REST API and worker lifecycle (23 tests)
mvn test -Dtest=ServerSecurityTest             # CSRF, rate-limiting, and tenant isolation (9 tests)
mvn test -Dtest=EvidenceEngineTest             # Text normalisation, exact & near matching (14 tests)
mvn test -Dtest=DocumentExtractorTest          # PDF, DOCX, and TXT parsing limits
mvn test -Dtest=SqliteDocumentDaoTest          # Desktop database persistence
```

### 4.2 Frontend & Markup Unit Tests

Execute the Node.js test suites (no external npm dependencies required):

```sh
# Test zip fixtures, CRC32, and synthetic DOCX/TXT generators
node scripts/e2e/fixtures.test.cjs

# Test landing page HTML markup balance and navigation integrity
node scripts/e2e/markup.test.cjs

# Test frontend upload validation, offset handling, and CSP compliance
node src/main/resources/web/frontend.test.cjs
```

### 4.3 Scientific Evaluation Benchmark

Verify the 32 synthetic benchmark test cases and confusion matrix metrics:

```sh
python3 scripts/evaluation/run.py --verify-only scripts/evaluation/results
```

To run a full local benchmark iteration with warm-up cycles and CPU timing measurements:
```sh
python3 scripts/evaluation/run.py --offline --output scripts/evaluation/results
```

### 4.4 Real Playwright Browser End-to-End Tests (Optional)

If Playwright is installed in your local node environment:
```sh
node scripts/e2e/real-browser.cjs /path/to/playwright/node_modules/playwright target/writing-assistant-1.0.0-SNAPSHOT.jar
```

---

## 5. Local Troubleshooting

### Port Already in Use (EADDRINUSE)
If port 8080 is occupied by another process:
```sh
# Change port via environment variables
DESK_PORT=8081 DESK_PUBLIC_ORIGIN=http://localhost:8081 java -jar target/writing-assistant-1.0.0-SNAPSHOT.jar --server
```

### SQLite File Lock (`server.lock`)
Only one server process can access a `DESK_DATA_DIR` simultaneously. If a previous process terminated uncleanly while holding the lock:
1. Ensure no background Java processes are running (`ps aux | grep java`).
2. Verify lock clearance or inspect the directory specified by `DESK_DATA_DIR`.

### Invalid Host / 403 Forbidden on API Requests
The server enforces that the HTTP `Host` header strictly matches the authority defined in `DESK_PUBLIC_ORIGIN`. If accessing the server via an IP address (e.g., `http://127.0.0.1:8080`), you must configure `DESK_PUBLIC_ORIGIN` accordingly:
```sh
DESK_HOST=127.0.0.1 DESK_PORT=8080 DESK_PUBLIC_ORIGIN=http://127.0.0.1:8080 java -jar target/writing-assistant-1.0.0-SNAPSHOT.jar --server
```

