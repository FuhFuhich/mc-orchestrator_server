#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────────────────────────────────────
# Container entrypoint.
# Validates critical secrets, ensures upload dir exists, then execs the JVM.
# ─────────────────────────────────────────────────────────────────────────────

random_alnum() {
  local length="$1"
  tr -dc 'A-Za-z0-9' < /dev/urandom | head -c "$length"
}

# JWT secret — auto-generate if left at the default / empty. This is safe
# because JWTs are only short-lived; a new secret simply invalidates existing
# sessions on restart.
if [[ -z "${APP_JWT_SECRET:-}" \
      || "${APP_JWT_SECRET}" == "change-me-in-env-with-at-least-32-bytes" \
      || "${APP_JWT_SECRET}" == "change_me_in_production_min_32_chars" ]]; then
  export APP_JWT_SECRET="$(random_alnum 64)"
  echo "[entrypoint] APP_JWT_SECRET was empty or default — generated a random 64-char secret for this container start."
fi

# Encryption key — DO NOT auto-generate. If rotated, all previously stored
# SSH/RCON credentials become undecryptable, so refuse to start instead.
if [[ -z "${APP_ENCRYPTION_KEY:-}" \
      || "${APP_ENCRYPTION_KEY}" == "replace-with-a-random-exact-32-char-key" ]]; then
  echo "[entrypoint] ERROR: APP_ENCRYPTION_KEY is not set or still contains the placeholder." >&2
  echo "[entrypoint] Set APP_ENCRYPTION_KEY (exactly 32 characters) in your .env and restart." >&2
  exit 1
fi

if [[ "${#APP_ENCRYPTION_KEY}" -ne 32 ]]; then
  echo "[entrypoint] ERROR: APP_ENCRYPTION_KEY must be exactly 32 characters (got ${#APP_ENCRYPTION_KEY})." >&2
  exit 1
fi

mkdir -p /app/uploads/avatars
umask 027

echo "[entrypoint] Starting JVM…"
echo "[entrypoint]   DB_URL                 = ${DB_URL:-<default>}"
echo "[entrypoint]   APP_REMOTE_ROOT        = ${APP_REMOTE_ROOT:-/opt/mc-com}"
echo "[entrypoint]   APP_CORS_ALLOWED_ORIGINS = ${APP_CORS_ALLOWED_ORIGINS:-<default>}"

exec java ${JAVA_OPTS} -jar /app/app.jar
