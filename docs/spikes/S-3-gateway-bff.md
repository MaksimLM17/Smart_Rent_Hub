# S-3. Gateway как BFF (OAuth2, Session in Redis, TokenRelay, CSRF Hardening, Redis Rate Limiting)

Дата: 15.10.2026 (Части 1 и 2: T3.2, T3.3)  
Версии компонентов:
- **Java:** 21 (OpenJDK 64-Bit Server VM, vendor: JetBrains s.r.o., runtime 21.0.9)
- **Spring Boot:** 4.1.0 (Spring Framework 7.0.8, Spring Security 7.1.0)
- **Spring Cloud Gateway:** 4.3.0 (Spring Cloud 2025.1.0, Reactor Netty 1.3.0)
- **Spring Session Data Redis:** 4.1.0
- **Lettuce:** 7.5.2.RELEASE / 7.1.0.RELEASE
- **Redis:** 7.4-alpine (via Testcontainers 2.0.5 / 1.20.1)
- **Keycloak:** 26.1 (realm `smartrent`, клиенты `srh-customer`, `srh-staff`)
- **OpenTelemetry / Micrometer Tracing:** 1.5.0 / 1.15.0

---

## Вопрос

1. Реализуем ли полноценный паттерн **Backend-for-Frontend (BFF)** на реактивном стеке **Spring Cloud Gateway** под управлением **Spring Boot 4.1.0** и **Spring Security 7.1.0**?
2. Обеспечивается ли прозрачная OAuth2-аутентификация (Authorization Code Flow) через Keycloak с раздельными клиентами (`srh-customer` для покупателей и `srh-staff` для персонала)?
3. Работает ли хранение состояния сессии на стороне сервера в Redis через Spring Session Reactive с единственной `SESSION` cookie (`HttpOnly`, `SameSite=Lax`, `Path=/`), скрывая `access_token` и `refresh_token` от клиентского JavaScript в браузере?
4. Внедряет ли стандартный фильтр `TokenRelay` заголовок `Authorization: Bearer <accessToken>` в исходящие запросы к downstream-сервисам (например, `inventory`), обновляя токен при необходимости?
5. Обеспечивается ли Single Logout (`/logout`) с полной очисткой серверной сессии в Redis и экспирацией клиентской cookie (`Max-Age=0`)?
6. Как ведет себя шлюз при отказе или недоступности Redis (Graceful Degradation): выдает ли 500 Internal Server Error или корректно деградирует в сброс сессии и перенаправление на повторный вход?
7. Предоставляет ли эндпоинт `GET /api/v1/session` фронтенду данные о пользователе (логин, роли realm, срок действия сессии, CSRF-токен) без утечки токенов?
8. Надежно ли защищает шлюз от межсайтовой подделки запросов (CSRF) с использованием Double-Submit Cookie (`XSRF-TOKEN` cookie + `X-XSRF-TOKEN` / `X-CSRF-TOKEN` заголовок) и валидации заголовков `Origin` и `Referer` для всех мутирующих запросов (POST, PUT, DELETE, PATCH)?
9. Обеспечивает ли Redis Rate Limiter токен-бакет изоляцию по пользователю (`user:`), сессии (`session:`) и IP (`ip:`), возвращая 429 Problem Details с заголовками `X-RateLimit-*`, и функционирует ли политика Fail-Open при сбоях Redis?

---

## Что делали

- Созданы ветки [`feature/T3.2-gateway-oauth2-bff`](https://github.com/MaksimLM17/Smart_Rent_Hub/tree/feature/T3.2-gateway-oauth2-bff) и [`feature/T3.3-gateway-csrf-ratelimit`](https://github.com/MaksimLM17/Smart_Rent_Hub/tree/feature/T3.3-gateway-csrf-ratelimit).
- В [`services/gateway/pom.xml`](file:///d:/Max/Projects/Smart_Rent_Hub/services/gateway/pom.xml) подключены стартеры:
  - `org.springframework.boot:spring-boot-starter-oauth2-client` (Spring Security 7.1.0)
  - `org.springframework.boot:spring-boot-starter-data-redis-reactive` (Lettuce)
  - `org.springframework.session:spring-session-data-redis` (4.1.0)
  - `team.ylab:smart-rent-hub-test-support` и `org.springframework.security:spring-security-test`
- Разработаны конфигурационные и доменные классы BFF:
  - [`SrhGatewayProperties.java`](file:///d:/Max/Projects/Smart_Rent_Hub/services/gateway/src/main/java/team/ylab/gateway/config/SrhGatewayProperties.java): параметры сессии, безопасности, CSRF (`allowedOrigins`, `cookieName`, `headerName`) и Rate Limiting (`replenishRate`, `burstCapacity`, `requestedTokens`, `failOpen`).
  - [`GatewaySessionConfig.java`](file:///d:/Max/Projects/Smart_Rent_Hub/services/gateway/src/main/java/team/ylab/gateway/config/GatewaySessionConfig.java): активация `@EnableRedisWebSession`, настройка `CookieWebSessionIdResolver`, установка `ConfigureReactiveRedisAction.NO_OP`.
  - [`GatewaySecurityConfig.java`](file:///d:/Max/Projects/Smart_Rent_Hub/services/gateway/src/main/java/team/ylab/gateway/security/GatewaySecurityConfig.java): конфигурация реактивной цепочки `SecurityWebFilterChain` со связыванием `oauth2Login`, `ServerOAuth2AuthorizedClientRepository` на базе `WebSessionServerOAuth2AuthorizedClientRepository`, double-submit cookie CSRF репозитория и обработчиков.
  - [`OriginValidationWebFilter.java`](file:///d:/Max/Projects/Smart_Rent_Hub/services/gateway/src/main/java/team/ylab/gateway/security/OriginValidationWebFilter.java): реактивный фильтр с приоритетом `@Order(-110)` (перед Security), проверяющий заголовки `Origin` и `Referer` для всех мутирующих методов (POST, PUT, DELETE, PATCH) и отклоняющий запросы с чужих доменов с HTTP 403 RFC 9457 Problem Details (`urn:problem:csrf:forbidden-origin`).
  - [`SpaServerCsrfTokenRequestHandler.java`](file:///d:/Max/Projects/Smart_Rent_Hub/services/gateway/src/main/java/team/ylab/gateway/security/SpaServerCsrfTokenRequestHandler.java): кастомный резолвер CSRF-токена без маскирования XOR для SPA-приложений (Next.js), поддерживающий заголовки `X-XSRF-TOKEN` и `X-CSRF-TOKEN`.
  - [`CsrfCookieWebFilter.java`](file:///d:/Max/Projects/Smart_Rent_Hub/services/gateway/src/main/java/team/ylab/gateway/security/CsrfCookieWebFilter.java): реактивный фильтр с порядком `@Order(-50)` (после Security), подписывающийся на отложенный `Mono<CsrfToken>` для записи cookie `XSRF-TOKEN` (`HttpOnly=false`, `SameSite=Lax`, `Path=/`) в ответ клиенту.
  - [`CustomAccessDeniedHandler.java`](file:///d:/Max/Projects/Smart_Rent_Hub/services/gateway/src/main/java/team/ylab/gateway/security/CustomAccessDeniedHandler.java): формирует ответы HTTP 403 Forbidden в стандарте RFC 9457 `application/problem+json` при сбоях валидации CSRF и отказах в доступе.
  - [`CustomServerAuthenticationSuccessHandler.java`](file:///d:/Max/Projects/Smart_Rent_Hub/services/gateway/src/main/java/team/ylab/gateway/security/CustomServerAuthenticationSuccessHandler.java): маршрутизация после успешного входа — клиенты на `/`, персонал на `/staff` (с приоритетом `ServerRequestCache` для восстановления запрошенного URL).
  - [`CustomServerLogoutSuccessHandler.java`](file:///d:/Max/Projects/Smart_Rent_Hub/services/gateway/src/main/java/team/ylab/gateway/security/CustomServerLogoutSuccessHandler.java): инвалидация сессии, установка cookie `SESSION` с `Max-Age=0`, возврат `{"status":"logged_out"}` для XHR/fetch-запросов и редирект 302 на `/` для браузеров.
  - [`CustomAuthenticationEntryPoint.java`](file:///d:/Max/Projects/Smart_Rent_Hub/services/gateway/src/main/java/team/ylab/gateway/security/CustomAuthenticationEntryPoint.java): для запросов к `/api/**` возвращает RFC 9457 Problem Details (HTTP 401), а для браузерных запросов инициирует 302 Redirect на вход.
  - [`RedisSessionFailureWebExceptionHandler.java`](file:///d:/Max/Projects/Smart_Rent_Hub/services/gateway/src/main/java/team/ylab/gateway/exception/RedisSessionFailureWebExceptionHandler.java): глобальный реактивный перехватчик сбоев Redis, удаляющий битую cookie и отправляющий пользователя на вход вместо сбоя с HTTP 500.
  - [`RateLimitWebExceptionHandler.java`](file:///d:/Max/Projects/Smart_Rent_Hub/services/gateway/src/main/java/team/ylab/gateway/exception/RateLimitWebExceptionHandler.java): реактивный перехватчик с кодом 429, формирующий RFC 9457 Problem Details (`urn:problem:rate-limit:exceeded`) с сохранением всех заголовков `X-RateLimit-*`.
  - [`SrhRateLimitKeyResolver.java`](file:///d:/Max/Projects/Smart_Rent_Hub/services/gateway/src/main/java/team/ylab/gateway/ratelimit/SrhRateLimitKeyResolver.java): многоуровневый определитель ключа ограничения скорости (1: аутентифицированный пользователь `user:`, 2: активная сессия `session:`, 3: клиентский IP `ip:` через `X-Forwarded-For` или `RemoteAddress`).
  - [`GatewayRateLimiterConfig.java`](file:///d:/Max/Projects/Smart_Rent_Hub/services/gateway/src/main/java/team/ylab/gateway/config/GatewayRateLimiterConfig.java): конфигурация бина `RedisRateLimiter` с параметрами токен-бакета.
  - [`SessionController.java`](file:///d:/Max/Projects/Smart_Rent_Hub/services/gateway/src/main/java/team/ylab/gateway/session/SessionController.java): эндпоинт `GET /api/v1/session`, извлекающий `username`, роли `realm_access.roles`, время действия сессии, а также `csrfToken` и `csrfHeaderName` без раскрытия секретных токенов.
  - [`GatewayRoutingConfig.java`](file:///d:/Max/Projects/Smart_Rent_Hub/services/gateway/src/main/java/team/ylab/gateway/config/GatewayRoutingConfig.java): включен фильтр `.filters(f -> f.tokenRelay().requestRateLimiter(...))` для маршрута `inventory-api`.
- Разработана тестовая инфраструктура и тестовые наборы:
  - [`KeycloakMockServer.java`](file:///d:/Max/Projects/Smart_Rent_Hub/services/gateway/src/test/java/team/ylab/gateway/test/KeycloakMockServer.java): реактивный OIDC-сервер с RSA-2048 ключами, JWKS endpoint `/certs`, генерацией подписанных JWT access/id-токенов и `/userinfo`.
  - [`TestSessionHelper.java`](file:///d:/Max/Projects/Smart_Rent_Hub/services/gateway/src/test/java/team/ylab/gateway/test/TestSessionHelper.java): генератор валидных предзаполненных `WebSession` в Redis.
  - [`GatewayOAuth2BffIntegrationTest.java`](file:///d:/Max/Projects/Smart_Rent_Hub/services/gateway/src/test/java/team/ylab/gateway/GatewayOAuth2BffIntegrationTest.java): 10 сквозных тестов OAuth2 BFF, Redis-сессий и TokenRelay.
  - [`GatewayCsrfAndRateLimitIntegrationTest.java`](file:///d:/Max/Projects/Smart_Rent_Hub/services/gateway/src/test/java/team/ylab/gateway/GatewayCsrfAndRateLimitIntegrationTest.java): 12 сквозных тестов CSRF double-submit cookie, Origin/Referer валидации, Redis rate limiting и Fail-Open.

---

## Результат по критериям

| Критерий | Результат | Детали и подтверждение |
|---|---|---|
| **(a) Вход Keycloak (две регистрации)** | **ДА (Успешно)** | Регистрации `srh-customer` и `srh-staff` изолированы. Для `/oauth2/authorization/{client}` Gateway перенаправляет на корректный Keycloak `authorization-uri`. Персонал после логина перенаправляется на `/staff`, покупатель — на `/`. |
| **(b) Серверная сессия в Redis** | **ДА (Успешно)** | В ответе браузеру возвращается cookie `SESSION` (`SameSite=Lax; HttpOnly; Path=/`). Токены (`access_token`, `refresh_token`) сохраняются строго в Redis (`spring:session:sessions:...`), браузер не имеет к ним прямого доступа. |
| **(c) TokenRelay к микросервисам** | **ДА (Успешно)** | При запросе к `/api/v1/catalog/**` с валидной cookie `SESSION` Gateway извлекает `OAuth2AuthorizedClient` из сессии и инжектирует заголовок `Authorization: Bearer <accessToken>` в downstream-запрос к сервису `inventory`. |
| **(d) Защита от утечки токенов** | **ДА (Успешно)** | Эндпоинты `/api/v1/session` и downstream-ответы возвращают только пользовательские данные (логин, realm-роли, время действия) без включения заголовков токенов или тела JWT. |
| **(e) Single Logout (`/logout`)** | **ДА (Успешно)** | Вызов `POST /logout` аннулирует сессию в Redis (`sessionRepository.deleteById`), сбрасывает cookie `SESSION` с `Max-Age=0`. Последующий запрос к защищенному API с той же cookie возвращает 401. |
| **(f) Обработка отказа Redis** | **ДА (Успешно)** | При симуляции сбоя Redis (`RedisSessionFailureWebExceptionHandler`) Gateway не падает с HTTP 500, а инвалидирует клиентскую cookie и возвращает 401 (для API) или 302 Redirect на логин (для браузеров). |
| **(g) RFC 9457 для неавторизованных API-запросов** | **ДА (Успешно)** | Запросы без cookie к `/api/v1/**` получают `401 Unauthorized` со структурированным телом Problem Details (`type`, `title`, `status`, `detail`, `instance`). Браузерные запросы к `/staff/**` получают 302 Redirect. |
| **(h) Double-Submit Cookie CSRF** | **ДА (Успешно)** | Безопасные запросы (GET) отдают cookie `XSRF-TOKEN` (`HttpOnly=false`, `SameSite=Lax`) и значение в `GET /api/v1/session`. Мутирующие запросы (POST, PUT, DELETE, PATCH) к `/api/**` без токена или с несовпадающим токеном отклоняются с 403 Problem Details (`title: Forbidden`). Запросы с совпадающим заголовком (`X-XSRF-TOKEN` или `X-CSRF-TOKEN`) успешно авторизуются. |
| **(i) Валидация Origin и Referer** | **ДА (Успешно)** | Мутирующие запросы с `Origin` или `Referer`, не входящими в доверенный список (`allowed-origins` или собственный Host шлюза), немедленно блокируются фильтром `OriginValidationWebFilter` с HTTP 403 (`type: urn:problem:csrf:forbidden-origin`). |
| **(j) Redis Rate Limiting (Token Bucket)** | **ДА (Успешно)** | Запросы к `inventory-api` ограничиваются токен-бакетом. Превышение лимита возвращает 429 Too Many Requests с телом RFC 9457 и полным набором заголовков `X-RateLimit-Remaining: 0`, `X-RateLimit-Replenish-Rate`, `X-RateLimit-Burst-Capacity`. |
| **(k) Многоуровневый KeyResolver** | **ДА (Успешно)** | Изоляция лимитов: `user:<username>` для аутентифицированных, `session:<id>` для пользователей с сессией, `ip:<clientIp>` для анонимных обращений (с поддержкой `X-Forwarded-For`). |
| **(l) Rate Limiter Fail-Open** | **ДА (Успешно)** | При отказе соединения с Redis или ошибке выполнения Lua-скрипта `RedisRateLimiter` переходит в fail-open режим (`allowed=true`), предотвращая деградацию доступности downstream-сервисов. |

---

## Проблемы и обходные пути

1. **Eager OpenID Connect Discovery в Spring Boot 4.1.0:**
   - *Проблема:* Если в `application.yml` указан `issuer-uri`, автоконфигурация Spring Boot `OAuth2ClientPropertiesMapper` синхронно вызывает `ClientRegistrations.fromIssuerLocation()` во время поднятия контекста Spring. Если Keycloak временно недоступен или поднимается параллельно, Gateway падает с фатальной ошибкой `IllegalArgumentException: Unable to resolve Configuration with the provided Issuer`.
   - *Решение:* В `application.yml` для обеих регистраций заданы явные статические эндпоинты (`authorization-uri`, `token-uri`, `jwk-set-uri`, `user-info-uri`). Это делает старт шлюза детерминированным и устойчивым к порядку запуска контейнеров в Docker Compose и Kubernetes.

2. **Перемещение классов в Spring Security 7.1.0:**
   - *Проблема:* Класс `DefaultReactiveOAuth2AuthorizedClientManager` перенесен из пакета `org.springframework.security.oauth2.client` в пакет `org.springframework.security.oauth2.client.web`.
   - *Решение:* Скорректированы импорты в `GatewaySecurityConfig.java`.

3. **Асинхронное закрытие спанов OpenTelemetry в Reactor Netty:**
   - *Проблема:* В интеграционных тестах WebTestClient завершает HTTP-обмен до того, как реактивная цепочка фильтра `TraceContextWebFilter` выполнит завершающий хук `doFinally` и передаст законченный спан в `InMemorySpanExporter`. Непосредственная проверка `getFinishedSpanItems()` сразу после запроса приводила к спорадическим сбоям из-за гонки потоков.
   - *Решение:* Проверки экспорта спанов OTel обернуты в `Awaitility.await().atMost(Durations.TWO_SECONDS).untilAsserted(...)`, гарантируя 100% стабильность тестов без флакающих падений.

4. **Особый `accessDeniedHandler` в `ServerHttpSecurity$CsrfSpec`:**
   - *Проблема:* Spring Security WebFlux содержит отдельное поле `accessDeniedHandler` внутри `CsrfWebFilter`, которое по умолчанию инициализируется `HttpStatusServerAccessDeniedHandler(HttpStatus.FORBIDDEN)`, возвращающим `text/plain`. Глобальный `exceptionHandling().accessDeniedHandler(...)` не подставляется в `CsrfWebFilter` автоматически.
   - *Решение:* Явно зарегистрирован `.accessDeniedHandler(new CustomAccessDeniedHandler())` внутри лямбды `.csrf(csrf -> ...)`.

5. **Исключение `DefaultCsrfToken` при пустом токене:**
   - *Проблема:* Конструктор `DefaultCsrfToken` валидирует `Assert.hasLength(token, "token cannot be null or empty")`. Попытка инициализации константы с пустой строкой приводит к `ExceptionInInitializerError` при старте `SessionController`.
   - *Решение:* Использование `Optional<CsrfToken>` с безопасной проверкой `Optional::isPresent` без создания фиктивных пустых токенов.

---

## Решение

1. **Утверждаем связку Spring Cloud Gateway + Spring Security 7.1 Reactive + Spring Session Redis** как стандартную реализацию BFF для Smart Rent Hub.
2. **Архитектурные требования для SDD / ADR:**
   - Доступ фронтенд-приложений (`customer-web`, `staff-web`) к API разрешен строго через единый Origin Gateway с передачей сессионной cookie `SESSION`. Прямое хранение JWT в `localStorage` или cookie браузера запрещено.
   - Внутренние микросервисы (`inventory`, `booking` и др.) настраиваются как OAuth2 Resource Server и проверяют Bearer JWT, проброшенный Gateway через фильтр `TokenRelay`.
   - Все мутирующие API-запросы (`POST`, `PUT`, `DELETE`, `PATCH` на `/api/**`) защищаются по схеме Double-Submit Cookie: клиент передает токен из cookie `XSRF-TOKEN` в заголовке `X-XSRF-TOKEN` (или `X-CSRF-TOKEN`).
   - Заголовки `Origin` и `Referer` валидируются шлюзом перед пропуском мутирующих запросов. Запросы с недоверенным Origin отклоняются с HTTP 403 Problem Details.
   - Rate Limiting на Gateway использует алгоритм Token Bucket в Redis с многоуровневым ключом (`user:`, `session:`, `ip:`) и политикой Fail-Open при сбоях Redis.
   - Все ошибки шлюза (401 Unauthorized, 403 Forbidden, 429 Too Many Requests, 500 Failure) возвращаются строго в формате RFC 9457 `application/problem+json` с включением `traceId`.

---

## Что остаётся неясным

- Поддержка WebSocket и SSE-потоков по cookie сессии с валидацией `Origin` (будет исследована и реализована в задаче T3.4).
