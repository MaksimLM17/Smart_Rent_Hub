# Smart Rent Hub — SDD, Приложение C: каталог событий

**Основание:** SDD v1.1, разделы 5.3, 8.4, 8.5; PRD v3.1, раздел 7.
**Состав:** соглашения, топики, события с полями, примеры Avro-схем, правила эволюции, обработка ошибок.

---

## C.1. Соглашения

| Тема | Правило |
|---|---|
| Именование топиков | `<домен>.<агрегат>.v<N>`, например `booking.order.v1` |
| Ключ | Идентификатор агрегата в виде строки; порядок гарантируется в пределах ключа |
| Формат | JSON (UTF-8). В outbox `payload` хранится как `jsonb`, Debezium EventRouter разворачивает его в JSON-сообщение (`table.expand.json.payload`) |
| Контракт | JSON Schema (2020-12) на каждый тип события: `contracts/events/<сервис>/<EventType>.schema.json`; Java-record в модуле контрактов |
| Правила JSON | `camelCase`; UUID и даты — строками (ISO-8601, UTC); деньги и десятичные — строками (`"1250.00"`); перечисления — строками; потребители игнорируют неизвестные поля (tolerant reader) |
| Публикация | Только через Outbox + Debezium (SDD 5.3); приложение напрямую в Kafka не пишет |
| Доставка | At-least-once, потребители идемпотентны (Inbox) |
| Совместимость | Только аддитивные изменения: новое необязательное поле допустимо, удаление, переименование и смена типа запрещены. Проверка в CI (C.5) |
| Несовместимое изменение | Новый топик `.v2` и период параллельной публикации |
| Retention | По умолчанию 7 суток; compacted-топики помечены |
| Партиции (dev) | 3 на топик, `booking.order.v1` — 6; RF=1 в local и stage |

### Заголовки сообщения

| Заголовок | Источник | Значение |
|---|---|---|
| `id` | колонка `id` | UUID события (ключ дедупликации в Inbox; заголовок по умолчанию в EventRouter) |
| `event-type` | `event_type` | Имя события |
| `event-version` | `schema_version` | Версия схемы события |
| `occurred-at` | `occurred_at` | Время события (UTC, ISO-8601) |
| `correlation-id` | `correlation_id` | Сквозной бизнес-сценарий (например, `orderId`) |
| `traceparent` | `traceparent` | Контекст трассировки W3C |

### Повторы и ошибки у потребителей

- Spring Kafka `@RetryableTopic`: неблокирующие повторы с возрастающей задержкой (1 с, 10 с, 60 с), затем топик `<имя>-dlt`.
- Для «ядовитых» сообщений (ошибка разбора JSON) повторов нет, сразу в DLT.
- Корреляция сообщений процессу (Приложение C.3 и SDD 6.1.1): при отсутствии подписки событие повторяется; для завершённого процесса отбрасывается.
- Алерт на рост DLT; процедура ручной обработки (повтор после исправления, пропуск с причиной) — runbook.
- Смещение (offset) фиксируется после коммита транзакции с Inbox.

---

## C.2. Топики

| Топик | Ключ | Продюсер | Основные потребители | Retention |
|---|---|---|---|---|
| `booking.order.v1` | `orderId` | booking-pricing | inventory, payments, risk, notification, finance | 7 суток |
| `booking.reservation.v1` | `orderId` | booking-pricing | notification, finance (аналитика) | 7 суток |
| `booking.overbooking.v1` | `orderId` | booking-pricing | notification, risk | 7 суток |
| `booking.tariff.v1` | `scopeId` (SKU или категория) | booking-pricing | search, finance | compacted |
| `inventory.sku.v1` | `skuId` | inventory | search, ai-assistant, booking | compacted |
| `inventory.unit.v1` | `unitId` | inventory | finance, booking | 7 суток |
| `inventory.capacity-source.v1` | `skuId` | inventory | booking | compacted |
| `inventory.handover.v1` | `orderId` | inventory | booking, payments, risk, finance, notification | 7 суток |
| `payments.payment.v1` | `orderId` | payments | booking, risk, finance, notification | 7 суток |
| `risk.review.v1` | `orderId` | risk | booking, notification | 7 суток |
| `risk.identity.v1` | `orderId` | risk | booking | 7 суток |
| `risk.customer.v1` | `customerId` | risk | booking, notification | 7 суток |
| `platform.approval.v1` | `requestId` | любой сервис | notification, сервис-владелец | 7 суток |
| `platform.config.v1` | `service:key` | любой сервис | все | compacted |

---

## C.3. События

Поля общего конверта (`id`, `occurred-at` и т. д.) идут в заголовках; в таблицах ниже — полезная нагрузка (Avro-запись).

### booking

| Событие | Топик | Поля |
|---|---|---|
| `CapacityReserved` | `booking.reservation.v1` | `orderId`, `reservationId`, `lines[{skuId, grade, qty}]`, `fromDay`, `toDay`, `expiresAt` |
| `ReservationExpired` | `booking.reservation.v1` | `orderId`, `reservationId`, `reason` |
| `OrderConfirmed` | `booking.order.v1` | `orderId`, `customerId`, `riskTier`, `lines[{lineId, skuId, grade, qty}]`, `pickupSlotStart`, `returnDeadline`, `totalRent`, `protectionFee`, `holdAmount` |
| `OrderStatusChanged` | `booking.order.v1` | `orderId`, `fromStatus`, `toStatus`, `reason` |
| `OrderOverdue` | `booking.order.v1` | `orderId`, `overdueSince`, `stage` |
| `OrderCompleted` | `booking.order.v1` | `orderId`, `customerId`, `clean` (без нарушений), `totalRent`, `protectionFee`, `penalties` |
| `OrderCancelled` | `booking.order.v1` | `orderId`, `reason`, `refundAmount`, `penaltyAmount` |
| `OverbookingDetected` | `booking.overbooking.v1` | `orderId`, `lineId`, `skuId`, `grade`, `day`, `deficit` |
| `ReplacementOffered` | `booking.overbooking.v1` | `orderId`, `offerId`, `step`, `offeredSkuId`, `offeredGrade`, `surcharge`, `expiresAt` |
| `ReplacementAccepted` | `booking.overbooking.v1` | `orderId`, `offerId`, `surcharge` |
| `CompensationIssued` | `booking.overbooking.v1` | `orderId`, `refundAmount`, `bonusAmount`, `bonusExpiresAt` |
| `TariffChanged` | `booking.tariff.v1` | `scopeId`, `scopeType`, `version`, `baseRate`, `effectiveFrom` |

### inventory

| Событие | Топик | Поля |
|---|---|---|
| `SkuUpserted` | `inventory.sku.v1` | `skuId`, `categoryId`, `brand`, `model`, `name`, `attributes`, `replacementValue`, `sameDayTurnaround` |
| `ServiceableUnitsChanged` | `inventory.capacity-source.v1` | `skuId`, `counts[{grade, units}]`, `version` |
| `UnitAcquired` | `inventory.unit.v1` | `unitId`, `skuId`, `acquisitionCost`, `acquisitionDate`, `residualValuePercent`, `expectedLifeWearDays` |
| `UnitAssigned` | `inventory.unit.v1` | `unitId`, `orderId`, `lineId` |
| `WearUpdated` | `inventory.unit.v1` | `unitId`, `orderId`, `deltaWearDays`, `totalWearDays`, `serviceWearDays`, `actualDays`, `coefficient` |
| `WearAdjusted` | `inventory.unit.v1` | `unitId`, `orderId`, `deltaWearDays`, `reason` |
| `GradeChanged` | `inventory.unit.v1` | `unitId`, `skuId`, `fromGrade`, `toGrade` |
| `RepairCompleted` | `inventory.unit.v1` | `unitId`, `jobId`, `kind`, `cost` |
| `UnitDecommissioned` | `inventory.unit.v1` | `unitId`, `reason`, `bookValue`, `proceeds` |
| `UnitLost` | `inventory.unit.v1` | `unitId`, `orderId` |
| `UnitIssued` | `inventory.handover.v1` | `orderId`, `handoverId`, `unitIds`, `issuedAt`, `performedBy` |
| `UnitReturned` | `inventory.handover.v1` | `orderId`, `handoverId`, `unitIds`, `returnedAt` |
| `InspectionCompleted` | `inventory.handover.v1` | `orderId`, `unitId`, `result`, `overdue` |
| `ClaimOpened` | `inventory.handover.v1` | `orderId`, `claimId`, `unitId` |
| `ClaimResolved` | `inventory.handover.v1` | `orderId`, `claimId`, `claimAmount`, `platformShare`, `customerFault` |

### payments, risk, platform

| Событие | Топик | Поля |
|---|---|---|
| `PaymentAuthorized` | `payments.payment.v1` | `orderId`, `holdId`, `amount` |
| `PaymentFailed` | `payments.payment.v1` | `orderId`, `reason` |
| `HoldReleased` | `payments.payment.v1` | `orderId`, `holdId` |
| `ChargeSucceeded` | `payments.payment.v1` | `orderId`, `paymentId`, `amount`, `purpose` |
| `ChargeFailed` | `payments.payment.v1` | `orderId`, `purpose`, `reason` |
| `DebtRecorded` | `payments.payment.v1` | `orderId`, `customerId`, `amount` |
| `RefundIssued` | `payments.payment.v1` | `orderId`, `amount`, `purpose` |
| `IdentityVerified` | `risk.identity.v1` | `orderId`, `customerId`, `level` |
| `IdentityFailed` | `risk.identity.v1` | `orderId`, `customerId`, `reason` |
| `ReviewRequested` | `risk.review.v1` | `orderId`, `reviewId`, `dueAt` |
| `ReviewApproved` | `risk.review.v1` | `orderId`, `reviewId`, `decidedBy` |
| `ReviewRejected` | `risk.review.v1` | `orderId`, `reviewId`, `reason` |
| `TrustLevelChanged` | `risk.customer.v1` | `customerId`, `fromLevel`, `toLevel`, `reason` |
| `UserBlocked` | `risk.customer.v1` | `customerId`, `reason` |
| `ApprovalRequested` | `platform.approval.v1` | `requestId`, `action`, `subjectId`, `requestedBy`, `requiredPermission`, `ownerService` |
| `ApprovalGranted` / `ApprovalRejected` | `platform.approval.v1` | `requestId`, `decidedBy` |
| `SodOverrideLogged` | `platform.approval.v1` | `action`, `subjectId`, `actor`, `comment` (режим `LOG_ONLY`) |
| `ConfigChanged` | `platform.config.v1` | `service`, `key`, `version` |

### Какие события запускают процессы Operaton

| Сообщение процессу | Источник | Процесс |
|---|---|---|
| `IdentityVerified`, `IdentityFailed` | `risk.identity.v1` | `checkout` |
| `PaymentAuthorized`, `PaymentFailed` | `payments.payment.v1` | `checkout` |
| `ReviewApproved`, `ReviewRejected` | `risk.review.v1` | `checkout` |
| `ServiceableUnitsChanged` (при дефиците) | `inventory.capacity-source.v1` | запускает `overbooking` |
| `ReplacementAccepted/Declined` | REST клиента → внутреннее сообщение | `overbooking` |
| `UnitIssued` | `inventory.handover.v1` | завершает `pickup-watch`, запускает `overdue-billing` |
| `UnitReturned` | `inventory.handover.v1` | завершает `overdue-billing` |

Корреляция сообщений — по `orderId` (business key процесса). Имена сообщений в BPMN: `msg_<событие>`, например `msg_identity_verified`, `msg_payment_authorized`, `msg_review_rejected`. Правила ожидания и обработки гонок — SDD 6.1.1.

---

## C.4. Примеры контрактов JSON Schema

### WearUpdated

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://srh.example/events/inventory/WearUpdated.schema.json",
  "title": "WearUpdated",
  "type": "object",
  "required": ["unitId", "orderId", "deltaWearDays", "totalWearDays", "serviceWearDays", "actualDays", "coefficient"],
  "properties": {
    "unitId": { "type": "string", "format": "uuid" },
    "orderId": { "type": "string", "format": "uuid" },
    "deltaWearDays": { "type": "string", "pattern": "^\\d+(\\.\\d)?$" },
    "totalWearDays": { "type": "string", "pattern": "^\\d+(\\.\\d)?$" },
    "serviceWearDays": { "type": "string", "pattern": "^\\d+(\\.\\d)?$" },
    "actualDays": { "type": "string", "pattern": "^\\d+(\\.\\d)?$" },
    "coefficient": { "type": "string", "pattern": "^\\d+(\\.\\d)?$" },
    "note": { "type": "string" }
  },
  "additionalProperties": true
}
```

Пример сообщения (`payload`):

```json
{
  "unitId": "0192f3a0-1c2d-7e11-8a45-3b9f0c7d1a10",
  "orderId": "0192f3a0-5d4e-7a02-9c11-6e2b8d4f7c21",
  "deltaWearDays": "12.0",
  "totalWearDays": "48.0",
  "serviceWearDays": "48.0",
  "actualDays": "10.0",
  "coefficient": "1.2"
}
```

### ServiceableUnitsChanged

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://srh.example/events/inventory/ServiceableUnitsChanged.schema.json",
  "title": "ServiceableUnitsChanged",
  "type": "object",
  "required": ["skuId", "version", "counts"],
  "properties": {
    "skuId": { "type": "string", "format": "uuid" },
    "version": { "type": "integer", "minimum": 0 },
    "counts": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["grade", "units"],
        "properties": {
          "grade": { "enum": ["A", "B"] },
          "units": { "type": "integer", "minimum": 0 }
        },
        "additionalProperties": true
      }
    }
  },
  "additionalProperties": true
}
```

---

## C.5. Правила эволюции контрактов

1. Разрешено: добавить необязательное поле, добавить новый тип события, ослабить ограничение (например, расширить `enum` при условии, что потребители не падают на неизвестном значении).
2. Запрещено: удалять и переименовывать поля, менять тип поля, менять смысл существующего поля, делать необязательное поле обязательным.
3. Для несовместимого изменения создаётся топик `.v2`, продюсер публикует в оба топика на период миграции, потребители переключаются по одному, `.v1` закрывается после перехода.
4. Каждое событие имеет владельца (сервис-продюсер); потребитель контракт не определяет.
5. Деньги и дни износа — строки с десятичной точкой, не `number` (нет ошибок округления).
6. Персональные данные в событиях не передаются; идентификаторы клиента — только UUID.
7. **Проверка в CI:** (а) образцы событий (`samples/`) валидны по JSON Schema; (б) golden-файлы всех прежних версий (`golden/<EventType>/v*.json`) успешно читаются кодом текущих потребителей; (в) изменение схемы без добавленного образца блокирует сборку.

## C.6. Тестирование событий

- Контрактный тест продюсера: сериализация образцовых объектов и валидация по JSON Schema из репозитория.
- Тест потребителя: разбор golden-файлов прежних версий (аддитивная совместимость).
- Интеграционный тест (Testcontainers): Outbox → Debezium → Kafka → Inbox потребителя; повторная доставка не меняет результат.
- Тест порядка: события одного `orderId` обрабатываются в порядке публикации; события разных агрегатов не зависят друг от друга.
- Тест гонки (SDD 6.1.1): событие приходит до того, как процесс «уснул» — после неблокирующего повтора корреляция проходит; событие для завершённого процесса отбрасывается; опоздавший `PaymentAuthorized` вызывает `ReleaseHold`.

## C.7. Возможное развитие: Avro + Apicurio Registry

После MVP как учебное упражнение: выбрать один топик, опубликовать параллельный `.v2` в Avro (Apicurio, правило совместимости BACKWARD), перевести потребителей и закрыть `.v1`. Это отрабатывает и эволюцию схем, и процедуру миграции топика. В v1 Apicurio не разворачивается.
