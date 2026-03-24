#!/bin/bash
set -e

echo "[MC-COM] Проверка Java..."

if java -version 2>&1 | grep -q "21"; then
    echo "[MC-COM] Java 21 уже установлена"
    exit 0
fi

echo "[MC-COM] Установка Java 21..."
apt-get update -qq
apt-get install -y -qq wget curl

# Eclipse Temurin 21
wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | apt-key add -
echo "deb https://packages.adoptium.net/artifactory/deb $(lsb_release -cs) main" \
    > /etc/apt/sources.list.d/adoptium.list

apt-get update -qq
apt-get install -y -qq temurin-21-jdk

java -version
echo "[MC-COM] Java 21 установлена успешно"