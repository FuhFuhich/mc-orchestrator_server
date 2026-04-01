#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

MC_ID="${1:-}"
MC_VERSION="${2:-}"
MOD_LOADER="${3:-vanilla}"
MOD_LOADER_VERSION="${4:-latest}"
GAME_PORT="${5:-25565}"
RAM_MB="${6:-2048}"
CPU_CORES="${7:-2}"
RCON_ENABLED="${8:-false}"
RCON_PORT="${9:-25575}"
RCON_PASSWORD="${10:-}"
WHITELIST_ENABLED="${11:-false}"

[ -n "$MC_ID" ] || fail "MC_ID is required"
[ -n "$MC_VERSION" ] || fail "MC_VERSION is required"

prepare_dirs "$MC_ID"

SERVER_DIR="$(server_dir "$MC_ID")"
LOGS_DIR="$(logs_dir "$MC_ID")"
RUNTIME_DIR="$(runtime_dir "$MC_ID")"
DEPLOY_LOCK="$(deploy_lock_file "$MC_ID")"
STATE_FILE="$(state_env_file "$MC_ID")"
SESSION_NAME="$(session_name "$MC_ID")"

LOG_FILE="$LOGS_DIR/deploy.log"
mkdir -p "$LOGS_DIR"
: > "$LOG_FILE"
exec >> "$LOG_FILE" 2>&1
trap cleanup_failed_deploy EXIT

echo "deploying $(date -Iseconds)" > "$DEPLOY_LOCK"
log "Deploy start: id=$MC_ID version=$MC_VERSION loader=$MOD_LOADER loaderVersion=$MOD_LOADER_VERSION"

require_command tar
require_command find
require_command grep
require_command screen

JAVA_MAJOR="$(required_java_major "$MC_VERSION")"
JAVA_BIN="$(ensure_java "$JAVA_MAJOR")"
MC_NAME="$MC_ID"

rm -rf "$SERVER_DIR/.install"
mkdir -p "$SERVER_DIR/.install"
mkdir -p "$RUNTIME_DIR"

extract_bundle() {
  local loader="$1"
  local bundle_path
  bundle_path="$(bundle_remote_path "$loader" "$MC_VERSION" "$MOD_LOADER_VERSION")"
  [ -f "$bundle_path" ] || fail "Bundle not found on server: $bundle_path"

  local bundle_root
  bundle_root="$(bundle_root_name "$bundle_path")"
  [ -n "$bundle_root" ] || fail "Cannot determine bundle root from archive: $bundle_path"

  log "Using bundle: $bundle_path"
  log "Detected bundle root: $bundle_root"

  rm -rf "$SERVER_DIR/.bundle-tmp"
  mkdir -p "$SERVER_DIR/.bundle-tmp"

  tar -xzf "$bundle_path" -C "$SERVER_DIR/.bundle-tmp"
  [ -d "$SERVER_DIR/.bundle-tmp/$bundle_root" ] || fail "Extracted bundle root not found: $bundle_root"

  find "$SERVER_DIR" -mindepth 1 -maxdepth 1 \
    ! -name logs \
    ! -name runtime \
    ! -name backups \
    ! -name .install \
    ! -name .bundle-tmp \
    -exec rm -rf {} +

  cp -a "$SERVER_DIR/.bundle-tmp/$bundle_root/." "$SERVER_DIR/"
  rm -rf "$SERVER_DIR/.bundle-tmp"
}

setup_paper() {
  extract_bundle paper
  local paper_jar
  paper_jar="$(find_first_matching_file "$SERVER_DIR" 'paper*.jar' 'server.jar')" || fail "Paper jar not found in bundle"
  local rel
  rel="${paper_jar#$SERVER_DIR/}"
  create_start_script_from_command "\"$JAVA_BIN\" -Xms${RAM_MB}M -Xmx${RAM_MB}M -jar \"$SERVER_DIR/$rel\" nogui"
}

setup_fabric() {
  extract_bundle fabric
  local fabric_run fabric_launch fabric_server
  fabric_run="$(find_first_matching_file "$SERVER_DIR" 'run.sh')" || true
  if [ -n "$fabric_run" ]; then
    chmod 755 "$fabric_run"
    create_start_script_from_command "cd \"$SERVER_DIR\" && ./$(basename "$fabric_run")"
    return 0
  fi

  fabric_launch="$(find_first_matching_file "$SERVER_DIR" 'fabric-server-launch.jar' '*fabric*launch*.jar')" || true
  if [ -n "$fabric_launch" ]; then
    local rel
    rel="${fabric_launch#$SERVER_DIR/}"
    create_start_script_from_command "\"$JAVA_BIN\" -Xms${RAM_MB}M -Xmx${RAM_MB}M -jar \"$SERVER_DIR/$rel\" nogui"
    return 0
  fi

  fabric_server="$(find_first_matching_file "$SERVER_DIR" 'server.jar')" || fail "Fabric launch jar not found in bundle"
  local rel2
  rel2="${fabric_server#$SERVER_DIR/}"
  create_start_script_from_command "\"$JAVA_BIN\" -Xms${RAM_MB}M -Xmx${RAM_MB}M -jar \"$SERVER_DIR/$rel2\" nogui"
}

setup_forge() {
  extract_bundle forge
  [ -f "$SERVER_DIR/run.sh" ] || fail "Forge run.sh not found in bundle"
  chmod 755 "$SERVER_DIR/run.sh"
  create_start_script_from_command "cd \"$SERVER_DIR\" && ./run.sh --nogui"
}

setup_neoforge() {
  extract_bundle neoforge
  [ -f "$SERVER_DIR/run.sh" ] || fail "NeoForge run.sh not found in bundle"
  chmod 755 "$SERVER_DIR/run.sh"
  create_start_script_from_command "cd \"$SERVER_DIR\" && ./run.sh --nogui"
}

case "${MOD_LOADER,,}" in
  forge)
    setup_forge
    ;;
  neoforge)
    setup_neoforge
    ;;
  fabric)
    setup_fabric
    ;;
  paper)
    setup_paper
    ;;
  *)
    fail "Unsupported loader for bundle deploy: $MOD_LOADER"
    ;;
esac

write_eula
write_server_properties
[ -x "$START_SCRIPT" ] || fail "Start script was not created"
finalize_deploy
log "Deploy completed successfully"
