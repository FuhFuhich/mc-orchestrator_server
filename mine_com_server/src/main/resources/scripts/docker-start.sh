#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/docker-common.sh"

MC_ID="${1:-}"
[[ -n "$MC_ID" ]] || fail "MC_ID is required"

require_docker
load_docker_state "$MC_ID"

log "Starting container: $CONTAINER_NAME"

if ! docker inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
  fail "Container '$CONTAINER_NAME' not found. Run deploy first."
fi

STATUS="$(docker inspect --format '{{.State.Status}}' "$CONTAINER_NAME" 2>/dev/null || echo "unknown")"
log "Container current status: $STATUS"

case "$STATUS" in
  running)
    log "Container is already running."
    ;;
  paused)
    docker unpause "$CONTAINER_NAME" >/dev/null
    ;;
  created|exited)
    docker start "$CONTAINER_NAME" >/dev/null
    ;;
  dead)
    fail "Container is in dead state. Remove and redeploy it."
    ;;
  *)
    docker start "$CONTAINER_NAME" >/dev/null
    ;;
esac

sleep 3

STATUS="$(docker inspect --format '{{.State.Status}}' "$CONTAINER_NAME" 2>/dev/null || echo "unknown")"
if [[ "$STATUS" != "running" ]]; then
  log "Container failed to stay running. Current status: $STATUS"
  docker logs "$CONTAINER_NAME" || true
  fail "Container did not enter running state"
fi

log "Container started successfully."