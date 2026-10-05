# S-1. Интеграция Operaton 2.1.x с Spring Boot 4.1.0

Дата: 05.10.2026  
Версии компонентов:
- **Java:** 21 (OpenJDK 64-Bit Server VM, vendor: JetBrains s.r.o., runtime 21.0.9)
- **Spring Boot:** 4.1.0 (Spring Framework 7.0.8, Spring Data JPA 4.1.0, Spring TX 7.0.8)
- **Operaton:** 2.1.0 (`org.operaton.bpm.springboot:operaton-bpm-spring-boot-starter:2.1.0`)
- **Hibernate ORM:** 7.4.1.Final
- **PostgreSQL JDBC:** 42.7.11
- **PostgreSQL Database:** 16-alpine (via Testcontainers 2.0.5 / 1.20.1)
- **HikariCP:** 7.0.2

---

## Вопрос
1. Совместим ли движок бизнес-процессов **Operaton 2.1.x** (community-fork Camunda 7) со стеком **Spring Boot 4.1.0** и **Java 21**?
2. Обеспечивается ли транзакционная атомарность (ACID rollback) между Spring Data JPA и процессом Operaton при выбросе `RuntimeException` в `JavaDelegate`?
3. Корректно ли функционирует изоляция в отдельной схеме PostgreSQL (`bpm`) для таблиц движка (`act_*`) и сущностей JPA (`spike_entity`)?
4. Как движок обрабатывает асинхронные продолжения (`operaton:asyncBefore="true"`), корреляцию сообщений по `businessKey`, гонки (race conditions) при параллельной отправке сообщений и события таймера (`PT30S`) на Event-Based Gateway?
5. Каковы показатели времени старта и потребления оперативной памяти?

---

## Что делали
- Создана ветка [`spike/S-1-operaton-boot4`](https://github.com/MaksimLM17/Smart_Rent_Hub/tree/spike/S-1-operaton-boot4).
- В корневой [`pom.xml`](file:///d:/Max/Projects/Smart_Rent_Hub/pom.xml) добавлен модуль `spikes/s1-operaton`.
- Создан модуль [`spikes/s1-operaton`](file:///d:/Max/Projects/Smart_Rent_Hub/spikes/s1-operaton/pom.xml) с наследованием от родительского POM `team.ylab:smart-rent-hub:0.0.1-SNAPSHOT`.
- Спроектирован BPMN-процесс [`checkout-mini.bpmn`](file:///d:/Max/Projects/Smart_Rent_Hub/spikes/s1-operaton/src/main/resources/processes/checkout-mini.bpmn):
  - Start Event
  - Service Task A с `operaton:asyncBefore="true"`, delegate expression `${taskADelegate}`
  - Event-Based Gateway
  - Два параллельных события ожидания: промежуточное сообщение `msg_x` и промежуточный таймер `PT30S`
  - Объединяющий Exclusive Gateway
  - Service Task B с delegate expression `${taskBDelegate}`
  - End Event
- Создан дескриптор развертывания [`META-INF/processes.xml`](file:///d:/Max/Projects/Smart_Rent_Hub/spikes/s1-operaton/src/main/resources/META-INF/processes.xml).
- Реализованы Java-компоненты:
  - [`SpikeApplication.java`](file:///d:/Max/Projects/Smart_Rent_Hub/spikes/s1-operaton/src/main/java/team/ylab/spikes/s1/SpikeApplication.java): `@SpringBootApplication`, `@EnableProcessApplication`.
  - [`SpikeEntity.java`](file:///d:/Max/Projects/Smart_Rent_Hub/spikes/s1-operaton/src/main/java/team/ylab/spikes/s1/entity/SpikeEntity.java): JPA-сущность в схеме `bpm`, таблица `spike_entity`.
  - [`SpikeRepository.java`](file:///d:/Max/Projects/Smart_Rent_Hub/spikes/s1-operaton/src/main/java/team/ylab/spikes/s1/repository/SpikeRepository.java): Spring Data JPA репозиторий.
  - [`TaskADelegate.java`](file:///d:/Max/Projects/Smart_Rent_Hub/spikes/s1-operaton/src/main/java/team/ylab/spikes/s1/delegate/TaskADelegate.java): сохраняет сущность со статусом `TASK_A_COMPLETED`.
  - [`TaskBDelegate.java`](file:///d:/Max/Projects/Smart_Rent_Hub/spikes/s1-operaton/src/main/java/team/ylab/spikes/s1/delegate/TaskBDelegate.java): обновляет сущность в `TASK_B_COMPLETED`; при флаге `simulateError == true` выбрасывает исключение `RuntimeException`.
- Написан комплексный интеграционный тест [`SpikeIntegrationTest.java`](file:///d:/Max/Projects/Smart_Rent_Hub/spikes/s1-operaton/src/test/java/team/ylab/spikes/s1/SpikeIntegrationTest.java) с использованием Testcontainers PostgreSQL 16.

---

## Результат по критериям

| Критерий | Результат | Детали и подтверждение |
|---|---|---|
| **(a) Job Executor (`asyncBefore`)** | **ДА (Успешно)** | При старте процесса Service Task A не исполняется синхронно — создается Job в `act_ru_job`. До исполнения джоба сущность в БД отсутствует. При вызове `managementService.executeJob(...)` задача выполняется, сущность сохраняется с `TASK_A_COMPLETED`, токен переходит на Event-Based Gateway. |
| **(b) Атомарность JPA + Operaton** | **ДА (Успешно)** | При ошибке в Task B транзакция откатывается полностью: JPA-сущность сохраняет статус `TASK_A_COMPLETED` (изменения на `TASK_B_COMPLETED` откатаны), а токен процесса Operaton остается на Event-Based Gateway с активной подпиской на `msg_x`. При повторной корреляции без ошибки процесс завершается успешно (`TASK_B_COMPLETED`). |
| **(c) Корреляция по businessKey** | **ДА (Успешно)** | Корреляция сообщения `msg_x` по `processInstanceBusinessKey` строго адресует нужный экземпляр процесса, не затрагивая параллельно запущенные процессы с другими businessKey. |
| **(d) Обработка Race Conditions** | **ДА (Успешно)** | При одновременной отправке сообщения `msg_x` из 4 параллельных потоков ровно 1 поток успешно проводит корреляцию, 3 остальных потока получают `MismatchingMessageCorrelationException` / `OptimisticLockingException`. Повторная отправка завершенному процессу гарантированно вызывает `MismatchingMessageCorrelationException`. |
| **(e) Срабатывание таймера** | **ДА (Успешно)** | Создается таймер-джоб на 30 секунд (`PT30S`). При программном триггере джоба процесс уходит по ветке таймера через объединяющий шлюз в Task B и завершается. |
| **(f) Изоляция схемы PostgreSQL (`bpm`)** | **ДА (Успешно)** | Через JDBC metadata / `information_schema.tables` подтверждено, что таблица `spike_entity` и 49 таблиц Operaton (`act_ru_*`, `act_ge_*`, `act_re_*`, `act_hi_*`, `act_id_*`) созданы строго в схеме `bpm`. Схема `public` чиста. |
| **(g) Метрики производительности** | **ДА (Успешно)** | • **Context Startup:** 7.75 с (включая инициализацию DDL Operaton и Hibernate)<br>• **JVM Uptime:** 13.8 с<br>• **Total Heap:** 288 MB<br>• **Used Heap:** 110 MB<br>• **Free Heap:** 178 MB<br>• **Время выполнения всех 7 тестов:** 14.54 с |

---

## Проблемы и обходные пути

1. **Обязательный History Time To Live (TTL):**
   - *Проблема:* Operaton 2.1.0 строго проверяет наличие TTL у процессов (`ENGINE-12018 History Time To Live (TTL) cannot be null`). При отсутствии процесс отклоняется на этапе парсинга.
   - *Решение:* В корневом элементе BPMN `<bpmn:process>` задан атрибут `operaton:historyTimeToLive="P30D"`, а также в `application.yml` прописан глобальный параметр по умолчанию `operaton.bpm.generic-properties.properties.historyTimeToLive: P30D`.

2. **Отключение стандартного авто-деплоя при `@EnableProcessApplication`:**
   - *Проблема:* Аннотация `@EnableProcessApplication` переключает движок на использование дескриптора `processes.xml` и отключает Spring Boot авто-сканирование classpath. Если `processes.xml` пустой без `<process-archive>`, процессы не регистрируются.
   - *Решение:* В `src/main/resources/META-INF/processes.xml` объявлен архив `<process-archive name="default">` с `<property name="isScanForProcessDefinitions">true</property>`. В интеграционном тесте добавлена дополнительная проверка и инициализация через `RepositoryService`.

3. **Конкуренция фонового потока Job Executor в тестах:**
   - *Проблема:* При `operaton.bpm.job-execution.enabled: true` фоновый поток воркера мгновенно забирает асинхронные джобы, что делает проверку промежуточного состояния (wait-state до запуска джоба) недетерминированной.
   - *Решение:* Для интеграционных тестов в `application.yml` установлено `operaton.bpm.job-execution.enabled: false`. Выполнение асинхронных джобов и таймеров контролируется явно через `managementService.executeJob(...)`.

---

## Решение
1. **Принимаем стек Operaton 2.1.0 на Spring Boot 4.1.0** в качестве основного движка оркестрации бизнес-процессов и саг в Smart Rent Hub (включая сервис Checkout / Orders).
2. **Архитектурные требования для SDD / ADR:**
   - Все BPMN-процессы обязаны содержать `operaton:historyTimeToLive="P<N>D"` в соответствии с политикой хранения истории.
   - Таблицы движка и сущности саг размещаются в схеме `bpm` базы данных PostgreSQL с указанием `currentSchema=bpm` в JDBC URL.
   - Взаимодействие Spring Data JPA и Operaton полностью покрывается распределенной транзакцией на уровне Spring `JpaTransactionManager` (Spring Transaction + Operaton `SpringTransactionInterceptor`). Дополнительный 2PC/XA не требуется для локальной БД.

---

## Что остаётся неясным
- Поведение Job Executor при масштабировании на несколько экземпляров подов в Kubernetes при высокой конкуренции за блокировки `ACT_RU_JOB` (будет исследовано в рамках нагрузочных испытаний S-3).
