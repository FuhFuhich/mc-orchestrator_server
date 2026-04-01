#!/bin/sh
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/docker-common.sh"

MC_ID="${1:-}"
ARCHIVE_PATH="${2:-}"

[ -n "$MC_ID" ]       || fail "MC_ID is required"
[ -n "$ARCHIVE_PATH" ] || fail "ARCHIVE_PATH is required"
[ -f "$ARCHIVE_PATH" ] || fail "Archive not found: $ARCHIVE_PATH"

require_docker
load_docker_state "$MC_ID"

log "Restoring MC_ID=$MC_ID from archive: $ARCHIVE_PATH"

if docker inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
  STATUS=$(docker inspect --format='{{.State.Status}}' "$CONTAINER_NAME" 2>/dev/null || echo "unknown")
  if [ "$STATUS" = "running" ]; then
    log "Stopping container: $CONTAINER_NAME"
    docker stop --time 30 "$CONTAINER_NAME" || true
  fi
fi

log "Clearing server data: $SERVER_DATA_DIR"
find "$SERVER_DATA_DIR" -mindepth 1 -maxdepth 1 \
    ! -name 'logs' \
    ! -name 'runtime' \
    ! -name 'backups' \
    -exec rm -rf {} + 2>/dev/null || true

log "Extracting archive..."
PARENT_DIR="$(dirname "$SERVER_DATA_DIR")"
BASENAME="$(basename "$SERVER_DATA_DIR")"

tar -xzf "$ARCHIVE_PATH" -C "$PARENT_DIR"

ARCHIVE_ROOT="$(tar -tzf "$ARCHIVE_PATH" | head -1 | cut -d/ -f1)"
if [ -n "$ARCHIVE_ROOT" ] && [ "$ARCHIVE_ROOT" != "$BASENAME" ] \
    && [ -d "$PARENT_DIR/$ARCHIVE_ROOT" ]; then
  log "Renaming extracted dir: $ARCHIVE_ROOT → $BASENAME"
  rm -rf "$SERVER_DATA_DIR"
  mv "$PARENT_DIR/$ARCHIVE_ROOT" "$SERVER_DATA_DIR"
fi

log "Restore extraction complete."

if docker inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
  log "Starting container: $CONTAINER_NAME"
  docker start "$CONTAINER_NAME"
  log "Container started."
else
  log "WARNING: Container '$CONTAINER_NAME' not found — run deploy to recreate it."
fi

log "Restore complete for MC_ID=$MC_ID"
