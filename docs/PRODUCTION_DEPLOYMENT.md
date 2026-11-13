# Originality Desk — Production Deployment Guide

This guide details best practices for deploying Originality Desk to production academic environments.

---

## 1. Production Architecture

Originality Desk is designed for secure, self-contained single-node or containerized deployments.

```
+-------------------------------------------------------------------+
|                        Internet / Campus WAN                      |
+-------------------------------------------------------------------+
                                  │ HTTPS (Port 443)
                                  ▼
+-------------------------------------------------------------------+
|               Reverse Proxy (TLS Termination, Nginx/Caddy)         |
|  - Terminates TLS with valid certificates                         |
|  - Preserves Host and Origin headers                              |
|  - Enforces client upload size limits (12 MB)                     |
+-------------------------------------------------------------------+
                                  │ HTTP (Loopback / Container Net)
                                  ▼
+-------------------------------------------------------------------+
|                Originality Desk Application Server                |
|  - Embedded JDK HTTP Server (8 request threads, 32 queued)        |
|  - Strict Origin & Host enforcement (Anti-CSRF)                   |
|  - In-process Background Job Worker (1 bounded compute worker)    |
|  - Strict Session Authentication (PBKDF2-HMAC-SHA256, 12h expiry)|
+-------------------------------------------------------------------+
           │                                      │
           ▼ (POSIX 0700)                         ▼ (POSIX 0700)
+------------------------+             +----------------------------+
|  SQLite Database (WAL) |             |  Document Storage (Blobs)  |
|  server.sqlite3        |             |  ./documents/              |
|  Synchronous FULL      |             |  SHA-256 Content-Addressed |
|  Foreign Keys ON       |             |  Secure Zero-Fill Deletion |
+------------------------+             +----------------------------+
```

---

## 2. Security Pre-Deployment Checklist

Before exposing Originality Desk to institutional users, verify each of the following controls:

- [ ] **TLS Termination**: The application listener must never be exposed directly to the public internet without an upstream TLS reverse proxy.
- [ ] **Canonical Origin**: `DESK_PUBLIC_ORIGIN` must match the exact institutional domain accessed by users (e.g., `https://originality.university.edu`).
- [ ] **Secure Cookies**: `DESK_SECURE_COOKIES=true` must be set so the session cookie carries the `; Secure` flag.
- [ ] **Restricted Storage Permissions**: The `DESK_DATA_DIR` directory must be on a local, non-NFS filesystem with POSIX permissions `0700` (`rwx------`).
- [ ] **Unprivileged Runtime User**: Run the Java process or container as a dedicated unprivileged user (`desk`, UID 1001), never as `root`.
- [ ] **JVM Memory Caps**: Configure JVM heap limits appropriate for your host (recommended `-Xms128m -Xmx512m` to `-Xmx1024m`).
- [ ] **File Descriptor Limits**: Ensure the operating system `nofile` limit is at least 4096.

---

## 3. Deployment Option 1: Docker Compose (Recommended)

### Step 1: Prepare Environment File
Create a production `.env` file from `.env.example`:

```sh
DESK_HOST=0.0.0.0
DESK_PORT=8080
DESK_PUBLIC_ORIGIN=https://originality.university.edu
DESK_SECURE_COOKIES=true
DESK_RETENTION_DAYS=90
DESK_JOB_TIMEOUT_SECONDS=60
HOST_PORT=8080
```

### Step 2: Launch Container Stack
```sh
docker compose pull || docker compose build
docker compose up -d
```

### Step 3: Verify Container Health
```sh
docker compose ps
curl -f http://127.0.0.1:8080/health/live
```

---

## 4. Deployment Option 2: Systemd Service (Linux Bare-Metal / VM)

### Step 1: Create Dedicated User and Directories
```sh
sudo useradd -r -s /bin/false -d /opt/originality-desk desk
sudo mkdir -p /opt/originality-desk/bin /var/lib/originality-desk
sudo chown -R desk:desk /opt/originality-desk /var/lib/originality-desk
sudo chmod 700 /var/lib/originality-desk
```

### Step 2: Install Shaded JAR
```sh
sudo cp target/writing-assistant-1.0.0-SNAPSHOT.jar /opt/originality-desk/bin/app.jar
sudo chown desk:desk /opt/originality-desk/bin/app.jar
```

### Step 3: Configure Environment
Create `/etc/originality-desk.env`:
```ini
DESK_HOST=127.0.0.1
DESK_PORT=8080
DESK_PUBLIC_ORIGIN=https://originality.university.edu
DESK_DATA_DIR=/var/lib/originality-desk
DESK_SECURE_COOKIES=true
DESK_RETENTION_DAYS=90
DESK_JOB_TIMEOUT_SECONDS=60
JAVA_OPTS=-Xms256m -Xmx1024m -XX:+UseG1GC -Dfile.encoding=UTF-8
```
```sh
sudo chmod 600 /etc/originality-desk.env
sudo chown desk:desk /etc/originality-desk.env
```

### Step 4: Create Systemd Unit
Create `/etc/systemd/system/originality-desk.service`:

```ini
[Unit]
Description=Originality Desk Academic Integrity Platform
After=network.target

[Service]
Type=simple
User=desk
Group=desk
WorkingDirectory=/var/lib/originality-desk
EnvironmentFile=/etc/originality-desk.env
ExecStart=/usr/bin/java $JAVA_OPTS -cp /opt/originality-desk/bin/app.jar com.writingassistant.server.OriginalityDeskServer
Restart=always
RestartSec=5
TimeoutStopSec=15

# Security hardening
ProtectSystem=strict
ReadWritePaths=/var/lib/originality-desk
ProtectHome=true
NoNewPrivileges=true
PrivateTmp=true
ProtectKernelTunables=true
ProtectControlGroups=true

[Install]
WantedBy=multi-user.target
```

Enable and start the service:
```sh
sudo systemctl daemon-reload
sudo systemctl enable originality-desk
sudo systemctl start originality-desk
sudo systemctl status originality-desk
```

---

## 5. Nginx Reverse Proxy Configuration

Create `/etc/nginx/sites-available/originality-desk.conf`:

```nginx
server {
    listen 80;
    server_name originality.university.edu;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name originality.university.edu;

    ssl_certificate     /etc/letsencrypt/live/originality.university.edu/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/originality.university.edu/privkey.pem;
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_ciphers         HIGH:!aNULL:!MD5;

    # Maximum document upload size (10 MiB application limit + headroom)
    client_max_body_size 12M;

    # Security headers
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Content-Type-Options nosniff always;
    add_header X-Frame-Options DENY always;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;

        # Mandatory: Upstream server enforces Host == configured DESK_PUBLIC_ORIGIN authority
        proxy_set_header Host $http_host;

        # Mandatory: Mutation requests require matching Origin header for CSRF defense
        proxy_set_header Origin $http_origin;

        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # Timeouts for document uploads and PDF export streaming
        proxy_connect_timeout 10s;
        proxy_send_timeout    60s;
        proxy_read_timeout    60s;
    }

    # Public health check endpoint
    location /health/ {
        proxy_pass http://127.0.0.1:8080/health/;
        proxy_set_header Host $http_host;
        access_log off;
    }
}
```

Enable the configuration and reload Nginx:
```sh
sudo ln -s /etc/nginx/sites-available/originality-desk.conf /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

---

## 6. Verification and Health Probing

Once deployed, verify the installation:

```sh
# Verify liveness
curl -I https://originality.university.edu/health/live

# Verify readiness (verifies worker thread and database access)
curl -s https://originality.university.edu/health/ready
```

Expected JSON response from `/health/ready`:
```json
{"status":"UP"}
```

