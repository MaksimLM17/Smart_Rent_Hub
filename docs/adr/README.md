# Журнал архитектурных решений (ADR)

Источник: SDD v1.1, раздел 14. Каждое решение со временем получает отдельный файл `NNNN-название.md` по шаблону `0000-template.md`.

| ID | Решение | Статус | Альтернативы |
|---|---|---|---|
| ADR-001 | Spring Boot 4.1.x (вместо 3.x) | Принято | Остаться на 3.5 (вне OSS-поддержки), 4.0 (до 31.12.2026) |
| ADR-002 | Java 21 LTS | Принято | 17, 25 |
| ADR-003 | Монорепозиторий, Maven multi-module | Принято | Gradle, отдельные репозитории |
| ADR-004 | Микросервисы, 9 деплоимых единиц | Принято | Модульный монолит |
| ADR-005 | Один кластер PostgreSQL, БД на сервис | Принято | Отдельный инстанс на сервис |
| ADR-006 | Outbox + Debezium EventRouter; события в JSON, контракты в JSON Schema (CI + golden-файлы); Avro + Apicurio отложены | Принято (изменено в v1.1) | Avro + Apicurio, Protobuf |
| ADR-007 | Оркестрация: Operaton (форк Camunda 7), встроен в booking-pricing | Принято | Camunda 7 EE, Camunda 8, Temporal, собственный оркестратор |
| ADR-008 | REST снаружи, gRPC внутри (Spring Boot 4.1 gRPC) | Принято | Только REST |
| ADR-009 | Ёмкость: бакеты + условный UPDATE + упорядоченная блокировка | Принято | B, C, D (раздел 8.2) |
| ADR-010 | Таймеры процессов — BPMN, периодические задания — db-scheduler | Принято | Quartz |
| ADR-011 | Spring Cloud Gateway (реактивный) | Принято | Kong, Envoy |
| ADR-012 | Роли Keycloak → разрешения в `security-starter` | Предложено | Keycloak Authorization Services |
| ADR-013 | LiteLLM как LLM-шлюз; клиент — Spring AI (запасной — OpenAI Java SDK) | Принято | Прямые SDK провайдеров |
| ADR-014 | **Gateway как BFF**: OAuth2-вход, серверная сессия (Spring Session, Redis), TokenRelay; Next.js — чистый UI за единым origin | Принято (изменено в v1.1) | Next.js как BFF (проксирование WS/SSE неудобно), SPA с токенами в браузере |
| ADR-015 | Micrometer Tracing + OTel → Jaeger, Prometheus, Loki | Принято (PRD) | Brave |
| ADR-016 | Compose → k3s/kind + Helm | Принято | Облачный Kubernetes |
| ADR-017 | GitHub Actions, Jib, GHCR, buf, Dependabot | Предложено | GitLab CI, buildpacks |
| ADR-018 | Тесты: Testcontainers, k6, Toxiproxy, ArchUnit, Playwright | Предложено | Gatling, Pact |
| ADR-019 | Матрица совместимости в PostgreSQL | Принято | Neo4j |
| ADR-020 | Elasticsearch не определяет доступность | Принято (PRD) | — |
| ADR-021 | Ожидания в BPMN: Event-Based Gateway + Message Catch Event, корреляция по business key = `orderId`, повторы при гонке, компенсация опоздавшего `PaymentAuthorized` | Принято | External tasks, опрос состояния |
