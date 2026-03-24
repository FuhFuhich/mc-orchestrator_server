#!/bin/bash
set -e

SERVER_ID=$1
MC_VERSION=$2
MOD_LOADER=$3
LOADER_VERSION=$4

SERVER_DIR="/home/sha/mc-com/servers/${SERVER_ID}"

echo "[MC-COM] Создание директории: ${SERVER_DIR}"
mkdir -p "${SERVER_DIR}"
cd "${SERVER_DIR}"

case "${MOD_LOADER}" in
    vanilla)
        echo "[MC-COM] Скачивание Vanilla ${MC_VERSION}..."
        MANIFEST=$(curl -s https://launchermeta.mojang.com/mc/game/version_manifest.json)
        VERSION_URL=$(echo "${MANIFEST}" | python3 -c \
            "import sys,json; m=json.load(sys.stdin); \
             print(next(v['url'] for v in m['versions'] if v['id']=='${MC_VERSION}'))")
        SERVER_URL=$(curl -s "${VERSION_URL}" | python3 -c \
            "import sys,json; d=json.load(sys.stdin); print(d['downloads']['server']['url'])")
        curl -fsSL "${SERVER_URL}" -o server.jar
        ;;
    paper)
        echo "[MC-COM] Скачивание Paper ${MC_VERSION}..."
        BUILD=$(curl -s \
            "https://api.papermc.io/v2/projects/paper/versions/${MC_VERSION}/builds" \
            | python3 -c \
            "import sys,json; b=json.load(sys.stdin)['builds']; print(b[-1]['build'])")
        curl -fsSL \
            "https://api.papermc.io/v2/projects/paper/versions/${MC_VERSION}/builds/${BUILD}/downloads/paper-${MC_VERSION}-${BUILD}.jar" \
            -o server.jar
        ;;
    fabric)
        echo "[MC-COM] Скачивание Fabric ${MC_VERSION}..."
        INSTALLER_URL="https://maven.fabricmc.net/net/fabricmc/fabric-installer/latest/fabric-installer-latest.jar"
        curl -fsSL "${INSTALLER_URL}" -o fabric-installer.jar
        java -jar fabric-installer.jar server \
            -mcversion "${MC_VERSION}" \
            -loader "${LOADER_VERSION:-latest}" \
            -downloadMinecraft
        mv fabric-server-launch.jar server.jar
        rm fabric-installer.jar
        ;;
    forge)
        echo "[MC-COM] Скачивание Forge ${MC_VERSION}-${LOADER_VERSION}..."
        FORGE_VER="${MC_VERSION}-${LOADER_VERSION}"
        curl -fsSL \
            "https://maven.minecraftforge.net/net/minecraftforge/forge/${FORGE_VER}/forge-${FORGE_VER}-installer.jar" \
            -o forge-installer.jar
        java -jar forge-installer.jar --installServer
        rm forge-installer.jar
        mv forge-*-universal.jar server.jar 2>/dev/null || true
        ;;
    *)
        echo "[MC-COM] Неизвестный лоадер: ${MOD_LOADER}"
        exit 1
        ;;
esac

echo "eula=true" > eula.txt

cat > server.properties <<EOF
server-port=25565
max-players=20
online-mode=true
motd=Powered by MC-COM
view-distance=10
spawn-protection=16
EOF

echo "[MC-COM] Сервер ${SERVER_ID} готов к запуску"
