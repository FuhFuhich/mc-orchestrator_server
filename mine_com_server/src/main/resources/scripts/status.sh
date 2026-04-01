#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

MC_ID="${1:-}"
[ -n "$MC_ID" ] || fail "Usage: status.sh <mc_id>"

if [ ! -f "$(state_env_file "$MC_ID")" ]; then
  echo "DEPLOY_NOT_COMPLETED"
  exit 0
fi

load_state "$MC_ID"
if screen_session_exists "$SESSION_NAME" || is_running_pid "$(pid_file "$MC_ID")"; then
  echo "RUNNING"
else
  echo "STOPPED"
fi
