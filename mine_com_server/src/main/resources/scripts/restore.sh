#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

MC_ID="${1:-}"
ARCHIVE_PATH="${2:-}"
[ -n "$MC_ID" ] || fail "Usage: restore.sh <mc_id> <backup_path>"
[ -n "$ARCHIVE_PATH" ] || fail "Usage: restore.sh <mc_id> <backup_path>"
[ -f "$ARCHIVE_PATH" ] || fail "Backup file not found: $ARCHIVE_PATH"
load_state "$MC_ID"

"$SCRIPT_DIR/stop.sh" "$MC_ID" || true
cd "$SERVER_DIR"
tar -xzf "$ARCHIVE_PATH"
log "Backup restored from $ARCHIVE_PATH"
