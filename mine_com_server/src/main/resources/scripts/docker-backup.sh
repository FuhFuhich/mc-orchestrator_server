#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/docker-common.sh"

MC_ID="${1:-}"
[[ -n "$MC_ID" ]] || fail "MC_ID is required"

LIVE_BACKUP="${LIVE_BACKUP:-false}"
LIVE_BACKUP="$(to_lower "$LIVE_BACKUP")"

require_docker
load_docker_state "$MC_ID"

TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
ARCHIVE_NAME="backup-${MC_ID}-${TIMESTAMP}.tar.gz"
ARCHIVE_PATH="${BACKUP_DIR}/${ARCHIVE_NAME}"

mkdir -p "$BACKUP_DIR"

CONTAINER_WAS_RUNNING=false
if [[ "$LIVE_BACKUP" != "true" ]]; then
  STATUS="$(docker inspect --format='{{.State.Status}}' "$CONTAINER_NAME" 2>/dev/null || echo "unknown")"
  if [[ "$STATUS" == "running" ]]; then
    CONTAINER_WAS_RUNNING=true
    log "Stopping container for backup: $CONTAINER_NAME"
    docker stop --time 20 "$CONTAINER_NAME" >/dev/null || true
  fi
fi

log "Creating backup: $ARCHIVE_PATH"
tar -czf "$ARCHIVE_PATH" \
  --exclude='*.lock' \
  -C "$(dirname "$SERVER_DATA_DIR")" \
  "$(basename "$SERVER_DATA_DIR")"

log "Backup created: $ARCHIVE_PATH ($(du -sh "$ARCHIVE_PATH" | cut -f1))"

if [[ "$CONTAINER_WAS_RUNNING" == true ]]; then
  log "Restarting container: $CONTAINER_NAME"
  docker start "$CONTAINER_NAME" >/dev/null || log "WARNING: Failed to restart container after backup"
fi

if [[ -n "${BACKUP_MAX_COUNT:-}" && "$BACKUP_MAX_COUNT" =~ ^[0-9]+$ && "$BACKUP_MAX_COUNT" -gt 0 ]]; then
  log "Enforcing backup count limit: max=$BACKUP_MAX_COUNT"
  mapfile -t BACKUPS < <(find "$BACKUP_DIR" -maxdepth 1 -name "backup-${MC_ID}-*.tar.gz" \
      -printf '%T@ %p\n' | sort -n | awk '{print $2}')

  EXCESS=$(( ${#BACKUPS[@]} - BACKUP_MAX_COUNT ))
  if [[ "$EXCESS" -gt 0 ]]; then
    for (( i = 0; i < EXCESS; i++ )); do
      log "Removing old backup: ${BACKUPS[$i]}"
      rm -f "${BACKUPS[$i]}"
    done
  fi
fi

printf '%s\n' "$ARCHIVE_PATH"
