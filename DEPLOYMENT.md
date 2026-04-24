# mc-orchestrator — развёртывание

Коротко: **вся связка (Postgres + Spring Boot + web + nginx + TLS)** поднимается в Docker одним `docker compose up -d`.

```
mc-orchestrator/
├── mine_com_server/        ← Spring Boot backend (Dockerfile здесь)
├── mine_com_web/           ← веб-приложение (статика)
├── nginx/                  ← конфиги nginx + letsencrypt/
├── docker-compose.yml      ← общий файл
└── .env                    ← секреты (создаёшь сам из .env.example)
```

---

## 0. Что нужно на сервере Ubuntu

```bash
sudo apt update
sudo apt install -y ca-certificates curl git
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
newgrp docker     # или перелогинься
```

В **DNS** сделай `A`-записи `mc-orchestrator.xyz` и `www.mc-orchestrator.xyz` на IP твоего сервера, иначе Let's Encrypt не выдаст сертификат.

---

## 1. Клонируем репозитории

```bash
cd /opt
sudo mkdir -p mc-orchestrator && sudo chown $USER:$USER mc-orchestrator
cd mc-orchestrator

# backend + docker-compose
git clone <url-твоего-mine_com_server-репо> mine_com_server_repo
mv mine_com_server_repo/* mine_com_server_repo/.env.example mine_com_server_repo/.gitignore ./
rm -rf mine_com_server_repo

# web
git clone <url-твоего-mine_com_web-репо> ../mine_com_web
```

> Итоговая раскладка папок должна быть:
> `/opt/mc-orchestrator/` (содержит `docker-compose.yml`, `mine_com_server/`, `nginx/`)
> `/opt/mine_com_web/mc-orchestrator_web/` (статика)

Либо держи всё в одном каталоге — тогда в `.env` поправь `WEB_ROOT`.

---

## 2. Заполняем `.env`

```bash
cp .env.example .env
nano .env
```

**Обязательные значения:**

| Переменная | Как получить |
|---|---|
| `DB_PASSWORD` | `openssl rand -base64 32` |
| `APP_JWT_SECRET` | `openssl rand -base64 48` |
| `APP_ENCRYPTION_KEY` | **ровно 32 символа**: `openssl rand -base64 24 \| cut -c1-32` |
| `CERTBOT_EMAIL` | свой email |
| `PUBLIC_DOMAIN` | `mc-orchestrator.xyz` (оставь как есть) |

`PROXY_CONF` на первом запуске **не трогай** — по умолчанию стоит bootstrap-конфиг (только HTTP).

---

## 3. Поднимаем стек в первый раз

```bash
cd /opt/mc-orchestrator

# Сборка образа backend (первый раз долго — Gradle качает зависимости).
docker compose build app

# Запускаем БД, backend, web, bootstrap-proxy на :80.
docker compose up -d postgres app web proxy

# Ждём ~60 сек и проверяем:
docker compose ps
curl -sS http://mc-orchestrator.xyz/actuator/health
```

Если `{"status":"UP"}` — бэкенд живой и доступен по домену.

---

## 4. Выпускаем TLS-сертификат Let's Encrypt

```bash
docker compose run --rm certbot
```

В выводе должно быть `Successfully received certificate.`
Сертификаты лежат в `./nginx/letsencrypt/live/mc-orchestrator.xyz/`.

---

## 5. Переключаем nginx на HTTPS-конфиг

```bash
# Меняем указатель конфига в .env:
sed -i 's|^PROXY_CONF=.*|PROXY_CONF=./nginx/proxy.conf|' .env

# Перезапускаем только proxy:
docker compose up -d proxy

# Проверяем:
curl -I https://mc-orchestrator.xyz
```

Всё. Сайт висит на `https://mc-orchestrator.xyz`, API на `https://mc-orchestrator.xyz/api/...`, WebSocket на `wss://mc-orchestrator.xyz/ws`.

---

## 6. Автопродление сертификатов

Let's Encrypt сертификат живёт 90 дней. Добавь cron (от `$USER`):

```bash
crontab -e
```

```cron
0 3 * * 1 cd /opt/mc-orchestrator && docker compose run --rm certbot renew --webroot -w /var/www/certbot --quiet && docker compose exec proxy nginx -s reload
```

---

## 7. Обновление бэкенда / фронта

```bash
cd /opt/mc-orchestrator
git pull                        # обновить backend
git -C ../mine_com_web pull     # обновить web
docker compose build app
docker compose up -d app proxy web
```

---

## 8. Локальная разработка (Windows / macOS / Linux)

### Вариант А. Полностью в Docker (рекомендую)

```powershell
cd C:\Users\PC\Desktop\VUZ\mine_com_server
copy .env.example .env
# отредактируй .env: DB_PASSWORD / APP_JWT_SECRET / APP_ENCRYPTION_KEY
docker compose build app
docker compose up -d postgres app web proxy
```

Сайт: `http://localhost/`
API:  `http://localhost/api/...`
Backend напрямую: `http://localhost:8080` **не** торчит наружу — ходи через nginx proxy на `http://localhost/`.

### Вариант Б. Backend локально (без Docker), БД в Docker, фронт статикой

```powershell
# 1) БД:
cd C:\Users\PC\Desktop\VUZ\mine_com_server
docker compose up -d postgres

# 2) Backend:
cd mine_com_server
$env:DB_URL="jdbc:postgresql://localhost:5432/mc_orchestrator"
$env:DB_USER="postgres"
$env:DB_PASSWORD="<тот-же-что-в-.env>"
$env:APP_ENCRYPTION_KEY="<тот-же-что-в-.env>"
$env:APP_JWT_SECRET="dev-jwt-secret-change-me-at-least-32-bytes"
$env:APP_REMOTE_ROOT="/tmp/mc-com"
$env:APP_CORS_ALLOWED_ORIGINS="http://localhost:5500,http://127.0.0.1:5500"
./gradlew bootRun

# 3) Веб открой через Live Server / простой http-server:
cd C:\Users\PC\Desktop\VUZ\mine_com_web\mc-orchestrator_web
npx http-server -p 5500
```

Браузер: `http://localhost:5500/auth.html`. API сам дотянется до `http://localhost:8080`.

---

## 9. Полезные команды

| Что | Команда |
|---|---|
| Логи backend | `docker compose logs -f app` |
| Логи nginx | `docker compose logs -f proxy` |
| Рестарт бэкенда | `docker compose restart app` |
| Полная пересборка | `docker compose build --no-cache app && docker compose up -d app` |
| Забрать всё | `docker compose down` |
| Забрать + данные БД | `docker compose down -v` |
| Войти в БД | `docker compose exec postgres psql -U postgres -d mc_orchestrator` |

---

## 10. Важные нюансы

- **`APP_ENCRYPTION_KEY` ровно 32 символа.** Поменяешь — потеряешь все сохранённые SSH/RCON-креденшлы.
- **Postgres наружу не торчит.** Если надо — добавь в `docker-compose.yml` у сервиса `postgres` `ports: ["5432:5432"]`.
- **VM-бандлы**: в Docker-образе бэкенда их нет (JAR стал 62 МБ вместо 1.9 ГБ). Нужен VM-режим деплоя Minecraft-сервера с предсобранными бандлами — раскомментируй в `docker-compose.yml` строку `./mine_com_server/src/main/resources/server-dist:/app/server-dist:ro` и выстави `APP_SERVER_DIST_ROOT=/app/server-dist` в `.env`.
- **DNS прежде всего.** Let's Encrypt проверяет домен через HTTP-challenge, так что домен **обязан** резолвиться в IP сервера до запуска certbot.
- **Firewall.** Открой 80 и 443: `sudo ufw allow 80,443/tcp`.
