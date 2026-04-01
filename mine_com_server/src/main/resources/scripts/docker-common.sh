#!/usr/bin/env bash
set -Eeuo pipefail

log()  { echo "[MC-DOCKER] $*" >&2; }
fail() { log "ERROR: $*"; exit 1; }

to_lower() {
  printf '%s' "${1:-}" | tr '[:upper:]' '[:lower:]'
}

discover_home_dir() {
  if [[ -n "${HOME:-}" ]]; then
    printf '%s\n' "$HOME"
    return 0
  fi

  local user
  user="$(id -un)"

  if command -v getent >/dev/null 2>&1; then
    local home_from_passwd
    home_from_passwd="$(getent passwd "$user" | cut -d: -f6 || true)"
    if [[ -n "$home_from_passwd" ]]; then
      printf '%s\n' "$home_from_passwd"
      return 0
    fi
  fi

  local expanded_home
  expanded_home="$(eval "printf '%s' ~${user}" 2>/dev/null || true)"
  [[ -n "$expanded_home" ]] || fail "Unable to determine home directory for user '$user'"
  printf '%s\n' "$expanded_home"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

require_docker() {
  require_command docker
  docker info >/dev/null 2>&1 || fail "Docker daemon is not running or current user has no access"
}

sanitize_single_line() {
  printf '%s' "${1:-}" | tr '\r\n' ' ' | sed 's/[[:cntrl:]]//g'
}

HOME_DIR="${MC_HOME_DIR:-$(discover_home_dir)}"
MC_ROOT="${MC_ROOT:-$HOME_DIR/mc-com}"
DOCKER_ROOT="${DOCKER_ROOT:-$MC_ROOT/docker}"

DOCKER_SSD_PATH="${DOCKER_SSD_PATH:-$DOCKER_ROOT/servers/ssd}"
DOCKER_HDD_PATH="${DOCKER_HDD_PATH:-$DOCKER_ROOT/servers/hdd}"
DOCKER_RAM_PATH="${DOCKER_RAM_PATH:-/dev/shm/mc-com/servers}"

BACKUP_SSD_PATH="${BACKUP_SSD_PATH:-$DOCKER_ROOT/backups/ssd}"
BACKUP_HDD_PATH="${BACKUP_HDD_PATH:-$DOCKER_ROOT/backups/hdd}"

DOCKER_RUNTIME_DIR="${DOCKER_RUNTIME_DIR:-$DOCKER_ROOT/runtime}"
DOCKER_LOGS_DIR="${DOCKER_LOGS_DIR:-$DOCKER_ROOT/logs}"

JAVA_IMAGE_21="${JAVA_IMAGE_21:-eclipse-temurin:21-jre-alpine}"
JAVA_IMAGE_17="${JAVA_IMAGE_17:-eclipse-temurin:17-jre-alpine}"
JAVA_IMAGE_JDK_21="${JAVA_IMAGE_JDK_21:-eclipse-temurin:21-jdk-alpine}"
JAVA_IMAGE_JDK_17="${JAVA_IMAGE_JDK_17:-eclipse-temurin:17-jdk-alpine}"

docker_container_name() {
  local mc_id="${1:?mc_id is required}"
  printf 'mc-%s\n' "$(printf '%s' "$mc_id" | tr -cd '[:alnum:]')"
}

docker_server_data_dir() {
  local mc_id="${1:?mc_id is required}"
  local storage_type="${2:-ssd}"

  case "$(to_lower "$storage_type")" in
    hdd) printf '%s\n' "$DOCKER_HDD_PATH/$mc_id" ;;
    ram) printf '%s\n' "$DOCKER_RAM_PATH/$mc_id" ;;
    *)   printf '%s\n' "$DOCKER_SSD_PATH/$mc_id" ;;
  esac
}

docker_default_backup_dir() {
  local mc_id="${1:?mc_id is required}"
  local server_storage="${2:-ssd}"

  case "$(to_lower "$server_storage")" in
    hdd) printf '%s\n' "$BACKUP_SSD_PATH/$mc_id" ;;
    ram) printf '%s\n' "$BACKUP_SSD_PATH/$mc_id" ;;
    ssd) printf '%s\n' "$BACKUP_HDD_PATH/$mc_id" ;;
    *)   printf '%s\n' "$BACKUP_SSD_PATH/$mc_id" ;;
  esac
}

docker_runtime_dir() {
  local mc_id="${1:?mc_id is required}"
  printf '%s\n' "$DOCKER_RUNTIME_DIR/$mc_id"
}

docker_logs_dir() {
  local mc_id="${1:?mc_id is required}"
  printf '%s\n' "$DOCKER_LOGS_DIR/$mc_id"
}

docker_state_file() {
  local mc_id="${1:?mc_id is required}"
  printf '%s\n' "$(docker_runtime_dir "$mc_id")/docker-state.env"
}

docker_deploy_lock() {
  local mc_id="${1:?mc_id is required}"
  printf '%s\n' "$(docker_runtime_dir "$mc_id")/deploy.lock"
}

_state_write() {
  local key="${1:?key is required}"
  local value="${2-}"
  printf '%s=%q\n' "$key" "$value"
}

load_docker_state() {
  local mc_id="${1:?mc_id is required}"
  local state_file
  state_file="$(docker_state_file "$mc_id")"

  [[ -f "$state_file" ]] || fail "Docker state file not found: $state_file"

  source "$state_file"

  : "${CONTAINER_NAME:?CONTAINER_NAME missing in docker-state.env}"
  : "${SERVER_DATA_DIR:?SERVER_DATA_DIR missing in docker-state.env}"
}

finalize_docker_deploy() {
  local state_file
  state_file="$(docker_state_file "$MC_ID")"
  mkdir -p "$(dirname "$state_file")"

  {
    _state_write MC_ID              "${MC_ID:-}"
    _state_write MC_VERSION         "${MC_VERSION:-}"
    _state_write MOD_LOADER         "${MOD_LOADER:-}"
    _state_write MOD_LOADER_VERSION "${MOD_LOADER_VERSION:-}"
    _state_write JAVA_MAJOR         "${JAVA_MAJOR:-}"
    _state_write JAVA_IMAGE         "${JAVA_IMAGE:-}"
    _state_write GAME_PORT          "${GAME_PORT:-}"
    _state_write RAM_MB             "${RAM_MB:-}"
    _state_write CPU_CORES          "${CPU_CORES:-}"
    _state_write RCON_ENABLED       "${RCON_ENABLED:-}"
    _state_write RCON_PORT          "${RCON_PORT:-}"
    _state_write RCON_PASSWORD      "${RCON_PASSWORD:-}"
    _state_write WHITELIST_ENABLED  "${WHITELIST_ENABLED:-}"
    _state_write STORAGE_TYPE       "${STORAGE_TYPE:-}"
    _state_write SERVER_DATA_DIR    "${SERVER_DATA_DIR:-}"
    _state_write BACKUP_DIR         "${BACKUP_DIR:-}"
    _state_write CONTAINER_NAME     "${CONTAINER_NAME:-}"
    _state_write LOG_MAX_FILES      "${LOG_MAX_FILES:-}"
    _state_write BACKUP_MAX_COUNT   "${BACKUP_MAX_COUNT:-}"
    _state_write MC_NAME            "${MC_NAME:-}"
  } > "$state_file"

  rm -f "$(docker_deploy_lock "$MC_ID")"
}

cleanup_failed_docker_deploy() {
  local exit_code=$?

  if [[ "$exit_code" -ne 0 ]]; then
    log "Docker deploy failed with exit code $exit_code"

    if [[ -n "${CONTAINER_NAME:-}" ]] && docker inspect "${CONTAINER_NAME}" >/dev/null 2>&1; then
      log "Removing failed container: ${CONTAINER_NAME}"
      docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
    fi
  fi

  if [[ -n "${MC_ID:-}" ]]; then
    local lock
    lock="$(docker_deploy_lock "$MC_ID")"
    [[ -f "$lock" ]] && rm -f "$lock" || true
  fi

  exit "$exit_code"
}

required_java_major() {
  local mc_version="${1:?mc_version is required}"
  case "$mc_version" in
    1.20.5*|1.20.6*|1.21*|1.22*) printf '21\n' ;;
    *)                           printf '17\n' ;;
  esac
}

java_runtime_image() {
  local java_major="${1:?java_major required}"
  if [[ "$java_major" == "21" ]]; then
    printf '%s\n' "$JAVA_IMAGE_21"
  else
    printf '%s\n' "$JAVA_IMAGE_17"
  fi
}

java_jdk_image() {
  local java_major="${1:?java_major required}"
  if [[ "$java_major" == "21" ]]; then
    printf '%s\n' "$JAVA_IMAGE_JDK_21"
  else
    printf '%s\n' "$JAVA_IMAGE_JDK_17"
  fi
}

download_with_retry() {
  local url="${1:?url required}"
  local output="${2:?output required}"
  local max_attempts="${3:-3}"
  local attempt=1

  while [[ "$attempt" -le "$max_attempts" ]]; do
    if curl -fsSL --connect-timeout 30 --retry 2 --retry-delay 3 \
             -A "mine-com-server/1.0 (server manager)" \
             -o "$output" "$url"; then
      return 0
    fi
    log "Download attempt $attempt/$max_attempts failed: $url"
    attempt=$((attempt + 1))
    sleep 3
  done
  fail "Failed to download after $max_attempts attempts: $url"
}

get_paper_latest_build() {
  local mc_version="${1:?mc_version required}"
  local url="https://api.papermc.io/v2/projects/paper/versions/${mc_version}/builds"

  curl -fsSL --connect-timeout 30 "$url" 2>/dev/null \
    | python3 -c '
import sys, json
try:
    data = json.load(sys.stdin)
except Exception as e:
    print(f"[MC-DOCKER] Paper API parse error: {e}", file=sys.stderr)
    raise SystemExit(1)

builds = [b for b in data.get("builds", []) if b.get("channel") == "default"]
if not builds:
    raise SystemExit(1)

print(max(b["build"] for b in builds))
' || fail "Failed to fetch/parse Paper builds for MC ${mc_version}"
}

get_fabric_installer_version() {
  local url="https://meta.fabricmc.net/v2/versions/installer"

  curl -fsSL --connect-timeout 30 "$url" 2>/dev/null \
    | python3 -c '
import sys, json
try:
    data = json.load(sys.stdin)
except Exception as e:
    print(f"[MC-DOCKER] Fabric installer API parse error: {e}", file=sys.stderr)
    raise SystemExit(1)

stable = [x for x in data if x.get("stable", False)]
items = stable if stable else data
if not items:
    raise SystemExit(1)

print(items[0]["version"])
' || fail "Failed to fetch/parse Fabric installer versions"
}

get_fabric_loader_version() {
  local mc_version="${1:?mc_version required}"
  local hint="${2:-latest}"

  if [[ -n "$hint" && "$hint" != "latest" ]]; then
    printf '%s\n' "$hint"
    return 0
  fi

  local url="https://meta.fabricmc.net/v2/versions/loader/${mc_version}"

  curl -fsSL --connect-timeout 30 "$url" 2>/dev/null \
    | python3 -c '
import sys, json
try:
    data = json.load(sys.stdin)
except Exception as e:
    print(f"[MC-DOCKER] Fabric loader API parse error: {e}", file=sys.stderr)
    raise SystemExit(1)

stable = [x for x in data if x.get("loader", {}).get("stable", False)]
items = stable if stable else data
if not items:
    raise SystemExit(1)

print(items[0]["loader"]["version"])
' || fail "Failed to fetch/parse Fabric loader versions for MC ${mc_version}"
}

write_docker_server_properties() {
  local motd_safe
  motd_safe="$(sanitize_single_line "${MC_NAME:-$MC_ID}")"

  cat > "$SERVER_DATA_DIR/server.properties" <<EOF2
server-port=${GAME_PORT}
enable-rcon=${RCON_ENABLED}
rcon.port=${RCON_PORT}
rcon.password=${RCON_PASSWORD}
white-list=${WHITELIST_ENABLED}
motd=${motd_safe}
enable-command-block=true
spawn-protection=0
online-mode=false
difficulty=easy
max-players=20
view-distance=10
simulation-distance=10
EOF2
}

write_docker_eula() {
  echo "eula=true" > "$SERVER_DATA_DIR/eula.txt"
}
