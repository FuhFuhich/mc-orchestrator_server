#!/bin/bash
set -e

if command -v screen &>/dev/null; then
    echo "[MC-COM] screen уже установлен"
    exit 0
fi

apt-get update -qq
apt-get install -y -qq screen
echo "[MC-COM] screen установлен"