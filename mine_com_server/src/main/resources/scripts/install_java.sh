#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

JAVA_MAJOR="${1:-}"
[ -n "$JAVA_MAJOR" ] || fail "Usage: install_java.sh <17|21>"
require_command curl
require_command tar

mkdir -p "$JAVA_ROOT"
TMP_DIR="$JAVA_ROOT/tmp-$JAVA_MAJOR-$$"
TARGET_DIR="$JAVA_ROOT/temurin-$JAVA_MAJOR"
mkdir -p "$TMP_DIR"
trap 'rm -rf "$TMP_DIR"' EXIT

ARCHIVE="$TMP_DIR/java.tar.gz"
URL="https://api.adoptium.net/v3/binary/latest/${JAVA_MAJOR}/ga/linux/x64/jre/hotspot/normal/eclipse"

log "Downloading Temurin JRE $JAVA_MAJOR"
curl -fsSL -H "User-Agent: $APP_USER_AGENT" -o "$ARCHIVE" "$URL"

rm -rf "$TARGET_DIR.new"
mkdir -p "$TARGET_DIR.new"
tar -xzf "$ARCHIVE" -C "$TARGET_DIR.new" --strip-components=1
[ -x "$TARGET_DIR.new/bin/java" ] || fail "Temurin archive does not contain bin/java"

rm -rf "$TARGET_DIR"
mv "$TARGET_DIR.new" "$TARGET_DIR"
ln -sfn "$TARGET_DIR" "$JAVA_ROOT/current-$JAVA_MAJOR"

log "Java $JAVA_MAJOR installed at $TARGET_DIR"