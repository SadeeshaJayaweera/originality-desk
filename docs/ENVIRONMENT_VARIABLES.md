# Originality Desk — Environment Variables Reference

This document provides a comprehensive specification of all environment variables supported by Originality Desk.

---

## 1. Core Server Variables

| Variable | Type | Default | Production Recommendation | Description |
|---|---|---|---|---|
| `DESK_HOST` | String | `127.0.0.1` | `127.0.0.1` or `0.0.0.0` (Docker) | Network interface address for the JDK HTTP socket binding. Do not expose `0.0.0.0` directly to public networks without a reverse proxy. |
| `DESK_PORT` | Integer | `8080` | `8080` | TCP port for the HTTP listener. Port `0` is supported only in automated tests to obtain an ephemeral dynamic port. |
| `DESK_PUBLIC_ORIGIN` | URI | `http://localhost:8080` | `https://originality.university.edu` | **Critical Security Setting.** The exact browser-visible canonical origin (scheme, domain, optional port). The server strictly matches the incoming `Host` header and mutation `Origin` headers against this value to defeat CSRF and DNS rebinding attacks. |
| `DESK_DATA_DIR` | Path | `./desk-server-data` | `/var/lib/originality-desk` | Filesystem path for persistence. Contains `server.sqlite3`, WAL/SHM files, `server.lock`, and the `documents/` blob storage directory. Must reside on a local POSIX-compliant filesystem. |
| `DESK_SECURE_COOKIES` | Boolean | `false` | `true` | When set to `true`, appends `; Secure` to the `desk_session` HTTP cookie. Mandatory whenever the application is served over HTTPS. |
| `DESK_RETENTION_DAYS` | Integer | `0` | `90` or `180` | Document and analysis data retention lifespan in days. When `0`, files remain stored until explicitly deleted by users. When $>0$, older records are purged automatically during background sweeps. |
| `DESK_JOB_TIMEOUT_SECONDS` | Integer | `60` | `60`–`120` | Bounded execution deadline in seconds for a single document extraction or similarity analysis task. Must be between `5` and `600`. Protects against uncooperative parser hangs or decompression loops. |

---

## 2. Java Virtual Machine Options

| Variable | Default | Recommended Production Value | Description |
|---|---|---|---|
| `JAVA_OPTS` | None | `-Xms256m -Xmx1024m -XX:+UseG1GC -Dfile.encoding=UTF-8` | JVM heap, garbage collector, and character encoding flags passed to the startup script or container runner. |

---

## 3. Desktop Application Variables (Desktop Mode Only)

| Variable | Type | Default | Description |
|---|---|---|---|
| `GEMINI_API_KEY` | String | None | Optional Google Gemini API key used **exclusively** by the preserved desktop writing assistant (JavaFX GUI) for authorial style rewriting and paraphrase generation. **The Originality Desk web server and similarity detection engine do not use this key and never transmit documents to third-party AI APIs.** |

---

## 4. Docker Compose Variables

| Variable | Default | Description |
|---|---|---|
| `HOST_PORT` | `8080` | The external TCP port exposed on the Docker host machine and mapped to container port 8080. |

---

## 5. Configuration Profiles

### 5.1 Local Development (`.env`)
```ini
DESK_HOST=127.0.0.1
DESK_PORT=8080
DESK_PUBLIC_ORIGIN=http://localhost:8080
DESK_DATA_DIR=./desk-server-data
DESK_SECURE_COOKIES=false
DESK_RETENTION_DAYS=0
DESK_JOB_TIMEOUT_SECONDS=60
```

### 5.2 Production Deployment with HTTPS Ingress
```ini
DESK_HOST=127.0.0.1
DESK_PORT=8080
DESK_PUBLIC_ORIGIN=https://originality.university.edu
DESK_DATA_DIR=/var/lib/originality-desk
DESK_SECURE_COOKIES=true
DESK_RETENTION_DAYS=90
DESK_JOB_TIMEOUT_SECONDS=90
JAVA_OPTS=-Xms256m -Xmx1024m -XX:+UseG1GC -Dfile.encoding=UTF-8
```

### 5.3 Docker Container Environment
```ini
DESK_HOST=0.0.0.0
DESK_PORT=8080
DESK_PUBLIC_ORIGIN=https://originality.university.edu
DESK_DATA_DIR=/app/data
DESK_SECURE_COOKIES=true
DESK_RETENTION_DAYS=90
DESK_JOB_TIMEOUT_SECONDS=60
HOST_PORT=8080
```

