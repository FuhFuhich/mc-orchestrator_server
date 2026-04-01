#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

MC_ID="${1:-}"
[ -n "$MC_ID" ] || fail "Usage: stop.sh <mc_id>"
load_state "$MC_ID"

if screen_session_exists "$SESSION_NAME"; then
  screen -S "$SESSION_NAME" -p 0 -X stuff $'stop\n' || true
  for _ in $(seq 1 30); do
    sleep 2
    screen_session_exists "$SESSION_NAME" || break
  done
fi

if screen_session_exists "$SESSION_NAME"; then
  screen -S "$SESSION_NAME" -X quit || true
fi

if is_running_pid "$(pid_file "$MC_ID")"; then
  kill "$(cat "$(pid_file "$MC_ID")")" 2>/dev/null || true
  for _ in $(seq 1 10); do
    sleep 1
    is_running_pid "$(pid_file "$MC_ID")" || break
  done
fi

if is_running_pid "$(pid_file "$MC_ID")"; then
  kill -9 "$(cat "$(pid_file "$MC_ID")")" 2>/dev/null || true
fi

rm -f "$(pid_file "$MC_ID")"
log "Server stopped"
