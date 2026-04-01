#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

MC_ID="${1:-}"
[ -n "$MC_ID" ] || fail "Usage: backup.sh <mc_id>"
load_state "$MC_ID"

mkdir -p "$(backups_dir "$MC_ID")"
BACKUP_PATH="$(backups_dir "$MC_ID")/backup-$(date +%Y%m%d-%H%M%S).tar.gz"

if screen_session_exists "$SESSION_NAME"; then
  screen -S "$SESSION_NAME" -p 0 -X stuff $'save-all\n'
  sleep 3
fi

cd "$SERVER_DIR"
tar -czf "$BACKUP_PATH" \
  world world_nether world_the_end \
  server.properties eula.txt ops.json whitelist.json usercache.json banned-players.json banned-ips.json \
  mods plugins config 2>/dev/null || true

[ -f "$BACKUP_PATH" ] || fail "Backup archive was not created"
printf '%s\n' "$BACKUP_PATH"
