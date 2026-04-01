#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

MC_ID="${1:-}"
[ -n "$MC_ID" ] || fail "Usage: cleanup.sh <mc_id>"

if [ -f "$(state_env_file "$MC_ID")" ]; then
  "$SCRIPT_DIR/stop.sh" "$MC_ID" || true
fi

rm -rf "$(server_dir "$MC_ID")"
log "Server data removed for $MC_ID"
