#!/bin/sh
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/docker-common.sh"

MC_ID="${1:-}"
[ -n "$MC_ID" ] || fail "MC_ID is required"

STATE_FILE="$(docker_state_file "$MC_ID")"

require_docker

if [ -f "$STATE_FILE" ]; then
  source "$STATE_FILE"
  log "Loaded state: container=$CONTAINER_NAME data=$SERVER_DATA_DIR"
else
  log "State file not found — deriving container name from MC_ID."
  CONTAINER_NAME="$(docker_container_name "$MC_ID")"
  SERVER_DATA_DIR=""
fi

if docker inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
  log "Stopping container: $CONTAINER_NAME"
  docker stop --time 20 "$CONTAINER_NAME" 2>/dev/null || true
  log "Removing container: $CONTAINER_NAME"
  docker rm -f "$CONTAINER_NAME" || true
else
  log "Container '$CONTAINER_NAME' not found — skipping remove."
fi

if [ -n "${SERVER_DATA_DIR:-}" ] && [ -d "$SERVER_DATA_DIR" ]; then
  log "Removing server data: $SERVER_DATA_DIR"
  rm -rf "$SERVER_DATA_DIR"
fi

RUNTIME_DIR="$(docker_runtime_dir "$MC_ID")"
if [ -d "$RUNTIME_DIR" ]; then
  log "Removing runtime dir: $RUNTIME_DIR"
  rm -rf "$RUNTIME_DIR"
fi

log "Cleanup complete for MC_ID=$MC_ID"
