#!/bin/bash

SERVER_ID=$1
MAX_HOURS="${2:-168}"
BACKUP_DIR="/home/sha/mc-com/backups/${SERVER_ID}"

if [ ! -d "${BACKUP_DIR}" ]; then
    echo "[MC-COM] Директория бэкапов не найдена: ${BACKUP_DIR}"
    exit 0
fi

echo "[MC-COM] Удаление бэкапов старше ${MAX_HOURS}ч в ${BACKUP_DIR}..."
find "${BACKUP_DIR}" -name "*.tar.gz" -mmin +$((MAX_HOURS * 60)) -delete
echo "[MC-COM] Очистка завершена"
