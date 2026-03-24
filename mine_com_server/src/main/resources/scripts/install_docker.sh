#!/bin/bash
set -e

echo "[MC-COM] Проверка Docker..."

if command -v docker &>/dev/null; then
    echo "[MC-COM] Docker уже установлен: $(docker --version)"
    exit 0
fi

echo "[MC-COM] Установка Docker..."
apt-get update -qq
apt-get install -y -qq ca-certificates curl gnupg lsb-release

curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
    | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] \
  https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
  > /etc/apt/sources.list.d/docker.list

apt-get update -qq
apt-get install -y -qq docker-ce docker-ce-cli containerd.io

systemctl enable docker
systemctl start docker

echo "[MC-COM] Docker установлен: $(docker --version)"