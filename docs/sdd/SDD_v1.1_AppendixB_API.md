# Smart Rent Hub — SDD, Приложение B: каталог API

**Основание:** SDD v1.1, разделы 3.4, 4, 9; PRD v3.1 (роли и разрешения, раздел 3).
**Состав:** соглашения, REST-эндпоинты через Gateway (с требуемыми разрешениями), gRPC-контракты между сервисами.
Это набросок контрактов: точные схемы фиксируются в `contracts/openapi` и `contracts/proto` и проверяются в CI.

---

## B.1. Соглашения REST (внешний API)

| Тема | Правило |
|---|---|
| Версионирование | Префикс `/api/v1`; обратно-несовместимые изменения — `/api/v2` |
| Контракт | OpenAPI 3.1, контракт хранится в `contracts/openapi`, клиент для Next.js генерируется из него `[ПРЕДЛОЖЕНО]` |
| Формат | JSON, UTF-8, `camelCase`, даты ISO-8601 в UTC, деньги строкой с двумя знаками (`"11568.84"`) |
| Ошибки | RFC 9457 `application/problem+json`: `type`, `title`, `status`, `detail`, `code` (бизнес-код), `traceId` |
| Идемпотентность | Заголовок `Idempotency-Key` обязателен для `POST`, создающих заказы, платежи, возвраты, претензии |
| Пагинация | Курсорная: `?limit=&cursor=`, ответ содержит `nextCursor` |
| Конкуренция | `ETag` / `If-Match` на карточках Inventory (значение — `@Version`), при несовпадении 412 |
| Корреляция | `X-Trace-Id` в ответе, `traceparent` принимается |
| Авторизация | Cookie-сессия Gateway (BFF); Gateway подставляет `Authorization: Bearer` для сервисов; WebSocket и SSE аутентифицируются той же cookie (4.1 SDD) |
| Защита от CSRF | `SameSite=Lax`, проверка `Origin`/`Referer`, заголовок `X-CSRF-Token` для небезопасных методов |
| Клиентские права | Клиент видит только свои ресурсы (проверка владения в сервисе) |

### Бизнес-коды ошибок (основные)

| Код | HTTP | Значение |
|---|---|---|
| `CAPACITY_UNAVAILABLE` | 409 | Нет ёмкости на выбранные даты |
| `RESERVATION_EXPIRED` | 409 | Истёк TTL резерва |
| `RISK_LIMIT_EXCEEDED` | 422 | Превышен лимит доверия клиента (TAV или число заказов) |
| `IDENTITY_REQUIRED` | 403 | Нужна идентификация на уровне риска заказа |
| `PAYMENT_DECLINED` | 402 | Платёж отклонён |
| `PRICE_CHANGED` | 409 | Тариф изменился, нужно повторить расчёт |
| `INVALID_STATE` | 409 | Недопустимый переход статуса |
| `SOD_BLOCKED` | 403 | Действие запрещено правилом разделения обязанностей |
| `APPROVAL_REQUIRED` | 202 | Действие ожидает второго утверждающего |
| `VERSION_CONFLICT` | 412 | Устаревшая версия карточки |

---

## B.2. REST: клиентский API

| Сервис | Метод и путь | Доступ | Назначение |
|---|---|---|---|
| search | `GET /api/v1/search?q&category&from&to` | Публично | Нечёткий поиск + флаг доступности и цена «от» |
| search | `GET /api/v1/search/suggest?q` | Публично | Подсказки |
| inventory | `GET /api/v1/catalog/categories`, `GET /api/v1/catalog/skus/{skuId}` | Публично | Каталог и карточка SKU |
| booking | `GET /api/v1/skus/{skuId}/availability?from&to&grade` | Публично | Доступность по суткам |
| booking | `POST /api/v1/quotes` | `CUSTOMER` | Расчёт цены без резерва (раскладка 5.3.3 PRD) |
| booking | `POST /api/v1/orders` | `CUSTOMER`, `Idempotency-Key` | Создать заказ `DRAFT` |
| booking | `PUT /api/v1/orders/{id}` | `CUSTOMER` (свой) | Изменить позиции и даты черновика |
| booking | `POST /api/v1/orders/{id}/checkout` | `CUSTOMER`, `Idempotency-Key` | Начать оформление: резерв + сага |
| booking | `GET /api/v1/orders`, `GET /api/v1/orders/{id}` | `CUSTOMER` (свои) | Список и карточка заказа |
| booking | `POST /api/v1/orders/{id}/cancel` | `CUSTOMER` (свой) | Отмена по правилам PRD 5.10 |
| booking | `POST /api/v1/orders/{id}/extend` | `CUSTOMER` (свой) | Продление (проверка ёмкости, без наценки спроса) |
| booking | `GET /api/v1/orders/{id}/replacement-offers` | `CUSTOMER` (свой) | Предложения замены при овербукинге |
| booking | `POST /api/v1/orders/{id}/replacement-offers/{offerId}/accept` и `/decline` | `CUSTOMER` (свой) | Решение клиента |
| payments | `POST /api/v1/payments/sessions` | `CUSTOMER` | Сессия оплаты и токенизации карты (заглушка) |
| payments | `GET /api/v1/orders/{id}/payment` | `CUSTOMER` (свой) | Статус оплаты и холда |
| risk | `POST /api/v1/identity/sessions` | `CUSTOMER` | Начать идентификацию |
| risk | `POST /api/v1/identity/callback/{provider}` | Подпись провайдера | Результат идентификации (вызывается заглушкой или провайдером) |
| risk | `GET /api/v1/me/trust` | `CUSTOMER` | Мой уровень доверия и лимиты |
| inventory | `GET /api/v1/claims`, `POST /api/v1/claims/{id}/respond` | `CUSTOMER` (свои) | Претензии и ответ клиента |
| ai-assistant | `POST /api/v1/assistant/sessions` | `CUSTOMER` | Создать сессию |
| ai-assistant | `POST /api/v1/assistant/sessions/{id}/messages` | `CUSTOMER` | Сообщение, ответ потоком SSE (`text/event-stream`) |
| gateway | `GET /oauth2/authorization/customer`, `GET /oauth2/authorization/staff`, `POST /logout`, `GET /api/v1/session` | Публично / сессия | Вход, выход, сведения о сессии (роли, имя, срок) |

Поле `checkoutStep` в ответе `GET /api/v1/orders/{id}` (`RISK`, `IDENTITY`, `PAYMENT`, `REVIEW`, `CONFIRMING`) показывает клиенту текущий шаг оформления.

### SSE-события AI-ответа

| Событие | Данные |
|---|---|
| `status` | Этап конвейера (`intent`, `search`, `compatibility`, `availability`) |
| `proposal` | Позиции, цена, доступность |
| `substitution` | Что заменено, на что и почему |
| `warning` | Позиция недоступна или более дорогая |
| `done` | Итог и идентификатор предложения |
| `error` | Код ошибки, сообщение без внутренних деталей |

---

## B.3. REST: API персонала

Каждый эндпоинт проверяет разрешение в сервисе (SDD 9.2). Изменения подпадают под SoD там, где указано.

| Сервис | Метод и путь | Разрешение | Примечание |
|---|---|---|---|
| inventory | `GET/POST/PUT /api/v1/staff/skus` | `sku:*` | `If-Match` обязателен для `PUT` |
| inventory | `GET/POST/PUT /api/v1/staff/units`, `GET …/{id}/passport` | `unit:create/update` | Паспорт: износ, история, фото |
| inventory | `GET/POST/PUT /api/v1/staff/kits`, `/consumables` | `kit:*`, `consumable:*` | |
| inventory | `POST /api/v1/staff/media/upload-url` | `photo:upload` | Pre-signed URL для загрузки в MinIO |
| inventory | `POST /api/v1/staff/units/{id}/status` | `unit-status:adjust` | Причина обязательна, аудит |
| inventory | `POST /api/v1/staff/units/{id}/maintenance-verdict` | `maintenance:verdict` | Сброс `service_wear_days` — `wear:reset` |
| inventory | `POST /api/v1/staff/units/{id}/decommission` | `unit:decommission:propose` | SoD (п. 4 PRD 3.4) |
| inventory | `GET /api/v1/staff/pickup-tasks` | `pickup-task:read` | Задачи сборки |
| inventory | `POST /api/v1/staff/handovers` | `handover:perform` | Выдача: сканы, проверка личности, акт |
| inventory | `POST /api/v1/staff/returns` | `return:accept` | Приём возврата |
| inventory | `POST /api/v1/staff/inspections` | `inspection:perform` | Результат инспекции |
| inventory | `POST /api/v1/staff/defects` | `defect:record` | Фиксация дефекта |
| inventory | `GET /api/v1/staff/claims`, `POST …/{id}/assess` | `damage:assess` | Оценка ущерба |
| inventory | `POST /api/v1/staff/claims/{id}/resolve` | `claim:resolve` | SoD по порогу |
| booking | `GET /api/v1/staff/orders`, `…/{id}` | `order:read` | Все заказы |
| booking | `POST /api/v1/staff/orders/{id}/assign-units` | `unit-assignment:override` | Ручное назначение |
| booking | `GET /api/v1/staff/tariffs`, `POST …/tariffs`, `POST …/tariffs/{id}/versions` | `tariff:manage` | Версии с датой вступления |
| booking | `POST /api/v1/staff/pricing/simulate` | `pricing-report:read` | Симулятор цены |
| booking | `GET/PUT /api/v1/staff/config/booking/{key}` | `pricing-config:manage` | Параметры `CFG` |
| payments | `POST /api/v1/staff/payments/manual-charge` | `charge:manual` | SoD по порогу |
| payments | `POST /api/v1/staff/refunds` | `refund:issue` | SoD по порогу |
| payments | `GET /api/v1/staff/debts`, `POST …/{id}/close` | `debt:manage` | |
| risk | `GET /api/v1/staff/reviews` | `review:read` | Очередь модерации |
| risk | `POST /api/v1/staff/reviews/{id}/decision` | `review:decide` | Блок для собственных заказов |
| risk | `POST /api/v1/staff/blacklist`, `…/{id}/approve`, `…/{id}/lift` | `blacklist:propose`, `blacklist:approve` | |
| notification | `GET /api/v1/staff/tasks` | По `required_permission` задачи | Инбокс |
| notification | `POST /api/v1/staff/approvals/{id}/decision` | Вызывает `Approvals.Decide` в сервисе-владельце | Проверку SoD выполняет владелец |
| ai-assistant | `GET/POST/PUT /api/v1/staff/compat-rules`, `/compat-edges` | `compat-rule:manage` | |
| search | `GET/PUT /api/v1/staff/search/synonyms` | `search-dictionary:manage` | |
| finance | `GET /api/v1/staff/finance/funds`, `/units/{id}/amortization`, `/reports/*` | `fund-ledger:read`, `finance-report:read` | |
| finance | `POST /api/v1/staff/finance/periods/{period}/close` | `finance-report:read` + `FINANCE_OPERATOR` | Ручной запуск закрытия |
| any | `GET /api/v1/staff/audit?entity&id` | `audit:read` | Журнал аудита |
| any | `GET/PUT /api/v1/staff/config/{service}/{key}` | `config:manage` | Технические параметры |

**WebSocket:** `GET /ws/staff` (рукопожатие по cookie сессии, проверка `Origin`). Сообщения: `task.created`, `task.updated`, `task.cancelled`, `escalation`, `approval.requested`. Сервер присваивает каждому сообщению `seq`; при переподключении клиент передаёт `lastEventId`.

---

## B.4. gRPC: соглашения

| Тема | Правило |
|---|---|
| Пакеты | `srh.<сервис>.v1`, один `.proto` на сервис в `contracts/proto/<сервис>/v1` |
| Совместимость | Только добавление полей и методов; номера полей не переиспользуются; проверка `buf breaking` в CI |
| Идемпотентность | Все команды записи содержат `idempotency_key` |
| Дедлайны | Клиент всегда задаёт дедлайн: чтение 2 с, запись 5 с (Payments до 10 с) |
| Повторы | Только для идемпотентных вызовов и кода `UNAVAILABLE`, экспоненциальная задержка |
| Метаданные | `authorization` (токен пользователя или сервисного клиента), `traceparent`, `x-correlation-id` |
| Ошибки | Коды gRPC + `google.rpc.ErrorInfo` с бизнес-кодом (`CAPACITY_UNAVAILABLE` и др.) |
| Деньги | Сообщение `Money { string amount = 1; }` (десятичная строка), валюта RUB |

### Соответствие кодов gRPC

| Код | Когда |
|---|---|
| `INVALID_ARGUMENT` | Нарушена валидация запроса |
| `NOT_FOUND` | Сущность не найдена |
| `FAILED_PRECONDITION` | Нарушено бизнес-правило (недопустимый статус, лимит) |
| `ABORTED` | Конфликт конкуренции, повторить |
| `RESOURCE_EXHAUSTED` | Лимит частоты или ёмкость |
| `PERMISSION_DENIED` | Нет разрешения или `SOD_BLOCKED` |
| `UNAVAILABLE` | Сервис или зависимость недоступны, можно повторять |
| `DEADLINE_EXCEEDED` | Таймаут: для платежей сначала проверить статус |

---

## B.5. gRPC: контракты

### booking: доступность и цены (клиенты: search, ai-assistant)

```proto
syntax = "proto3";
package srh.booking.v1;

service AvailabilityService {
  rpc CheckAvailability(CheckAvailabilityRequest) returns (CheckAvailabilityResponse);
}

message CheckAvailabilityRequest {
  repeated SkuQuery items = 1;
  string from_date = 2;              // YYYY-MM-DD
  string to_date = 3;
}
message SkuQuery { string sku_id = 1; string grade = 2; int32 qty = 3; }
message CheckAvailabilityResponse { repeated SkuAvailability items = 1; }
message SkuAvailability {
  string sku_id = 1;
  string grade = 2;
  bool available = 3;
  int32 max_qty = 4;
  Money price_from = 5;              // ориентировочная цена за период
  bool surge = 6;
}
message Money { string amount = 1; }
```

### risk

```proto
syntax = "proto3";
package srh.risk.v1;

service RiskService {
  rpc AssessOrderRisk(AssessOrderRiskRequest) returns (AssessOrderRiskResponse);
  rpc RequestReview(RequestReviewRequest) returns (RequestReviewResponse);
  rpc GetCustomerLimits(GetCustomerLimitsRequest) returns (GetCustomerLimitsResponse);
}

message AssessOrderRiskRequest {
  string idempotency_key = 1;
  string order_id = 2;
  string customer_id = 3;
  string tav = 4;                    // десятичная строка
  int32 duration_days = 5;
}
message AssessOrderRiskResponse {
  string tier = 1;                   // LOW, MEDIUM, HIGH
  repeated string required_checks = 2;
  string hold_amount = 3;
  bool protection_required = 4;
  bool limits_ok = 5;
  string limit_violation_code = 6;   // RISK_LIMIT_EXCEEDED и др.
}
message RequestReviewRequest { string idempotency_key = 1; string order_id = 2; }
message RequestReviewResponse { string review_id = 1; }
message GetCustomerLimitsRequest { string customer_id = 1; }
message GetCustomerLimitsResponse {
  string trust_level = 1;
  string max_tav = 2;
  int32 max_active_orders = 3;
  bool blacklisted = 4;
}
```

### payments

```proto
syntax = "proto3";
package srh.payments.v1;

service PaymentsService {
  rpc AuthorizeHold(AuthorizeHoldRequest) returns (HoldResponse);
  rpc ReleaseHold(ReleaseHoldRequest) returns (HoldResponse);
  rpc CaptureHold(CaptureHoldRequest) returns (PaymentResponse);
  rpc Charge(ChargeRequest) returns (PaymentResponse);
  rpc Refund(RefundRequest) returns (PaymentResponse);
  rpc GetPaymentStatus(GetPaymentStatusRequest) returns (PaymentStatusResponse);
}

message AuthorizeHoldRequest {
  string idempotency_key = 1;
  string order_id = 2;
  string method_id = 3;
  string amount = 4;
}
message HoldResponse { string hold_id = 1; string status = 2; string provider_ref = 3; }
message ReleaseHoldRequest { string idempotency_key = 1; string hold_id = 2; }
message CaptureHoldRequest {
  string idempotency_key = 1; string hold_id = 2; string amount = 3; string purpose = 4;
}
message ChargeRequest {
  string idempotency_key = 1; string order_id = 2; string method_id = 3;
  string amount = 4; string purpose = 5;
}
message RefundRequest {
  string idempotency_key = 1; string order_id = 2; string amount = 3; string purpose = 4;
}
message PaymentResponse { string payment_id = 1; string status = 2; }
message GetPaymentStatusRequest { string idempotency_key = 1; }
message PaymentStatusResponse { string status = 1; string kind = 2; string provider_ref = 3; }
```

### inventory

```proto
syntax = "proto3";
package srh.inventory.v1;

service InventoryService {
  rpc AssignUnits(AssignUnitsRequest) returns (AssignUnitsResponse);
  rpc GetServiceableCounts(GetServiceableCountsRequest) returns (GetServiceableCountsResponse);
}

message AssignUnitsRequest {
  string idempotency_key = 1;
  string order_id = 2;
  repeated AssignLine lines = 3;
}
message AssignLine { string line_id = 1; string sku_id = 2; string grade = 3; int32 qty = 4; }
message AssignUnitsResponse {
  repeated Assigned assigned = 1;
  repeated Shortage shortages = 2;
}
message Assigned { string line_id = 1; repeated string unit_ids = 2; }
message Shortage { string line_id = 1; int32 missing_qty = 2; }
message GetServiceableCountsRequest { repeated string sku_ids = 1; }
message GetServiceableCountsResponse { repeated Count counts = 1; }
message Count { string sku_id = 1; string grade = 2; int32 units = 3; int64 version = 4; }
```

### search

```proto
syntax = "proto3";
package srh.search.v1;

service SearchService {
  rpc SearchSkus(SearchSkusRequest) returns (SearchSkusResponse);
}
message SearchSkusRequest {
  string category = 1;
  string text = 2;
  map<string, string> attributes = 3;   // например mount=Sony E
  int32 limit = 4;
}
message SearchSkusResponse { repeated SkuHit hits = 1; }
message SkuHit { string sku_id = 1; string name = 2; string category_id = 3; double score = 4; }
```

### approvals (реализуют сервисы-владельцы чувствительных действий)

```proto
syntax = "proto3";
package srh.platform.v1;

service ApprovalService {
  rpc Decide(DecideRequest) returns (DecideResponse);
}
message DecideRequest {
  string idempotency_key = 1;
  string request_id = 2;
  string decision = 3;                  // GRANT, REJECT
  string comment = 4;                   // решающий определяется по токену
}
message DecideResponse { string status = 1; }
```

Сервис-владелец сам определяет решающего из токена, проверяет `решающий != инициатор`, разрешение и актуальность запроса (SDD 6.5).

---

## B.6. Матрица вызовов между сервисами

| Клиент | Сервер | RPC | Когда |
|---|---|---|---|
| booking | risk | `AssessOrderRisk`, `RequestReview`, `GetCustomerLimits` | Сага `checkout` |
| booking | payments | `AuthorizeHold`, `ReleaseHold`, `Charge`, `CaptureHold`, `Refund`, `GetPaymentStatus` | Сага, просрочка, овербукинг |
| booking | inventory | `AssignUnits`, `GetServiceableCounts` | Задание назначения, сверка |
| search | booking | `CheckAvailability` | Обогащение результатов |
| ai-assistant | booking | `CheckAvailability` | Конвейер подбора |
| ai-assistant | search | `SearchSkus` | Конвейер подбора |
| notification | inventory, payments, risk, booking | `Decide` | Решение по согласованию SoD |
