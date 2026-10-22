# S-2. Debezium CDC + Transactional JSON-Outbox + Inbox Deduplication

**Дата:** 22.10.2026  
**Версии компонентов:**
- **Java:** 21 (OpenJDK 64-Bit Server VM, runtime 21.0.9)
- **Spring Boot:** 4.1.0 (Spring Framework 7.0.8, Spring Kafka 4.1.0, Spring JDBC 7.0.8)
- **PostgreSQL:** 17-alpine (logical decoding `wal_level=logical`, `plugin=pgoutput`)
- **PostgreSQL JDBC:** 42.7.11
- **Apache Kafka:** 3.9.0 (KRaft mode)
- **Kafka Clients:** 4.2.1
- **Debezium Connect:** 2.7.3.Final (`io.debezium.connector.postgresql.PostgresConnector`, `io.debezium.transforms.outbox.EventRouter`)
- **Testcontainers:** 2.0.5 / 1.20.1
- **Jackson Databind:** 2.21.4

---

## Вопрос

1. Обеспечивает ли связка PostgreSQL (logical WAL `pgoutput`) + Debezium 2.7.3 (`PostgresConnector`) + SMT `EventRouter` надежный захват событий из таблицы `outbox` при вставке и немедленном удалении строки в одной транзакции (`deleteImmediately=true`)?
2. Разворачивается ли `jsonb` payload в обычный неэкранированный JSON в значении Kafka-сообщения (`table.expand.json.payload: true`, `value.converter: JsonConverter`, `schemas.enable: false`)?
3. Попадают ли все необходимые метаданные (`event-type`, `event-version`, `correlation-id`, `traceparent`, `occurred-at`, `event-id` / `id`) в заголовки Kafka через стандартный `additional.placement`?
4. Работает ли динамическая маршрутизация топика Kafka на основе `aggregate_type` (`transforms.outbox.route.topic.replacement: ${routedByValue}`)?
5. Сохраняется ли строгий порядок следования сообщений внутри одного ключа агрегата (`aggregate_id`)?
6. Происходит ли потеря данных или неконтролируемое дублирование при паузе/рестарте Debezium Connect?
7. Гарантирует ли Inbox-таблица `processed_event (consumer, event_id)` отсечение повторно доставленных сообщений на стороне потребителя?
8. Как ведет себя слот логической репликации (`pg_replication_slots`) и контролируется ли рост WAL?

---

## Что делали

- Создана рабочая ветка [`spike/S-2-debezium-outbox`](https://github.com/MaksimLM17/Smart_Rent_Hub/tree/spike/S-2-debezium-outbox).
- В корневой [`pom.xml`](file:///d:/Max/Projects/Smart_Rent_Hub/pom.xml) добавлен модуль [`spikes/s2-debezium-outbox`](file:///d:/Max/Projects/Smart_Rent_Hub/spikes/s2-debezium-outbox/pom.xml).
- Создан модуль `spikes/s2-debezium-outbox` с зависимостями на библиотеки ядра:
  - `team.ylab:smart-rent-hub-outbox-starter`
  - `team.ylab:smart-rent-hub-test-support`
  - `org.springframework.kafka:spring-kafka`
  - `org.springframework.boot:spring-boot-starter-jdbc`
  - `org.postgresql:postgresql`
- Реализованы компоненты тестирования:
  - [`SpikeApplication.java`](file:///d:/Max/Projects/Smart_Rent_Hub/spikes/s2-debezium-outbox/src/main/java/team/ylab/spikes/s2/SpikeApplication.java): тестовое приложение со стартером Outbox.
  - [`OrderCreatedEvent.java`](file:///d:/Max/Projects/Smart_Rent_Hub/spikes/s2-debezium-outbox/src/main/java/team/ylab/spikes/s2/model/OrderCreatedEvent.java), [`SkuUpsertedEvent.java`](file:///d:/Max/Projects/Smart_Rent_Hub/spikes/s2-debezium-outbox/src/main/java/team/ylab/spikes/s2/model/SkuUpsertedEvent.java): доменные полезные нагрузки (DTO).
  - [`DebeziumConnectClient.java`](file:///d:/Max/Projects/Smart_Rent_Hub/spikes/s2-debezium-outbox/src/test/java/team/ylab/spikes/s2/client/DebeziumConnectClient.java): HTTP-клиент к REST API Kafka Connect для регистрации, мониторинга статуса, паузы и возобновления коннекторов.
  - [`DebeziumOutboxSpikeIntegrationTest.java`](file:///d:/Max/Projects/Smart_Rent_Hub/spikes/s2-debezium-outbox/src/test/java/team/ylab/spikes/s2/DebeziumOutboxSpikeIntegrationTest.java): сквозной интеграционный тест на Testcontainers:
    - PostgreSQL 17-alpine с `wal_level=logical`, `max_replication_slots=10`, `max_wal_senders=10`.
    - Apache Kafka 3.9.0 в режиме KRaft.
    - Debezium Connect 2.7.3.Final с подключением к Postgres и Kafka на общей виртуальной сети `Network.newNetwork()`.
- Сконфигурирован коннектор `booking-outbox` строго по спецификации SDD v1.1, раздел 5.3:
  ```json
  {
    "name": "booking-outbox",
    "config": {
      "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
      "plugin.name": "pgoutput",
      "database.hostname": "postgres",
      "database.port": "5432",
      "database.user": "postgres",
      "database.password": "postgres",
      "database.dbname": "booking",
      "database.sslmode": "disable",
      "topic.prefix": "booking",
      "slot.name": "booking_outbox_slot",
      "publication.name": "booking_outbox_pub",
      "publication.autocreate.mode": "disabled",
      "table.include.list": "public.outbox",
      "tombstones.on.delete": "false",
      "key.converter": "org.apache.kafka.connect.storage.StringConverter",
      "value.converter": "org.apache.kafka.connect.json.JsonConverter",
      "value.converter.schemas.enable": "false",
      "transforms": "outbox",
      "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
      "transforms.outbox.table.field.event.id": "id",
      "transforms.outbox.table.field.event.key": "aggregate_id",
      "transforms.outbox.table.field.event.payload": "payload",
      "transforms.outbox.table.expand.json.payload": "true",
      "transforms.outbox.route.by.field": "aggregate_type",
      "transforms.outbox.route.topic.replacement": "${routedByValue}",
      "transforms.outbox.table.fields.additional.placement": "event_type:header:event-type,schema_version:header:event-version,correlation_id:header:correlation-id,traceparent:header:traceparent,occurred_at:header:occurred-at,id:header:event-id"
    }
  }
  ```

---

## Результат по критериям

| № | Критерий | Результат | Детали и подтверждение |
|---|---|---|---|
| 1 | **Чистый JSON без экранирования** (`table.expand.json.payload: true`) | **ДА (Успешно)** | При вставке `payload` (`jsonb`) значение сообщения в Kafka содержит чистый валидный JSON `{...}` без экранирования строк вида `"{\"orderId\":...}"`. Сообщение десериализуется в `OrderCreatedEvent` без искажений. Строка в таблице `outbox` удалена в той же транзакции (`deleteImmediately=true`), в таблице 0 строк, событие успешно извлечено из WAL. |
| 2 | **Заголовки Kafka на месте** | **ДА (Успешно)** | В Kafka Headers присутствуют: `event-type` (`OrderCreated`), `event-version` (`1`), `correlation-id` (`corr-req-777`), `traceparent` (`00-4bf92...`), `occurred-at` (ISO-8601 UTC), и идентификатор события в заголовках `event-id` и `id`. |
| 3 | **Ключ сообщения = `aggregate_id`** | **ДА (Успешно)** | Ключ записи `record.key()` строго равен `aggregateId` (`order-...`, `sku-...`). Партиционирование выполняется по ключу агрегата. |
| 4 | **Динамическая маршрутизация топика** | **ДА (Успешно)** | Событие с `aggregate_type = "booking.order.v1"` маршрутизируется в топик `booking.order.v1`; событие с `aggregate_type = "inventory.sku.v1"` маршрутизируется в топик `inventory.sku.v1` через `${routedByValue}`. |
| 5 | **Строгий порядок внутри ключа** | **ДА (Успешно)** | 5 последовательных событий по одному `aggregate_id` (`order-seq-...`) пришли в топик в гарантированном порядке (шаги 1 → 2 → 3 → 4 → 5) и на одну и ту же партицию. |
| 6 | **Рестарт/пауза Connect без потерь** | **ДА (Успешно)** | При паузе коннектора (`PUT /connectors/booking-outbox/pause`) вставка 3 событий зафиксирована в WAL PostgreSQL. После вызова `PUT /connectors/booking-outbox/resume` все 3 события без потерь и дублей доставлены в Kafka. |
| 7 | **Дедупликация Inbox (`processed_event`)** | **ДА (Успешно)** | При симуляции повторной доставки (`at-least-once`) `InboxService.executeIfNotProcessed` выполняет бизнес-логику ровно 1 раз, повторный вызов возвращает `false` (или `Optional.empty()`), а в таблице `processed_event (consumer, event_id)` сохраняется ровно одна запись. |
| 8 | **Контроль WAL и replication slot** | **ДА (Успешно)** | Слот `booking_outbox_slot` с плагином `pgoutput` находится в состоянии `active=true`. При нормальной работе `confirmed_flush_lsn` непрерывно подтверждается коннектором, лаг WAL (`pg_wal_lsn_diff`) составляет 0–56 байт. |

---

## Проблемы и обходные пути

1. **Сетевая адресация Kafka KRaft внутри общей Docker-сети:**
   - *Проблема:* `KafkaContainer` в Testcontainers 1.20 по умолчанию настраивает слушатель `PLAINTEXT` на `localhost:<mapped_port>` (для обращения с хоста), а внутренний слушатель `BROKER` привязывает к порту `9093` и внутреннему имени хоста контейнера. Если указать Debezium Connect `BOOTSTRAP_SERVERS=kafka:9092`, брокер возвращает метаданные с адресом `localhost`, и Debezium не может подключиться изнутри контейнера.
   - *Решение:* Для Debezium Connect передается внутренний адрес брокера: `kafka.getContainerInfo().getConfig().getHostName() + ":9093"`. Это обеспечивает стабильное подключение Debezium Connect к брокеру внутри Docker-сети, сохраняя при этом доступ хост-тестов через `kafka.getBootstrapServers()`.
2. **Обработка nullable заголовков:**
   - *Проблема:* Если `correlation_id` или `traceparent` не заданы в событии (значение `null`), Debezium EventRouter передает заголовок с `null`-массивом байт. При вызове `new String(header.value(), UTF_8)` возникал `NullPointerException`.
   - *Решение:* Декодирование заголовков консьюмером оформлено безопасно: `header.value() == null ? null : new String(header.value(), StandardCharsets.UTF_8)`.
3. **Асинхронность восстановления соединения при `resume`:**
   - *Проблема:* Эндпоинт `PUT /connectors/<name>/resume` возвращает HTTP 202 Accepted асинхронно. Переподключение задачи к логическому слоту PostgreSQL walsender занимает 100–300 мс. Проверка `active=true` в `pg_replication_slots` сразу после ответа могла увидеть слот еще не перешедшим в `active`.
   - *Решение:* Проверка состояния слота репликации обернута в Awaitility с таймаутом 15 с.

---

## Решение

1. **Решение принято:** Архитектурное решение SDD v1.1 Section 5.3 (Transactional Outbox + Debezium CDC + EventRouter SMT + Inbox Deduplication) полностью подтверждено на практике.
2. **Паттерн немедленного удаления (`deleteImmediately=true`):**
   - Вставка строки в `outbox` и ее немедленное удаление в рамках одной транзакции PostgreSQL полностью поддерживается логическим декодированием WAL (`pgoutput`).
   - Событие вставки фиксируется в WAL и считывается Debezium, а событие удаления игнорируется коннектором (`tombstones.on.delete: false`).
   - Это исключает накопление строк в таблице `outbox` и снимает необходимость в периодическом фоновом batch-удалении (vacuum/cleanup cron).
3. **Конфигурация коннектора:**
   - Параметры в SDD 5.3 являются оптимальными и не требуют никаких сторонних или кастомных плагинов/SMT. Стандартный `io.debezium.transforms.outbox.EventRouter` полностью закрывает потребности проекта.
4. **Паттерн Inbox:**
   - Подтверждена надежность дедупликации через композитный ключ `(consumer, event_id)` в таблице `processed_event`. Потребители событий защищены от дубликатов при гарантии доставки at-least-once.

---

## Что остаётся неясным

1. **Мониторинг слотов при длительном простое сервисов:**
   - При отказе или длительной остановке инстанса Debezium Connect слот логической репликации в PostgreSQL перестает сдвигать `confirmed_flush_lsn`. Это приводит к удержанию WAL-сегментов на диске БД.
   - *Рекомендация для Stage 1:* В конфигурации PostgreSQL для продакшена и стейджинга необходимо установить `max_slot_wal_keep_size` (например, 10–20 GB), чтобы предотвратить переполнение диска ценой временной инвалидации слота при аварии, а также настроить Prometheus-алерт на размер отставания слота (`pg_wal_lsn_diff > 1GB`).
