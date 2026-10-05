# Локальная инфраструктура Smart Rent Hub

В данном каталоге находится конфигурация Docker Compose и скрипты инициализации инфраструктурных сервисов (PostgreSQL, Redis, MinIO, Keycloak, Kafka, Debezium, стек мониторинга).

## 1. Структура

```
infra/
├── compose/
│   └── docker-compose.core.yml   # Сервисы профиля core (PostgreSQL, Redis, MinIO, ...)
├── postgres/
│   ├── init/
│   │   └── 01-init-databases.sh  # Инициализация БД, ролей и прав доступа
│   ├── verify-postgres.ps1       # Скрипт верификации для Windows (PowerShell)
│   └── verify-postgres.sh        # Скрипт верификации для Linux/macOS (Bash)
├── storage/
│   ├── verify-storage.ps1        # Скрипт верификации Redis & MinIO (PowerShell)
│   └── verify-storage.sh         # Скрипт верификации Redis & MinIO (Bash)
└── pom.xml
```

В корне репозитория расположен основной `docker-compose.yml`, объединяющий модули через директиву `include`.

## 2. PostgreSQL (T1.1)

### Параметры сервера (CDC / Debezium)
- **Образ**: `postgres:17-alpine`
- **Порт**: `5432`
- **Именованный том**: `srh_postgres_data`
- **Сеть**: `srh-network`
- **Параметры репликации**:
  - `wal_level = logical`
  - `max_replication_slots = 10`
  - `max_wal_senders = 10`

### Создаваемые базы данных и разделение прав (SDD 5.2)

1. **Системные сервисы**:
   - `keycloak` (владелец: `keycloak`, пароль: `keycloak_pass`)
   - `litellm` (владелец: `litellm`, пароль: `litellm_pass`)
   - `debezium` (роль с атрибутом `REPLICATION`, пароль: `debezium_pass`)

2. **Бизнес-сервисы (`booking`, `inventory`, `payments`, `risk`, `notification`, `ai`, `finance`)**:
   Для каждого сервиса создаются две роли:
   - `<service>_owner` (`<service>_owner_pass`): Владелец базы и схемы `public`. Используется для миграций схемы (Liquibase/Flyway). Имеет полные права DDL (CREATE, ALTER, DROP).
   - `<service>_app` (`<service>_app_pass`): Пользователь для runtime-подключения микросервиса. Имеет доступ только к DML (SELECT, INSERT, UPDATE, DELETE на таблицы, USAGE/SELECT на последовательности, EXECUTE на процедуры). **Права DDL строго запрещены**. Настроены `ALTER DEFAULT PRIVILEGES`, автоматически предоставляющие DML-права на любые новые таблицы, созданные владельцем схемы.
   - Также в каждой БД предсоздана схема `bpm` для оркестратора Operaton со схожим разделением прав.
   - Пользователю `debezium` выданы права на чтение таблиц и создание публикации для CDC outbox.

### Проверка PostgreSQL

Для Windows:
```powershell
powershell -ExecutionPolicy Bypass -File infra/postgres/verify-postgres.ps1
```

Для Linux / macOS / WSL:
```bash
bash infra/postgres/verify-postgres.sh
```

---

## 3. Redis и MinIO (T1.2)

### Redis
- **Образ**: `redis:7-alpine`
- **Порт**: `6379`
- **Именованный том**: `srh_redis_data`
- **Назначение**: сессии Gateway (BFF), rate limiting, временные кэши, Pub/Sub для WebSocket.

### MinIO (S3-совместимое объектное хранилище)
- **Образ**: `minio/minio:latest`
- **Порты**:
  - `9000` (S3 API)
  - `9001` (Web Console)
- **Именованный том**: `srh_minio_data`
- **Учётные данные по умолчанию**: `minioadmin` / `minioadmin`
- **Автоматическая инициализация бакетов (`minio-init`)**:
  При старте Compose контейнер `minio-init` (`minio/mc:latest`) автоматически и идемпотентно создаёт требуемые бакеты:
  - `handover-photos` (фотографии приёма-передачи оборудования)
  - `acts` (сгенерированные акты)
  - `catalog-media` (фотографии и медиа карточек SKU каталога)

### Запуск сервисов
```bash
docker compose --profile core up -d
```

### Проверка Redis и MinIO

Для Windows:
```powershell
powershell -ExecutionPolicy Bypass -File infra/storage/verify-storage.ps1
```

Для Linux / macOS / WSL:
```bash
bash infra/storage/verify-storage.sh
```

Скрипт проверяет:
- Работоспособность Redis (PING/PONG, запись и чтение тестового ключа).
- Live healthcheck MinIO (`/minio/health/live`).
- Существование всех 3 обязательных бакетов (`handover-photos`, `acts`, `catalog-media`).
- Загрузку, чтение и удаление тестового объекта через MinIO Client (`mc`).

---

## 4. Keycloak (T1.3)

### Параметры сервера
- **Образ**: `quay.io/keycloak/keycloak:26.1`
- **Порты**: `8081` (внешний HTTP для хоста), `8080` (внутренний в сети Docker)
- **База данных**: PostgreSQL (`jdbc:postgresql://postgres:5432/keycloak`, пользователь `keycloak`)
- **Режим запуска**: `start-dev --import-realm`

### Realm `smartrent` как код
Файл конфигурации: [`infra/keycloak/realm-smartrent.json`](file:///d:/Max/Projects/Smart_Rent_Hub/infra/keycloak/realm-smartrent.json)

1. **Функциональные роли (PRD 3.2)**:
   - `CUSTOMER` — клиент сервиса
   - `CATALOG_EDITOR` — редактор каталога
   - `WAREHOUSE_OPERATOR` — оператор склада
   - `WAREHOUSE_SUPERVISOR` — старший смены склада
   - `SERVICE_ENGINEER` — сервисный инженер
   - `RISK_MODERATOR` — модератор рисков
   - `SUPPORT_AGENT` — оператор поддержки
   - `FINANCE_OPERATOR` — финансовый специалист
   - `PRICING_MANAGER` — менеджер по тарифам
   - `CONTENT_MANAGER` — контент-менеджер
   - `AUDITOR` — аудитор
   - `ADMIN` — администратор платформы

2. **Клиенты OIDC (SDD 9.1)**:
   - `srh-customer` (confidential, secret: `srh-customer-secret`, redirect URIs: `http://localhost:8080/login/oauth2/code/customer`, `http://localhost:3000/*`)
   - `srh-staff` (confidential, secret: `srh-staff-secret`, redirect URIs: `http://localhost:8080/login/oauth2/code/staff`, `http://localhost:3001/*`)
   - `srh-service-client` (client credentials, secret: `srh-service-secret`)

3. **Тестовые пользователи**:
   - `customer_test` (пароль `password123`, роль `CUSTOMER`)
   - `editor_test` (пароль `password123`, роль `CATALOG_EDITOR`)
   - `operator_test`, `supervisor_test`, `engineer_test`, `moderator_test`, `support_test`, `finance_test`, `pricing_test`, `admin_test`
   - `staff_customer_test` (роли `CUSTOMER` + `WAREHOUSE_OPERATOR` для проверки SoD)

### Проверка Keycloak

Для Windows:
```powershell
powershell -ExecutionPolicy Bypass -File infra/keycloak/verify-keycloak.ps1
```

Для Linux / macOS / WSL:
```bash
bash infra/keycloak/verify-keycloak.sh
```

Скрипт проверяет:
- Доступность realm `smartrent`.
- Успешное получение JWT-токена клиентом через `srh-customer` с ролью `CUSTOMER`.
- Успешное получение JWT-токена персоналом через `srh-staff` с ролями `CATALOG_EDITOR` и `ADMIN`.
- Успешное получение сервисного токена через `srh-service-client` (Client Credentials).

---

## 5. Kafka (KRaft) и Kafka Connect с Debezium (T1.4)

### Apache Kafka (KRaft)
- **Образ**: `apache/kafka:3.9.0`
- **Режим**: KRaft (без ZooKeeper)
- **Порты**:
  - `9092` (внутренний брокер для сервисов Docker)
  - `9094` (внешний доступ с хоста для инструментов и тестов)
- **Именованный том**: `srh_kafka_data`

### Kafka Connect с Debezium
- **Образ**: `debezium/connect:2.7.3.Final`
- **Порт**: `8083` (REST API управления коннекторами)
- **Предустановленный плагин**: `io.debezium.connector.postgresql.PostgresConnector`
- **Трансформация Outbox**: встроенный `io.debezium.transforms.outbox.EventRouter` (SDD 5.3)

### Коннекторы CDC Outbox (`infra/debezium/connectors/`)
Готовые конфигурации для сервисов платформы:
- `booking-outbox.json` (публикация событий заказов в топики `booking.*`)
- `inventory-outbox.json` (события каталога и единиц `inventory.*`)
- `payments-outbox.json` (события платежей `payments.*`)
- `risk-outbox.json` (события проверок рисков `risk.*`)
- `notification-outbox.json` (события уведомлений)
- `finance-outbox.json` (финансовые транзакции)

### Регистрация коннекторов
Скрипты автоматически дожидаются готовности Kafka Connect REST API и идемпотентно регистрируют коннекторы:
- Для Windows: `powershell -ExecutionPolicy Bypass -File infra/debezium/register-connectors.ps1`
- Для Linux: `bash infra/debezium/register-connectors.sh`

### Проверка Kafka и Kafka Connect

Для Windows:
```powershell
powershell -ExecutionPolicy Bypass -File infra/debezium/verify-kafka-connect.ps1
```

Для Linux / macOS / WSL:
```bash
bash infra/debezium/verify-kafka-connect.sh
```

Скрипт проверяет:
- Доступность Kafka Connect REST API.
- Загрузку плагина `io.debezium.connector.postgresql.PostgresConnector`.
- Автоматическую регистрацию коннектора `booking-outbox` (и остальных).
- Статус зарегистрированных коннекторов.


