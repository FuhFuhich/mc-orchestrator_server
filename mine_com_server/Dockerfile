### ──────────────────────────────────────────────────────────────────────────
### Stage 1 — build the Spring Boot fat-jar
### ──────────────────────────────────────────────────────────────────────────
FROM gradle:8.10-jdk21 AS build

WORKDIR /workspace

# Download deps first so they can be cached between builds.
COPY settings.gradle build.gradle ./
RUN gradle --no-daemon dependencies > /dev/null 2>&1 || true

# Copy only what's required to build the JAR.
COPY src ./src

# Build the bootJar (tests skipped for faster container builds).
RUN gradle --no-daemon clean bootJar -x test

### ──────────────────────────────────────────────────────────────────────────
### Stage 2 — minimal runtime image
### ──────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy AS runtime

LABEL org.opencontainers.image.title="mine-com-server"
LABEL org.opencontainers.image.description="Minecraft server management backend"

# tini → proper signal handling (PID 1)
# wget → actuator healthcheck
# ca-certificates → HTTPS access for bundle/modloader downloads
# curl → misc diagnostics
RUN apt-get update \
    && apt-get install -y --no-install-recommends tini wget curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Dedicated non-root user.
RUN groupadd --system --gid 10001 mcapp \
    && useradd --system --uid 10001 --gid mcapp --create-home --home-dir /app mcapp

WORKDIR /app

# The Gradle build produces build/libs/app.jar (archiveBaseName = 'app').
COPY --from=build /workspace/build/libs/app.jar /app/app.jar
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

RUN chmod 755 /usr/local/bin/docker-entrypoint.sh \
    && mkdir -p /app/uploads/avatars \
    && chown -R mcapp:mcapp /app

USER mcapp

EXPOSE 8080

# Keep uploads persistent across restarts.
VOLUME ["/app/uploads"]

# Default JVM flags — let operators override via JAVA_OPTS.
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["/usr/bin/tini", "--", "/usr/local/bin/docker-entrypoint.sh"]
