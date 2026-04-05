#!/usr/bin/env bash
set -Eeuo pipefail
umask 022

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/docker-common.sh"

MC_ID="${1:-}"
MC_VERSION="${2:-}"
MOD_LOADER="${3:-paper}"
MOD_LOADER_VERSION="${4:-}"
GAME_PORT="${5:-25565}"
RAM_MB="${6:-2048}"
CPU_CORES="${7:-2}"
RCON_ENABLED="${8:-false}"
RCON_PORT="${9:-25575}"
RCON_PASSWORD="${10:-}"
WHITELIST_ENABLED="${11:-false}"
STORAGE_TYPE="${12:-ssd}"
BACKUP_PATH_OVERRIDE="${13:-}"
LOG_MAX_FILES="${14:-10}"
BACKUP_MAX_COUNT="${15:-10}"
MC_NAME="${16:-$MC_ID}"

[[ -n "$MC_ID" ]] || fail "MC_ID is required"
[[ -n "$MC_VERSION" ]] || fail "MC_VERSION is required"

[[ "$GAME_PORT" =~ ^[0-9]+$ ]] || fail "GAME_PORT must be numeric"
[[ "$RAM_MB" =~ ^[0-9]+$ ]] || fail "RAM_MB must be numeric"
[[ "$CPU_CORES" =~ ^[0-9]+([.][0-9]+)?$ ]] || fail "CPU_CORES must be numeric"
[[ "$RCON_PORT" =~ ^[0-9]+$ ]] || fail "RCON_PORT must be numeric"
[[ "$LOG_MAX_FILES" =~ ^[0-9]+$ ]] || fail "LOG_MAX_FILES must be numeric"
[[ "$BACKUP_MAX_COUNT" =~ ^[0-9]+$ ]] || fail "BACKUP_MAX_COUNT must be numeric"

RCON_ENABLED="$(to_lower "$RCON_ENABLED")"
WHITELIST_ENABLED="$(to_lower "$WHITELIST_ENABLED")"
STORAGE_TYPE="$(to_lower "$STORAGE_TYPE")"
MOD_LOADER="$(to_lower "$MOD_LOADER")"

case "$STORAGE_TYPE" in
  ssd|hdd|ram) ;;
  *) fail "Unsupported STORAGE_TYPE: $STORAGE_TYPE. Supported: ssd, hdd, ram" ;;
esac

if [[ "$RCON_ENABLED" == "true" && -z "$RCON_PASSWORD" ]]; then
  fail "RCON_PASSWORD is required when RCON_ENABLED=true"
fi

require_command curl
require_command python3
require_docker

HOST_UID="$(id -u)"
HOST_GID="$(id -g)"

SERVER_DATA_DIR="$(docker_server_data_dir "$MC_ID" "$STORAGE_TYPE")"
RUNTIME_DIR="$(docker_runtime_dir "$MC_ID")"
LOGS_DIR_MC="$(docker_logs_dir "$MC_ID")"
CONTAINER_NAME="$(docker_container_name "$MC_ID")"

if [[ -n "$BACKUP_PATH_OVERRIDE" ]]; then
  BACKUP_DIR="${BACKUP_PATH_OVERRIDE}/${MC_ID}"
else
  BACKUP_DIR="$(docker_default_backup_dir "$MC_ID" "$STORAGE_TYPE")"
fi

if [[ "$STORAGE_TYPE" == "ram" ]]; then
  mkdir -p "$(dirname "$SERVER_DATA_DIR")"
fi

mkdir -p "$SERVER_DATA_DIR" "$RUNTIME_DIR" "$LOGS_DIR_MC" "$BACKUP_DIR"

DEPLOY_LOCK="$(docker_deploy_lock "$MC_ID")"
LOG_FILE="$LOGS_DIR_MC/deploy.log"
: > "$LOG_FILE"
exec >> "$LOG_FILE" 2>&1

echo "deploying $(date -Iseconds)" > "$DEPLOY_LOCK"
trap cleanup_failed_docker_deploy EXIT

log "=== Docker deploy start ==="
log "id=$MC_ID version=$MC_VERSION loader=$MOD_LOADER loaderVersion=$MOD_LOADER_VERSION"
log "port=$GAME_PORT ram=${RAM_MB}m cpu=$CPU_CORES storage=$STORAGE_TYPE"
log "SERVER_DATA_DIR=$SERVER_DATA_DIR"
log "BACKUP_DIR=$BACKUP_DIR"
log "HOST_UID=$HOST_UID HOST_GID=$HOST_GID"

JAVA_MAJOR="$(required_java_major "$MC_VERSION")"
JAVA_IMAGE="$(java_runtime_image "$JAVA_MAJOR")"
JAVA_JDK_IMAGE="$(java_jdk_image "$JAVA_MAJOR")"
log "Java major=$JAVA_MAJOR runtime image=$JAVA_IMAGE jdk image=$JAVA_JDK_IMAGE"

log "Pulling Docker images..."
docker pull "$JAVA_IMAGE"     >/dev/null
docker pull "$JAVA_JDK_IMAGE" >/dev/null

fix_path_ownership() {
  local target_dir="${1:?target_dir is required}"
  [[ -d "$target_dir" ]] || return 0

  log "Fixing ownership for $target_dir -> ${HOST_UID}:${HOST_GID}"
  docker run --rm \
    -u 0:0 \
    -v "${target_dir}:/data" \
    --entrypoint sh \
    "$JAVA_IMAGE" \
    -c "chown -R ${HOST_UID}:${HOST_GID} /data"
}

run_installer_container() {
  local mem_limit="${1:?mem_limit is required}"
  shift

  docker run --rm \
    --memory "$mem_limit" \
    --user "${HOST_UID}:${HOST_GID}" \
    -e HOME=/tmp \
    -v "$SERVER_DATA_DIR:/data" \
    -w /data \
    "$JAVA_JDK_IMAGE" \
    "$@"
}

purge_stale_install_artifacts() {
  log "Purging stale install artifacts from $SERVER_DATA_DIR"

  rm -rf "$SERVER_DATA_DIR/.docker-install"
  rm -rf "$SERVER_DATA_DIR/libraries"
  rm -rf "$SERVER_DATA_DIR/versions"
  rm -rf "$SERVER_DATA_DIR/cache"

  rm -f "$SERVER_DATA_DIR/start-server.sh"
  rm -f "$SERVER_DATA_DIR/run.sh"
  rm -f "$SERVER_DATA_DIR/run.bat"
  rm -f "$SERVER_DATA_DIR/user_jvm_args.txt"
  rm -f "$SERVER_DATA_DIR/unix_args.txt"
  rm -f "$SERVER_DATA_DIR/paper.jar"
  rm -f "$SERVER_DATA_DIR/fabric-server-launch.jar"
  rm -f "$SERVER_DATA_DIR/forge-installer.jar.log"
  rm -f "$SERVER_DATA_DIR/neoforge-installer.jar.log"

  find "$SERVER_DATA_DIR" -maxdepth 1 -type f \( \
    -name 'paper-*.jar' -o \
    -name '*fabric*launch*.jar' -o \
    -name 'forge-*.jar' -o \
    -name 'neoforge-*.jar' \
  \) -delete 2>/dev/null || true

  mkdir -p "$SERVER_DATA_DIR/.docker-install"
}

finalize_server_dir_permissions() {
  chmod 755 "$SERVER_DATA_DIR" 2>/dev/null || true
  chmod 755 "$SERVER_DATA_DIR/start-server.sh" 2>/dev/null || true
  chmod 755 "$SERVER_DATA_DIR/run.sh" 2>/dev/null || true
  chmod 644 "$SERVER_DATA_DIR/user_jvm_args.txt" 2>/dev/null || true
  fix_path_ownership "$SERVER_DATA_DIR"
}

assert_modloader_mc_version_artifacts() {
  local expected="${1:?expected mc version is required}"
  local base="$SERVER_DATA_DIR/libraries/net/minecraft/server"

  if [[ -d "$base" ]]; then
    if ! find "$base" -mindepth 1 -maxdepth 1 -type d -name "${expected}-*" -print -quit | grep -q .; then
      fail "Installed server artifacts do not match expected Minecraft version ${expected}"
    fi
  fi
}

setup_paper() {
  log "Setting up Paper ${MC_VERSION}..."

  local build jar_name url
  if [[ -n "$MOD_LOADER_VERSION" && "$MOD_LOADER_VERSION" =~ ^[0-9]+$ ]]; then
    build="$MOD_LOADER_VERSION"
  else
    build="$(get_paper_latest_build "$MC_VERSION")" \
      || fail "Could not determine stable Paper build for MC ${MC_VERSION}"
  fi

  [[ "$build" =~ ^[0-9]+$ ]] || fail "Invalid Paper build returned for MC ${MC_VERSION}: $build"

  MOD_LOADER_VERSION="$build"
  jar_name="paper-${MC_VERSION}-${build}.jar"
  url="https://api.papermc.io/v2/projects/paper/versions/${MC_VERSION}/builds/${build}/downloads/${jar_name}"

  log "Downloading Paper: $url"
  download_with_retry "$url" "$SERVER_DATA_DIR/$jar_name"
  mv "$SERVER_DATA_DIR/$jar_name" "$SERVER_DATA_DIR/paper.jar"
  chmod 644 "$SERVER_DATA_DIR/paper.jar"

  cat > "$SERVER_DATA_DIR/start-server.sh" <<'INNER'
#!/usr/bin/env sh
set -eu
cd /data
exec java -Xms${RAM_MB}m -Xmx${RAM_MB}m \
  -XX:+UseG1GC \
  -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=200 \
  -jar /data/paper.jar nogui
INNER

  log "Paper setup complete."
}

setup_fabric() {
  log "Setting up Fabric ${MC_VERSION}..."

  local installer_ver loader_ver installer_url launch_jar
  installer_ver="$(get_fabric_installer_version)"
  loader_ver="$(get_fabric_loader_version "$MC_VERSION" "$MOD_LOADER_VERSION")"

  log "Fabric: installer=$installer_ver loader=$loader_ver"

  installer_url="https://maven.fabricmc.net/net/fabricmc/fabric-installer/${installer_ver}/fabric-installer-${installer_ver}.jar"
  download_with_retry "$installer_url" "$SERVER_DATA_DIR/.docker-install/fabric-installer.jar"

  log "Running Fabric installer in Docker..."
  run_installer_container 1g \
    java -jar /data/.docker-install/fabric-installer.jar \
      server \
      -mcversion "$MC_VERSION" \
      -loader "$loader_ver" \
      -downloadMinecraft \
      -dir /data

  rm -rf "$SERVER_DATA_DIR/.docker-install"
  fix_path_ownership "$SERVER_DATA_DIR"

  MOD_LOADER_VERSION="$loader_ver"

  launch_jar="fabric-server-launch.jar"
  if [[ ! -f "$SERVER_DATA_DIR/$launch_jar" ]]; then
    launch_jar="$(find "$SERVER_DATA_DIR" -maxdepth 1 -type f -name '*fabric*launch*.jar' -print -quit || true)"
    [[ -n "$launch_jar" ]] || fail "Fabric launch jar not found after installation"
    launch_jar="$(basename "$launch_jar")"
  fi

  cat > "$SERVER_DATA_DIR/start-server.sh" <<INNER
#!/usr/bin/env sh
set -eu
cd /data
exec java -Xms\${RAM_MB}m -Xmx\${RAM_MB}m \
  -XX:+UseG1GC \
  -jar /data/${launch_jar} nogui
INNER

  log "Fabric setup complete."
}

setup_forge() {
  local forge_version
  forge_version="$MOD_LOADER_VERSION"

  if [[ -z "$forge_version" || "$forge_version" == "latest" ]]; then
    forge_version="$(get_forge_latest_version "$MC_VERSION")"
  fi

  MOD_LOADER_VERSION="$forge_version"
  log "Setting up Forge ${MC_VERSION}-${forge_version}..."

  local url
  url="https://maven.minecraftforge.net/net/minecraftforge/forge/${MC_VERSION}-${forge_version}/forge-${MC_VERSION}-${forge_version}-installer.jar"
  download_with_retry "$url" "$SERVER_DATA_DIR/.docker-install/forge-installer.jar"

  log "Running Forge installer in Docker..."
  run_installer_container 2g \
    java -jar /data/.docker-install/forge-installer.jar --installServer /data

  rm -rf "$SERVER_DATA_DIR/.docker-install"
  rm -f "$SERVER_DATA_DIR/forge-installer.jar.log" 2>/dev/null || true
  fix_path_ownership "$SERVER_DATA_DIR"

  cat > "$SERVER_DATA_DIR/user_jvm_args.txt" <<JVM
-Xms${RAM_MB}M
-Xmx${RAM_MB}M
-XX:+UseG1GC
-XX:+ParallelRefProcEnabled
-XX:MaxGCPauseMillis=200
JVM

  [[ -f "$SERVER_DATA_DIR/run.sh" ]] || fail "Forge run.sh not generated by installer"
  assert_modloader_mc_version_artifacts "$MC_VERSION"

  cat > "$SERVER_DATA_DIR/start-server.sh" <<'INNER'
#!/usr/bin/env sh
set -eu
cd /data
chmod 755 /data/run.sh
exec /data/run.sh --nogui
INNER

  log "Forge setup complete."
}

setup_neoforge() {
  local neoforge_version
  neoforge_version="$MOD_LOADER_VERSION"

  if [[ -z "$neoforge_version" || "$neoforge_version" == "latest" ]]; then
    neoforge_version="$(get_neoforge_latest_version "$MC_VERSION" "$MOD_LOADER_VERSION")"
  fi

  MOD_LOADER_VERSION="$neoforge_version"
  log "Setting up NeoForge ${MC_VERSION}-${neoforge_version}..."

  local url
  url="https://maven.neoforged.net/releases/net/neoforged/neoforge/${neoforge_version}/neoforge-${neoforge_version}-installer.jar"
  download_with_retry "$url" "$SERVER_DATA_DIR/.docker-install/neoforge-installer.jar"

  log "Running NeoForge installer in Docker..."
  run_installer_container 2g \
    java -jar /data/.docker-install/neoforge-installer.jar --installServer /data

  rm -rf "$SERVER_DATA_DIR/.docker-install"
  fix_path_ownership "$SERVER_DATA_DIR"

  cat > "$SERVER_DATA_DIR/user_jvm_args.txt" <<JVM
-Xms${RAM_MB}M
-Xmx${RAM_MB}M
-XX:+UseG1GC
-XX:+ParallelRefProcEnabled
-XX:MaxGCPauseMillis=200
JVM

  [[ -f "$SERVER_DATA_DIR/run.sh" ]] || fail "NeoForge run.sh not generated by installer"
  assert_modloader_mc_version_artifacts "$MC_VERSION"

  cat > "$SERVER_DATA_DIR/start-server.sh" <<'INNER'
#!/usr/bin/env sh
set -eu
cd /data
chmod 755 /data/run.sh
exec /data/run.sh --nogui
INNER

  log "NeoForge setup complete."
}

purge_stale_install_artifacts

case "$MOD_LOADER" in
  paper)    setup_paper ;;
  fabric)   setup_fabric ;;
  forge)    setup_forge ;;
  neoforge) setup_neoforge ;;
  *)        fail "Unsupported loader: $MOD_LOADER. Supported: paper, fabric, forge, neoforge" ;;
esac

chmod 755 "$SERVER_DATA_DIR/start-server.sh"
finalize_server_dir_permissions

write_docker_eula
write_docker_server_properties
fix_path_ownership "$SERVER_DATA_DIR"
log "EULA and server.properties written."

if docker inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
  log "Removing existing container: $CONTAINER_NAME"
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
fi

log "Creating Docker container: $CONTAINER_NAME"

docker_create_args=(
  create
  --name "$CONTAINER_NAME"
  --restart unless-stopped
  --memory "${RAM_MB}m"
  --memory-swap "${RAM_MB}m"
  --cpus "$CPU_CORES"
  --user "${HOST_UID}:${HOST_GID}"
  -e "RAM_MB=$RAM_MB"
  -v "${SERVER_DATA_DIR}:/data"
  -p "${GAME_PORT}:${GAME_PORT}"
  --log-driver json-file
  --log-opt max-size=10m
  --log-opt "max-file=${LOG_MAX_FILES}"
  --workdir /data
)

if [[ "$RCON_ENABLED" == "true" ]]; then
  docker_create_args+=(-p "${RCON_PORT}:${RCON_PORT}")
fi

docker "${docker_create_args[@]}" "$JAVA_IMAGE" sh /data/start-server.sh >/dev/null
log "Container created: $CONTAINER_NAME"

log "Starting Docker container: $CONTAINER_NAME"
docker start "$CONTAINER_NAME" >/dev/null

sleep 3

STATUS="$(docker inspect --format '{{.State.Status}}' "$CONTAINER_NAME" 2>/dev/null || true)"
if [[ "$STATUS" != "running" ]]; then
  log "Container status after start: ${STATUS:-unknown}"
  docker logs "$CONTAINER_NAME" || true
  fail "Container was created but did not enter running state: $CONTAINER_NAME"
fi

log "Container started successfully: $CONTAINER_NAME"

finalize_docker_deploy
log "=== Docker deploy completed successfully ==="