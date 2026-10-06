# Smart Rent Hub

Smart Rent Hub — высоконадёжная распределённая B2B/B2C SaaS-платформа для шеринга и аренды строительного оборудования, инструмента и спецтехники.

Архитектура системы основана на микросервисном подходе (Spring Boot 4.1.x, Java 21), событийно-ориентированном взаимодействии (Kafka, Debezium Transactional Outbox), оркестрации бизнес-процессов (Operaton BPMN), многоуровневой защите (Keycloak, BFF Gateway) и сквозной наблюдаемости (OpenTelemetry, Jaeger, Prometheus, Grafana, Loki, Alloy).

---

## 1. Системные требования (Рабочая станция Windows)

Для локальной разработки и запуска полного стенда на Windows требуется:

- **ОС:** Windows 10/11 (Pro, Enterprise или Home с включённым WSL2)
- **WSL 2:** установленный дистрибутив (рекомендуется Ubuntu 22.04 / 24.04)
- **Docker Desktop:** версия 4.25+ с бэкендом WSL 2 (выделение ОЗУ: минимум 8 ГБ для профиля `core`, 12–16 ГБ для `core` + `obs`)
- **Java Development Kit (JDK):** Java 21 (Eclipse Temurin или BellSoft Liberica)
- **PowerShell:** PowerShell 5.1 (встроен в Windows) или PowerShell 7+ (pwsh)
- **Git:** Git для Windows (с включённой поддержкой `autocrlf = input` или `.gitattributes`)

> [!TIP]
> **Оптимизация памяти WSL2:**  
> Рекомендуется создать файл `C:\Users\<ВашПользователь>\.wslconfig` со следующим содержимым:
> ```ini
> [wsl2]
> memory=12GB
> processors=4
> swap=4GB
> ```

---

## 2. Быстрый старт (Скрипты запуска)

Для комфортной работы в Windows терминале (PowerShell) подготовлены скрипты управления инфраструктурным стендом:

### Запуск инфраструктуры (`.\up.ps1`)

```powershell
# 1. Запуск базового профиля core (PostgreSQL, Redis, MinIO, Keycloak, Kafka, Kafka Connect, external-stubs)
.\up.ps1

# 2. Запуск базового профиля вместе со стеком наблюдаемости (Jaeger, Prometheus, Grafana, Loki, Alloy)
.\up.ps1 -Obs

# 3. Принудительная пересборка локальных контейнеров (например, external-stubs)
.\up.ps1 -Obs -Build

# 4. Запуск всех доступных профилей
.\up.ps1 -All
```

Скрипт `up.ps1`:
- Проверяет запуск Docker-демона;
- Автоматически инициализирует `.env` из `.env.example`, если он отсутствует;
- Поднимает контейнеры Docker Compose;
- Ожидает прохождения всех проверок здоровья (`healthcheck`);
- Автоматически регистрирует коннекторы Debezium Outbox;
- Выводит сводную таблицу со всеми URL, портами и учётными данными.

### Остановка инфраструктуры (`.\down.ps1`)

```powershell
# Остановка всех запущенных сервисов с сохранением данных в томах
.\down.ps1

# Остановка с удалением именованных томов (PostgreSQL, Kafka, MinIO и т.д.)
.\down.ps1 -Volumes
```

### Полный сброс стенда с нуля (`.\reset.ps1`)

```powershell
# Полная очистка томов данных и повторный запуск свежего стенда
.\reset.ps1

# Полный сброс с профилем наблюдаемости без запроса подтверждения
.\reset.ps1 -Obs -Force
```

*(Пользователи Linux, WSL и macOS могут использовать аналогичные Bash-скрипты: `bash infra/scripts/up.sh --obs`, `bash infra/scripts/down.sh`, `bash infra/scripts/reset.sh`)*.

---

## 3. Профили Docker Compose

В соответствии с SDD 12.2 сервисы разделены по профилям:

| Профиль | Сервисы | Описание |
|---|---|---|
| **`core`** | PostgreSQL 17, Redis 7, MinIO, Keycloak 26, Kafka 3.9 KRaft, Kafka Connect + Debezium 2.7, external-stubs | Базовая инфраструктура платформы, необходимая для запуска бизнес-сервисов и E2E тестов |
| **`obs`** | OTel Collector, Jaeger, Prometheus, Grafana Loki, Grafana Alloy, Grafana | Сквозная трассировка, метрики, сбор структурированных JSON-логов и дашборды мониторинга |
| **`search`** | Elasticsearch, search-service *(будет добавлен на следующих этапах)* | Полнотекстовый поиск по каталогу товаров |
| **`ai`** | LiteLLM, Ollama, ai-assistant *(будет добавлен на следующих этапах)* | Локальный AI-конвейер подбора оборудования |
| **`finance`** | ClickHouse / DuckDB, finance-analytics *(будет добавлен на следующих этапах)* | Аналитика и финотчётность |

---

## 4. Сводная шпаргалка сервисов и доступов

| Сервис | Хост / URL | Порт | Учётные данные / Параметры |
|---|---|---|---|
| **PostgreSQL** | `localhost` | `5432` | user: `postgres`, pass: `postgres` (схемы: `booking`, `inventory`, `payments`, `keycloak`...) |
| **Redis** | `localhost` | `6379` | пароль не требуется |
| **MinIO S3 API** | `http://localhost:9000` | `9000` | user: `minioadmin`, pass: `minioadmin` |
| **MinIO Console** | `http://localhost:9001` | `9001` | user: `minioadmin`, pass: `minioadmin` (бакеты: `handover-photos`, `acts`, `catalog-media`) |
| **Keycloak** | `http://localhost:8081` | `8081` | user: `admin`, pass: `admin`, Realm: `smartrent` |
| **Kafka Connect** | `http://localhost:8083` | `8083` | REST API коннекторов Debezium |
| **External Stubs** | `http://localhost:8089` | `8089` | Заглушки SMS (`/api/stubs/sms`), Bank ID (`/api/stubs/bank-id`), Payments (`/api/stubs/payments`) |
| **Jaeger UI** | `http://localhost:16686` | `16686` | Трассировки и OTLP-приёмник |
| **Prometheus** | `http://localhost:9090` | `9090` | Метрики серверов и приложений |
| **Grafana** | `http://localhost:3000` | `3000` | user: `admin`, pass: `admin` (предсоздан дашборд `Smart Rent Hub — System Overview`) |
| **Grafana Loki** | `http://localhost:3100` | `3100` | REST API приёма и запроса логов |
| **Grafana Alloy** | `http://localhost:12345` | `12345` | Агент сбора логов и отправки в Loki (HTTP listener: `9999`) |
| **OTel Collector** | `localhost` | `4317` (gRPC), `4318` (HTTP) | OpenTelemetry Collector OTLP-порты |

---

## 5. Скрипты верификации компонентов

Каждый инфраструктурный компонент снабжён сценарием проверки:

```powershell
# Проверка PostgreSQL (логическая репликация, права и роли сервисов)
powershell -ExecutionPolicy Bypass -File infra/postgres/verify-postgres.ps1

# Проверка Redis и бакетов MinIO
powershell -ExecutionPolicy Bypass -File infra/storage/verify-storage.ps1

# Проверка Keycloak (клиенты, роли, выпуск тестовых JWT)
powershell -ExecutionPolicy Bypass -File infra/keycloak/verify-keycloak.ps1

# Проверка Kafka Connect и Debezium outbox коннекторов
powershell -ExecutionPolicy Bypass -File infra/debezium/verify-kafka-connect.ps1

# Проверка сценариев внешних заглушек external-stubs
powershell -ExecutionPolicy Bypass -File infra/stubs/verify-stubs.ps1

# Проверка стека наблюдаемости (Jaeger, Prometheus, Loki, Grafana, Alloy)
powershell -ExecutionPolicy Bypass -File infra/obs/verify-obs.ps1
```

---

## 6. Структура монорепозитория

```
smart-rent-hub/
├── contracts/        # API контракты: gRPC proto, JSON Schema событий, OpenAPI
├── libs/             # Общие Java-библиотеки (security, outbox, observability, test-support)
├── services/         # Бэкенд микросервисы (gateway, inventory, booking-pricing, payments, risk...)
├── tools/            # Вспомогательные сервисы разработки (external-stubs)
├── web/              # Фронтенд приложения (customer-web, staff-web на Next.js)
├── infra/            # Инфраструктура, Docker Compose, манифесты и конфиги
├── load-tests/       # Нагрузочные тесты k6
└── docs/             # Архитектурная документация (PRD, SDD, ADR, план реализации)
```

---

## 7. Решение типовых проблем (FAQ & Troubleshooting)

### Ошибка ExecutionPolicy при запуске PowerShell скриптов
Если при запуске `.\up.ps1` возникает ошибка `File cannot be loaded because running scripts is disabled on this system`:
```powershell
# Разрешить выполнение скриптов для текущего процесса терминала
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

### Порт уже занят (например, 5432 или 8081)
Если на вашей машине уже работает локальный PostgreSQL или Keycloak:
1. Откройте `.env`;
2. Измените порт внешнего маппинга (например, `POSTGRES_PORT=5433` или `KEYCLOAK_PORT=8082`);
3. Перезапустите стенд: `.\up.ps1`.

### Недостаточно памяти для Docker Desktop
Если контейнеры Kafka Connect или Keycloak падают с кодом 137 (OOMKilled), увеличьте лимит памяти в настройках Docker Desktop (*Settings -> Resources -> Advanced*) либо в `.wslconfig` минимум до 8–10 ГБ.
