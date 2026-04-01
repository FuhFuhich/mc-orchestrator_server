#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/docker-common.sh"

MC_ID="${1:-}"
[ -n "$MC_ID" ] || fail "MC_ID is required"

require_docker
load_docker_state "$MC_ID"

log "Stopping container: $CONTAINER_NAME"

if ! docker inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
  log "Container '$CONTAINER_NAME' not found — nothing to stop."
  exit 0
fi

STATUS=$(docker inspect --format='{{.State.Status}}' "$CONTAINER_NAME" 2>/dev/null || echo "unknown")
if [ "$STATUS" = "running" ]; then
  docker stop --time 30 "$CONTAINER_NAME"
  log "Container stopped."
else
  log "Container is not running (status: $STATUS). Nothing to do."
fi
