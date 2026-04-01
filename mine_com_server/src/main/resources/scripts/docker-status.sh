#!/bin/sh
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/docker-common.sh"

MC_ID="${1:-}"
[ -n "$MC_ID" ] || fail "MC_ID is required"

STATE_FILE="$(docker_state_file "$MC_ID")"
DEPLOY_LOCK="$(docker_deploy_lock "$MC_ID")"

if [ ! -f "$STATE_FILE" ]; then
  if [ -f "$DEPLOY_LOCK" ]; then
    echo "DEPLOYING"
  else
    echo "DEPLOY_NOT_COMPLETED"
  fi
  exit 0
fi

source "$STATE_FILE"

require_docker

if ! docker inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
  echo "NOT_FOUND"
  exit 0
fi

DOCKER_STATUS=$(docker inspect --format='{{.State.Status}}' "$CONTAINER_NAME" 2>/dev/null || echo "unknown")

case "$DOCKER_STATUS" in
  running)  echo "RUNNING" ;;
  exited)   echo "STOPPED" ;;
  created)  echo "STOPPED" ;;
  dead)     echo "ERROR" ;;
  *)        echo "STOPPED" ;;
esac
