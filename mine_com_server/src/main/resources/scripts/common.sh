#!/usr/bin/env bash
set -Eeuo pipefail

MC_ROOT="${MC_ROOT:-/opt/mc-com}"
SERVERS_ROOT="${SERVERS_ROOT:-$MC_ROOT/servers}"
DIST_ROOT="${DIST_ROOT:-$MC_ROOT/dist}"
BUNDLES_ROOT="${BUNDLES_ROOT:-$DIST_ROOT/bundles}"
JAVA_ROOT="${JAVA_ROOT:-$MC_ROOT/.java}"
APP_USER_AGENT="${APP_USER_AGENT:-mine-com-server/1.0 ([email protected])}"
DEFAULT_RETENTION_DAYS="${DEFAULT_RETENTION_DAYS:-7}"

mkdir -p "$MC_ROOT" "$SERVERS_ROOT" "$DIST_ROOT" "$BUNDLES_ROOT" "$JAVA_ROOT"

log() {
  echo "[MC-COM] $*"
}

fail() {
  log "ERROR: $*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

server_dir() {
  local mc_id="${1:?mc_id is required}"
  echo "$SERVERS_ROOT/$mc_id"
}

runtime_dir() {
  local mc_id="${1:?mc_id is required}"
  echo "$(server_dir "$mc_id")/runtime"
}

logs_dir() {
  local mc_id="${1:?mc_id is required}"
  echo "$(server_dir "$mc_id")/logs"
}

backups_dir() {
  local mc_id="${1:?mc_id is required}"
  echo "$(server_dir "$mc_id")/backups"
}

deploy_lock_file() {
  local mc_id="${1:?mc_id is required}"
  echo "$(runtime_dir "$mc_id")/deploy.lock"
}

state_env_file() {
  local mc_id="${1:?mc_id is required}"
  echo "$(runtime_dir "$mc_id")/state.env"
}

pid_file() {
  local mc_id="${1:?mc_id is required}"
  echo "$(runtime_dir "$mc_id")/server.pid"
}

session_name() {
  local mc_id="${1:?mc_id is required}"
  echo "mc-${mc_id//[^a-zA-Z0-9]/}"
}

bundle_remote_path() {
  local loader="${1:?loader is required}"
  local mc_version="${2:-}"
  local loader_version="${3:-}"

  case "${loader,,}" in
    forge)
      echo "$BUNDLES_ROOT/forge/forge-${mc_version}-${loader_version}.tar.gz"
      ;;
    neoforge)
      echo "$BUNDLES_ROOT/neoforge/neoforge-${loader_version}.tar.gz"
      ;;
    fabric)
      echo "$BUNDLES_ROOT/fabric/fabric-${mc_version}.tar.gz"
      ;;
    paper)
      echo "$BUNDLES_ROOT/paper/paper-${mc_version}.tar.gz"
      ;;
    *)
      fail "Unsupported loader for bundle path: $loader"
      ;;
  esac
}

prepare_dirs() {
  local mc_id="${1:?mc_id is required}"
  mkdir -p \
    "$(server_dir "$mc_id")" \
    "$(runtime_dir "$mc_id")" \
    "$(logs_dir "$mc_id")" \
    "$(backups_dir "$mc_id")"
}

write_eula() {
  echo "eula=true" > "$SERVER_DIR/eula.txt"
}

write_server_properties() {
  cat > "$SERVER_DIR/server.properties" <<EOF2
server-port=${GAME_PORT}
enable-rcon=${RCON_ENABLED}
rcon.port=${RCON_PORT}
rcon.password=${RCON_PASSWORD}
white-list=${WHITELIST_ENABLED}
motd=${MC_NAME}
enable-command-block=true
spawn-protection=0
online-mode=false
difficulty=easy
max-players=20
view-distance=10
simulation-distance=10
EOF2
}

required_java_major() {
  local mc_version="${1:?mc_version is required}"
  case "$mc_version" in
    1.20.5*|1.20.6*|1.21*|1.22*) echo "21" ;;
    *) echo "17" ;;
  esac
}

ensure_java() {
  local java_major="${1:?java_major is required}"
  local java_bin="$JAVA_ROOT/current-$java_major/bin/java"

  if [ -x "$java_bin" ]; then
    echo "$java_bin"
    return 0
  fi

  "$SCRIPT_DIR/install_java.sh" "$java_major" >/dev/null
  [ -x "$java_bin" ] || fail "Java $java_major was not installed correctly"
  echo "$java_bin"
}

create_start_script_from_command() {
  local command_line="${1:?command_line is required}"
  START_SCRIPT="$SERVER_DIR/start-server.sh"

  cat > "$START_SCRIPT" <<EOF2
#!/usr/bin/env bash
set -Eeuo pipefail
cd "$SERVER_DIR"
exec bash -lc $(printf '%q' "$command_line")
EOF2

  chmod 755 "$START_SCRIPT"
}

cleanup_failed_deploy() {
  local exit_code=$?
  if [ "$exit_code" -ne 0 ]; then
    log "Deploy failed with exit code $exit_code"
  fi
  if [ -n "${DEPLOY_LOCK:-}" ] && [ -f "${DEPLOY_LOCK}" ]; then
    rm -f "${DEPLOY_LOCK}" || true
  fi
  exit "$exit_code"
}

finalize_deploy() {
  cat > "$STATE_FILE" <<EOF2
MC_ID=${MC_ID}
MC_VERSION=${MC_VERSION}
MOD_LOADER=${MOD_LOADER}
MOD_LOADER_VERSION=${MOD_LOADER_VERSION}
JAVA_MAJOR=${JAVA_MAJOR}
JAVA_BIN=${JAVA_BIN}
GAME_PORT=${GAME_PORT}
RAM_MB=${RAM_MB}
CPU_CORES=${CPU_CORES}
RCON_ENABLED=${RCON_ENABLED}
RCON_PORT=${RCON_PORT}
RCON_PASSWORD=${RCON_PASSWORD}
WHITELIST_ENABLED=${WHITELIST_ENABLED}
SERVER_DIR=${SERVER_DIR}
RUNTIME_DIR=${RUNTIME_DIR}
LOGS_DIR=${LOGS_DIR}
START_SCRIPT=${START_SCRIPT}
SESSION_NAME=${SESSION_NAME}
EOF2

  rm -f "$DEPLOY_LOCK"
}

load_state() {
  local mc_id="${1:?mc_id is required}"
  local state_file
  state_file="$(state_env_file "$mc_id")"

  [ -f "$state_file" ] || fail "State file not found: $state_file"
  source "$state_file"

  : "${SERVER_DIR:?SERVER_DIR is missing in state.env}"
  : "${START_SCRIPT:?START_SCRIPT is missing in state.env}"
  : "${SESSION_NAME:?SESSION_NAME is missing in state.env}"

  RUNTIME_DIR="${RUNTIME_DIR:-$(runtime_dir "$mc_id")}"
  LOGS_DIR="${LOGS_DIR:-$(logs_dir "$mc_id")}"
}

screen_session_exists() {
  local name="${1:?screen session name is required}"
  screen -list 2>/dev/null | grep -Fq "$name"
}

is_running_pid() {
  local pidfile="${1:?pid file path is required}"

  [ -f "$pidfile" ] || return 1
  local pid
  pid="$(cat "$pidfile" 2>/dev/null || true)"
  [ -n "$pid" ] || return 1

  kill -0 "$pid" 2>/dev/null
}

remove_stale_pid_file() {
  local pidfile="${1:?pid file path is required}"
  if [ -f "$pidfile" ] && ! is_running_pid "$pidfile"; then
    rm -f "$pidfile"
  fi
}

bundle_root_name() {
  local archive_path="${1:?archive_path is required}"
  local first_line=""

  while IFS= read -r first_line; do
    break
  done < <(tar -tzf "$archive_path" 2>/dev/null)

  [ -n "$first_line" ] || return 1
  printf '%s\n' "${first_line%%/*}"
}

find_first_matching_file() {
  local root="${1:?root is required}"
  shift

  local pattern
  for pattern in "$@"; do
    local found=""
    while IFS= read -r found; do
      break
    done < <(find "$root" -type f -name "$pattern" | sort)

    if [ -n "$found" ]; then
      printf '%s\n' "$found"
      return 0
    fi
  done

  return 1
}
