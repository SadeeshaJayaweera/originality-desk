# Multi-stage Dockerfile for Originality Desk Server
# Stage 1: Build shaded application JAR
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /build

COPY pom.xml .
# Cache dependencies where possible
RUN mvn dependency:go-offline -B || true

COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal, secure JRE runtime
FROM eclipse-temurin:17-jre-jammy AS runtime

# Install curl for health probes and fontconfig/fonts for PDFBox report rendering
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    fontconfig \
    fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*

# Run as dedicated unprivileged user
RUN groupadd -g 1001 desk && \
    useradd -u 1001 -g desk -m -s /bin/bash desk

WORKDIR /app
RUN mkdir -p /app/data && chown -R desk:desk /app

COPY --from=builder --chown=desk:desk /build/target/writing-assistant-1.0.0-SNAPSHOT.jar /app/app.jar

ENV DESK_HOST=0.0.0.0 \
    DESK_PORT=8080 \
    DESK_PUBLIC_ORIGIN=http://localhost:8080 \
    DESK_DATA_DIR=/app/data \
    DESK_SECURE_COOKIES=false \
    DESK_RETENTION_DAYS=0 \
    DESK_JOB_TIMEOUT_SECONDS=60 \
    JAVA_OPTS="-Xms128m -Xmx512m -XX:+UseG1GC -Dfile.encoding=UTF-8"

VOLUME ["/app/data"]
EXPOSE 8080

USER desk

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -f http://127.0.0.1:8080/health/live || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -cp /app/app.jar com.writingassistant.server.OriginalityDeskServer"]

