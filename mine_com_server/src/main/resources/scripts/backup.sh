#!/bin/bash
set -e

SERVER_ID=$1
BACKUP_DIR="${2:-/home/sha/mc-com/backups}/${SERVER_ID}"
SERVER_DIR="/home/sha/mc-com/servers/${SERVER_ID}"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="${BACKUP_DIR}/${TIMESTAMP}.tar.gz"

mkdir -p "${BACKUP_DIR}"

echo "[MC-COM] Создание бэкапа: ${BACKUP_FILE}"
tar -czf "${BACKUP_FILE}" -C "${SERVER_DIR}" .

echo "[MC-COM] Бэкап создан: ${BACKUP_FILE} ($(du -sh "${BACKUP_FILE}" | cut -f1))"
echo "${BACKUP_FILE}"
