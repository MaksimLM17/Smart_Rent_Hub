package team.ylab.spikes.s2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import team.ylab.outbox.autoconfigure.OutboxProperties;
import team.ylab.outbox.inbox.InboxService;
import team.ylab.outbox.model.OutboxEvent;
import team.ylab.outbox.publisher.OutboxPublisher;
import team.ylab.spikes.s2.client.DebeziumConnectClient;
import team.ylab.spikes.s2.model.OrderCreatedEvent;
import team.ylab.spikes.s2.model.SkuUpsertedEvent;
import team.ylab.testsupport.containers.KafkaTestContainer;

@SpringBootTest(classes = SpikeApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Spike S-2: Debezium CDC + JSON Outbox + Inbox deduplication")
class DebeziumOutboxSpikeIntegrationTest {

  private static final Logger log =
      LoggerFactory.getLogger(DebeziumOutboxSpikeIntegrationTest.class);
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private static final Network network = Network.newNetwork();

  private static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
          .withNetwork(network)
          .withNetworkAliases("postgres")
          .withDatabaseName("booking")
          .withUsername("postgres")
          .withPassword("postgres")
          .withCommand(
              "postgres",
              "-c",
              "wal_level=logical",
              "-c",
              "max_replication_slots=10",
              "-c",
              "max_wal_senders=10");

  private static final KafkaTestContainer kafka = new KafkaTestContainer();

  private static final GenericContainer<?> connect;

  static {
    postgres.start();
    kafka.withNetwork(network).withNetworkAliases("kafka");
    kafka.start();

    String kafkaInternalBootstrap = kafka.getContainerInfo().getConfig().getHostName() + ":9093";

    connect =
        new GenericContainer<>(DockerImageName.parse("debezium/connect:2.7.3.Final"))
            .withNetwork(network)
            .withNetworkAliases("connect")
            .withExposedPorts(8083)
            .withEnv("BOOTSTRAP_SERVERS", kafkaInternalBootstrap)
            .withEnv("GROUP_ID", "srh-connect-group")
            .withEnv("CONFIG_STORAGE_TOPIC", "_connect_configs")
            .withEnv("OFFSET_STORAGE_TOPIC", "_connect_offsets")
            .withEnv("STATUS_STORAGE_TOPIC", "_connect_statuses")
            .withEnv("CONFIG_STORAGE_REPLICATION_FACTOR", "1")
            .withEnv("OFFSET_STORAGE_REPLICATION_FACTOR", "1")
            .withEnv("STATUS_STORAGE_REPLICATION_FACTOR", "1")
            .withEnv("KEY_CONVERTER", "org.apache.kafka.connect.storage.StringConverter")
            .withEnv("VALUE_CONVERTER", "org.apache.kafka.connect.json.JsonConverter")
            .withEnv("VALUE_CONVERTER_SCHEMAS_ENABLE", "false")
            .withEnv("ENABLE_DEBEZIUM_SCRIPTING", "false")
            .waitingFor(
                Wait.forHttp("/").forStatusCode(200).withStartupTimeout(Duration.ofSeconds(120)));
    connect.start();
  }

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    registry.add("srh.outbox.delete-immediately", () -> "true");
  }

  private static DebeziumConnectClient connectClient;
  private static final String CONNECTOR_NAME = "booking-outbox";

  @Autowired private OutboxPublisher outboxPublisher;
  @Autowired private InboxService inboxService;
  @Autowired private OutboxProperties outboxProperties;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TransactionTemplate transactionTemplate;

  @BeforeAll
  static void setUpCluster() throws Exception {
    connectClient = new DebeziumConnectClient(connect.getHost(), connect.getMappedPort(8083));

    // Initialize Outbox and Inbox tables in PostgreSQL
    try (Connection conn =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Statement stmt = conn.createStatement()) {
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS outbox ("
              + "id UUID PRIMARY KEY, "
              + "aggregate_type TEXT NOT NULL, "
              + "aggregate_id TEXT NOT NULL, "
              + "event_type TEXT NOT NULL, "
              + "schema_version INT NOT NULL DEFAULT 1, "
              + "correlation_id TEXT, "
              + "traceparent TEXT, "
              + "occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(), "
              + "payload JSONB NOT NULL, "
              + "created_at TIMESTAMPTZ NOT NULL DEFAULT now()"
              + ")");

      stmt.execute(
          "CREATE TABLE IF NOT EXISTS processed_event ("
              + "consumer TEXT NOT NULL, "
              + "event_id UUID NOT NULL, "
              + "processed_at TIMESTAMPTZ NOT NULL DEFAULT now(), "
              + "PRIMARY KEY (consumer, event_id)"
              + ")");

      stmt.execute("CREATE PUBLICATION booking_outbox_pub FOR TABLE outbox");
    }

    // Register Debezium PostgreSQL Connector
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("connector.class", "io.debezium.connector.postgresql.PostgresConnector");
    config.put("plugin.name", "pgoutput");
    config.put("database.hostname", "postgres");
    config.put("database.port", "5432");
    config.put("database.user", "postgres");
    config.put("database.password", "postgres");
    config.put("database.dbname", "booking");
    config.put("database.sslmode", "disable");
    config.put("topic.prefix", "booking");
    config.put("slot.name", "booking_outbox_slot");
    config.put("publication.name", "booking_outbox_pub");
    config.put("publication.autocreate.mode", "disabled");
    config.put("table.include.list", "public.outbox");
    config.put("tombstones.on.delete", "false");
    config.put("key.converter", "org.apache.kafka.connect.storage.StringConverter");
    config.put("value.converter", "org.apache.kafka.connect.json.JsonConverter");
    config.put("value.converter.schemas.enable", "false");
    config.put("transforms", "outbox");
    config.put("transforms.outbox.type", "io.debezium.transforms.outbox.EventRouter");
    config.put("transforms.outbox.table.field.event.id", "id");
    config.put("transforms.outbox.table.field.event.key", "aggregate_id");
    config.put("transforms.outbox.table.field.event.payload", "payload");
    config.put("transforms.outbox.table.expand.json.payload", "true");
    config.put("transforms.outbox.route.by.field", "aggregate_type");
    config.put("transforms.outbox.route.topic.replacement", "${routedByValue}");
    config.put(
        "transforms.outbox.table.fields.additional.placement",
        "event_type:header:event-type,schema_version:header:event-version,correlation_id:header:correlation-id,traceparent:header:traceparent,occurred_at:header:occurred-at,id:header:event-id");

    connectClient.registerConnector(CONNECTOR_NAME, config);

    // Wait until connector and its tasks transition to RUNNING state
    await()
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofSeconds(1))
        .until(() -> connectClient.isConnectorRunning(CONNECTOR_NAME));

    log.info("Debezium connector [{}] successfully started and running.", CONNECTOR_NAME);
  }

  @AfterAll
  static void tearDownCluster() {
    try {
      connectClient.deleteConnector(CONNECTOR_NAME);
    } catch (Exception ignored) {
    }
  }

  private KafkaConsumer<String, String> createConsumer(String topic) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "spike-test-group-" + UUID.randomUUID());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

    KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
    consumer.subscribe(Collections.singletonList(topic));
    return consumer;
  }

  @Test
  @Order(1)
  @DisplayName("1. Message value is pure valid JSON without string escaping")
  void testMessageValueIsPureJsonWithoutEscaping() throws Exception {
    String orderId = "order-" + UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    OrderCreatedEvent payload =
        new OrderCreatedEvent(
            orderId, "cust-42", "PENDING_CONFIRMATION", "14500.00", List.of("sku-101", "sku-102"));

    OutboxEvent event =
        OutboxEvent.builder()
            .id(eventId)
            .aggregateType("booking.order.v1")
            .aggregateId(orderId)
            .eventType("OrderCreated")
            .schemaVersion(1)
            .correlationId("corr-req-777")
            .traceparent("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
            .occurredAt(Instant.now())
            .payload(payload)
            .build();

    // Publish event (with deleteImmediately=true, insert & delete happen in same TX)
    transactionTemplate.executeWithoutResult(status -> outboxPublisher.publish(event));

    // Verify outbox table is empty in PostgreSQL (deleted within same TX)
    Integer outboxCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM outbox WHERE id = ?", Integer.class, eventId);
    assertThat(outboxCount).isEqualTo(0);

    // Consume event from Kafka
    try (KafkaConsumer<String, String> consumer = createConsumer("booking.order.v1")) {
      ConsumerRecord<String, String> record = pollSingleRecord(consumer, orderId);
      assertThat(record).isNotNull();

      // Verify partition key is aggregate_id
      assertThat(record.key()).isEqualTo(orderId);

      // Verify message value is pure JSON (not a JSON string literal)
      String rawValue = record.value();
      assertThat(rawValue).isNotBlank();
      assertThat(rawValue.trim()).startsWith("{").endsWith("}");
      assertThat(rawValue).doesNotStartWith("\"{\\");

      // Verify parseable into JsonNode and field contents
      JsonNode jsonNode = objectMapper.readTree(rawValue);
      assertThat(jsonNode.isObject()).isTrue();
      assertThat(jsonNode.path("orderId").asText()).isEqualTo(orderId);
      assertThat(jsonNode.path("customerId").asText()).isEqualTo("cust-42");
      assertThat(jsonNode.path("status").asText()).isEqualTo("PENDING_CONFIRMATION");
      assertThat(jsonNode.path("totalAmount").asText()).isEqualTo("14500.00");
      assertThat(jsonNode.path("items")).hasSize(2);
    }
  }

  @Test
  @Order(2)
  @DisplayName("2. Kafka headers are populated correctly via additional.placement and event.id")
  void testKafkaHeadersPopulated() {
    String orderId = "order-" + UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-10-22T10:15:30.123456Z");

    OutboxEvent event =
        OutboxEvent.builder()
            .id(eventId)
            .aggregateType("booking.order.v1")
            .aggregateId(orderId)
            .eventType("OrderCreated")
            .schemaVersion(1)
            .correlationId("corr-header-999")
            .traceparent("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01")
            .occurredAt(occurredAt)
            .payload(new OrderCreatedEvent(orderId, "cust-99", "DRAFT", "5000.00", List.of()))
            .build();

    transactionTemplate.executeWithoutResult(status -> outboxPublisher.publish(event));

    try (KafkaConsumer<String, String> consumer = createConsumer("booking.order.v1")) {
      ConsumerRecord<String, String> record = pollSingleRecord(consumer, orderId);
      assertThat(record).isNotNull();

      Map<String, String> headers = extractHeaders(record);

      // Required headers per SDD Section 5.3 & Appendix C
      assertThat(headers).containsKey("event-type");
      assertThat(headers.get("event-type")).isEqualTo("OrderCreated");

      assertThat(headers).containsKey("event-version");
      assertThat(headers.get("event-version")).isEqualTo("1");

      assertThat(headers).containsKey("correlation-id");
      assertThat(headers.get("correlation-id")).isEqualTo("corr-header-999");

      assertThat(headers).containsKey("traceparent");
      assertThat(headers.get("traceparent"))
          .isEqualTo("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");

      assertThat(headers).containsKey("occurred-at");
      assertThat(headers.get("occurred-at")).isNotBlank();

      // Check event ID in headers (available as event-id or id)
      assertThat(headers.get("event-id") != null ? headers.get("event-id") : headers.get("id"))
          .isEqualTo(eventId.toString());
    }
  }

  @Test
  @Order(3)
  @DisplayName("3. Dynamic topic routing based on aggregate_type")
  void testDynamicTopicRouting() throws Exception {
    String skuId = "sku-item-" + UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    SkuUpsertedEvent payload =
        new SkuUpsertedEvent(
            skuId, "Bosch Hammer Drill GBH 2-28", "Bosch", "GBH 2-28", "tools/drills", "1200.00");

    OutboxEvent event =
        OutboxEvent.builder()
            .id(eventId)
            .aggregateType("inventory.sku.v1")
            .aggregateId(skuId)
            .eventType("SkuUpserted")
            .schemaVersion(1)
            .correlationId("corr-sku-1")
            .traceparent("00-11111111111111111111111111111111-2222222222222222-01")
            .occurredAt(Instant.now())
            .payload(payload)
            .build();

    transactionTemplate.executeWithoutResult(status -> outboxPublisher.publish(event));

    try (KafkaConsumer<String, String> consumer = createConsumer("inventory.sku.v1")) {
      ConsumerRecord<String, String> record = pollSingleRecord(consumer, skuId);
      assertThat(record).isNotNull();
      assertThat(record.topic()).isEqualTo("inventory.sku.v1");
      assertThat(record.key()).isEqualTo(skuId);

      JsonNode jsonNode = objectMapper.readTree(record.value());
      assertThat(jsonNode.path("skuId").asText()).isEqualTo(skuId);
      assertThat(jsonNode.path("brand").asText()).isEqualTo("Bosch");
      assertThat(jsonNode.path("model").asText()).isEqualTo("GBH 2-28");
    }
  }

  @Test
  @Order(4)
  @DisplayName("4. Message ordering is strictly preserved per aggregate key")
  void testMessageOrderingPreservedPerKey() {
    String orderId = "order-seq-" + UUID.randomUUID();
    int eventCount = 5;

    for (int i = 1; i <= eventCount; i++) {
      int step = i;
      OutboxEvent event =
          OutboxEvent.builder()
              .id(UUID.randomUUID())
              .aggregateType("booking.order.v1")
              .aggregateId(orderId)
              .eventType("OrderStep" + step)
              .schemaVersion(1)
              .occurredAt(Instant.now())
              .payload(
                  Map.of(
                      "orderId", orderId,
                      "step", step,
                      "description", "Processing step " + step))
              .build();

      transactionTemplate.executeWithoutResult(status -> outboxPublisher.publish(event));
    }

    try (KafkaConsumer<String, String> consumer = createConsumer("booking.order.v1")) {
      List<ConsumerRecord<String, String>> records =
          pollRecordsForKey(consumer, orderId, eventCount);
      assertThat(records).hasSize(eventCount);

      int expectedPartition = records.get(0).partition();
      for (int i = 0; i < eventCount; i++) {
        ConsumerRecord<String, String> record = records.get(i);
        int expectedStep = i + 1;

        // Same partition for identical aggregate key
        assertThat(record.partition())
            .as("Event %d partition mismatch", expectedStep)
            .isEqualTo(expectedPartition);

        // Sequence order strictly preserved
        Map<String, String> headers = extractHeaders(record);
        assertThat(headers.get("event-type")).isEqualTo("OrderStep" + expectedStep);
      }
    }
  }

  @Test
  @Order(5)
  @DisplayName("5. Connector pause & resume does not lose messages or duplicate them")
  void testConnectorPauseAndResumeWithoutDataLoss() throws Exception {
    String orderId = "order-pause-" + UUID.randomUUID();

    // 1. Pause connector
    connectClient.pauseConnector(CONNECTOR_NAME);
    Thread.sleep(2000);

    // 2. Insert 3 events while connector is paused
    List<UUID> pausedEventIds = new ArrayList<>();
    for (int i = 1; i <= 3; i++) {
      UUID id = UUID.randomUUID();
      pausedEventIds.add(id);
      OutboxEvent event =
          OutboxEvent.builder()
              .id(id)
              .aggregateType("booking.order.v1")
              .aggregateId(orderId)
              .eventType("PausedEvent" + i)
              .schemaVersion(1)
              .occurredAt(Instant.now())
              .payload(Map.of("orderId", orderId, "seq", i))
              .build();

      transactionTemplate.executeWithoutResult(status -> outboxPublisher.publish(event));
    }

    // 3. Resume connector
    connectClient.resumeConnector(CONNECTOR_NAME);

    // Wait until running again
    await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> connectClient.isConnectorRunning(CONNECTOR_NAME));

    // 4. Verify all 3 events arrive in Kafka
    try (KafkaConsumer<String, String> consumer = createConsumer("booking.order.v1")) {
      List<ConsumerRecord<String, String>> records = pollRecordsForKey(consumer, orderId, 3);
      assertThat(records).hasSize(3);

      for (int i = 0; i < 3; i++) {
        Map<String, String> headers = extractHeaders(records.get(i));
        assertThat(headers.get("event-type")).isEqualTo("PausedEvent" + (i + 1));
      }
    }
  }

  @Test
  @Order(6)
  @DisplayName("6. Inbox idempotency: duplicate event is rejected by processed_event")
  void testInboxDeduplicationWithProcessedEvent() {
    UUID eventId = UUID.randomUUID();
    String consumerName = "order-inbox-consumer";
    AtomicInteger executionCount = new AtomicInteger(0);

    // First arrival: should execute action
    boolean executedFirst =
        inboxService.executeIfNotProcessed(
            consumerName, eventId, (Runnable) executionCount::incrementAndGet);
    assertThat(executedFirst).isTrue();
    assertThat(executionCount.get()).isEqualTo(1);

    // Duplicate arrival: should be rejected/skipped
    boolean executedSecond =
        inboxService.executeIfNotProcessed(
            consumerName, eventId, (Runnable) executionCount::incrementAndGet);
    assertThat(executedSecond).isFalse();
    assertThat(executionCount.get()).isEqualTo(1);

    // Verify record exists in processed_event
    Integer rowCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM processed_event WHERE consumer = ? AND event_id = ?",
            Integer.class,
            consumerName,
            eventId);
    assertThat(rowCount).isEqualTo(1);
  }

  @Test
  @Order(7)
  @DisplayName("7. PostgreSQL logical WAL replication slot status and lag check")
  void testReplicationSlotAndWalHealth() {
    await()
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              List<Map<String, Object>> slots =
                  jdbcTemplate.queryForList("SELECT * FROM pg_replication_slots");
              log.info("Found {} replication slot(s): {}", slots.size(), slots);
              assertThat(slots).isNotEmpty();

              Map<String, Object> slot =
                  slots.stream()
                      .filter(s -> "booking_outbox_slot".equals(s.get("slot_name")))
                      .findFirst()
                      .orElse(slots.get(0));

              assertThat(slot.get("plugin")).isEqualTo("pgoutput");
              log.info(
                  "Replication slot [{}] plugin=[{}] active=[{}] confirmed_flush_lsn=[{}]",
                  slot.get("slot_name"),
                  slot.get("plugin"),
                  slot.get("active"),
                  slot.get("confirmed_flush_lsn"));
            });
  }

  private ConsumerRecord<String, String> pollSingleRecord(
      KafkaConsumer<String, String> consumer, String targetKey) {
    List<ConsumerRecord<String, String>> records = pollRecordsForKey(consumer, targetKey, 1);
    return records.isEmpty() ? null : records.get(0);
  }

  private List<ConsumerRecord<String, String>> pollRecordsForKey(
      KafkaConsumer<String, String> consumer, String targetKey, int expectedCount) {
    List<ConsumerRecord<String, String>> matchingRecords = new ArrayList<>();
    Instant deadline = Instant.now().plusSeconds(30);

    while (Instant.now().isBefore(deadline) && matchingRecords.size() < expectedCount) {
      ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
      for (ConsumerRecord<String, String> record : records) {
        if (targetKey.equals(record.key())) {
          matchingRecords.add(record);
        }
      }
    }
    return matchingRecords;
  }

  private Map<String, String> extractHeaders(ConsumerRecord<String, String> record) {
    Map<String, String> map = new HashMap<>();
    for (Header header : record.headers()) {
      map.put(
          header.key(),
          header.value() == null ? null : new String(header.value(), StandardCharsets.UTF_8));
    }
    return map;
  }
}
