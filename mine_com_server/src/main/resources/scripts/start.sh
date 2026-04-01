#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

MC_ID="${1:-}"
[ -n "$MC_ID" ] || fail "Usage: start.sh <mc_id>"

require_command screen
load_state "$MC_ID"

mkdir -p "$LOGS_DIR" "$RUNTIME_DIR"
PID_FILE="$(pid_file "$MC_ID")"
remove_stale_pid_file "$PID_FILE"

[ -x "$START_SCRIPT" ] || fail "Start script not found or not executable: $START_SCRIPT"

if screen_session_exists "$SESSION_NAME"; then
  log "Server is already running in screen session $SESSION_NAME"
  exit 0
fi

if is_running_pid "$PID_FILE"; then
  log "Server process is already running"
  exit 0
fi

cd "$SERVER_DIR"

screen -dmS "$SESSION_NAME" bash -lc '
  set -Eeuo pipefail
  "'"$START_SCRIPT"'" >> "'"$LOGS_DIR"'/latest.log" 2>&1 &
  echo $! > "'"$PID_FILE"'"
  wait
'

sleep 3

if screen_session_exists "$SESSION_NAME" || is_running_pid "$PID_FILE"; then
  log "Server started"
  exit 0
fi

fail "Server process did not start"
