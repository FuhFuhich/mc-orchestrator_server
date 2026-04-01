#!/bin/sh
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/docker-common.sh"

MC_ID="${1:-}"
[ -n "$MC_ID" ] || fail "MC_ID is required"

require_docker
load_docker_state "$MC_ID"

log "Restarting container: $CONTAINER_NAME"

if ! docker inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
  fail "Container '$CONTAINER_NAME' not found. Run deploy first."
fi

docker restart --time 30 "$CONTAINER_NAME"
log "Container restarted."
