# Локальная инфраструктура Smart Rent Hub

В данном каталоге находится конфигурация Docker Compose и скрипты инициализации инфраструктурных сервисов (PostgreSQL, Redis, MinIO, Keycloak, Kafka, Debezium, стек мониторинга).

## 1. Структура

```
infra/
├── compose/
│   └── docker-compose.core.yml   # Сервисы профиля core (PostgreSQL, ...)
├── postgres/
│   ├── init/
│   │   └── 01-init-databases.sh  # Инициализация БД, ролей и прав доступа
│   ├── verify-postgres.ps1       # Скрипт верификации для Windows (PowerShell)
│   └── verify-postgres.sh        # Скрипт верификации для Linux/macOS (Bash)
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

## 3. Запуск и проверка

### Запуск контейнера
```bash
docker compose --profile core up -d postgres
```

### Проверка работоспособности и прав доступа

Для Windows:
```powershell
powershell -ExecutionPolicy Bypass -File infra/postgres/verify-postgres.ps1
```

Для Linux / macOS / WSL:
```bash
bash infra/postgres/verify-postgres.sh
```

Скрипты верификации проверяют:
- Включение `wal_level=logical`, лимиты слотов и отправителей WAL.
- Наличие всех 9 баз данных.
- Работу системных пользователей `debezium`, `keycloak`, `litellm`.
- Для всех 7 бизнес-сервисов: создание таблицы владельцем, чтение/запись пользователем приложения, **отклонение попытки создания таблицы пользователем приложения (DDL forbidden)**, чтение данных пользователем Debezium, очистка тестовой таблицы.
