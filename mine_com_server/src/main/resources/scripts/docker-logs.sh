#!/bin/sh
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/docker-common.sh"

MC_ID="${1:-}"
LINES="${2:-100}"
[ -n "$MC_ID" ] || fail "MC_ID is required"

LINES=$(( LINES < 1 ? 1 : LINES > 2000 ? 2000 : LINES ))

require_docker
load_docker_state "$MC_ID"

if ! docker inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
  echo "[MC-COM] Container '$CONTAINER_NAME' not found."
  exit 0
fi

docker logs --tail "$LINES" "$CONTAINER_NAME" 2>&1 \
  || echo "[MC-COM] Could not retrieve logs from container $CONTAINER_NAME"
