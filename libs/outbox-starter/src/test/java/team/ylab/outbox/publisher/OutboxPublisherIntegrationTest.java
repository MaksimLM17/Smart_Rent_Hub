package team.ylab.outbox.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import team.ylab.outbox.autoconfigure.OutboxAutoConfiguration;
import team.ylab.outbox.autoconfigure.OutboxProperties;
import team.ylab.outbox.model.OutboxEvent;

@SpringBootTest(classes = OutboxPublisherIntegrationTest.TestConfig.class)
@DisplayName("Transactional Outbox Publisher Integration Tests")
class OutboxPublisherIntegrationTest {

  @SpringBootApplication
  @Import(OutboxAutoConfiguration.class)
  static class TestConfig {}

  @Autowired private OutboxPublisher outboxPublisher;
  @Autowired private OutboxProperties outboxProperties;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TransactionTemplate transactionTemplate;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute(
        "CREATE TABLE IF NOT EXISTS outbox ("
            + "id UUID PRIMARY KEY, "
            + "aggregate_type VARCHAR(255) NOT NULL, "
            + "aggregate_id VARCHAR(255) NOT NULL, "
            + "event_type VARCHAR(255) NOT NULL, "
            + "schema_version INT NOT NULL, "
            + "correlation_id VARCHAR(255), "
            + "traceparent VARCHAR(255), "
            + "occurred_at TIMESTAMP NOT NULL, "
            + "payload VARCHAR(4000) NOT NULL, "
            + "created_at TIMESTAMP NOT NULL)");

    jdbcTemplate.execute("DELETE FROM outbox");
    outboxProperties.setDeleteImmediately(true);
    MDC.clear();
  }

  @Test
  @DisplayName(
      "Publish event with deleteImmediately=true executes insert and delete in same transaction")
  void publishWithImmediateDelete() {
    UUID eventId = UUID.randomUUID();
    OutboxEvent event =
        OutboxEvent.builder()
            .id(eventId)
            .aggregateType("inventory.sku.v1")
            .aggregateId("sku-100")
            .eventType("SkuUpserted")
            .payload(Map.of("skuId", "sku-100", "name", "Sony FX3", "brand", "Sony"))
            .build();

    transactionTemplate.executeWithoutResult(
        status -> {
          outboxPublisher.publish(event);
        });

    // In deleteImmediately mode, row was written to WAL and deleted in same tx
    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox", Integer.class);
    assertThat(count).isZero();
  }

  @Test
  @DisplayName("Publish event with deleteImmediately=false retains record in outbox table")
  void publishWithoutImmediateDeleteRetainsRecord() {
    outboxProperties.setDeleteImmediately(false);

    UUID eventId = UUID.randomUUID();
    OutboxEvent event =
        OutboxEvent.builder()
            .id(eventId)
            .aggregateType("booking.order.v1")
            .aggregateId("order-555")
            .eventType("OrderCreated")
            .schemaVersion(2)
            .payload(Map.of("orderId", "order-555", "totalAmount", 12500))
            .build();

    transactionTemplate.executeWithoutResult(
        status -> {
          outboxPublisher.publish(event);
        });

    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox", Integer.class);
    assertThat(count).isEqualTo(1);

    Map<String, Object> row =
        jdbcTemplate.queryForMap("SELECT * FROM outbox WHERE id = ?", eventId);
    assertThat(row.get("aggregate_type")).isEqualTo("booking.order.v1");
    assertThat(row.get("aggregate_id")).isEqualTo("order-555");
    assertThat(row.get("event_type")).isEqualTo("OrderCreated");
    assertThat(row.get("schema_version")).isEqualTo(2);
    assertThat((String) row.get("payload")).contains("order-555");
  }

  @Test
  @DisplayName("Transaction rollback rolls back outbox insert")
  void transactionRollbackRollsBackOutbox() {
    outboxProperties.setDeleteImmediately(false);

    assertThatThrownBy(
            () -> {
              transactionTemplate.executeWithoutResult(
                  status -> {
                    outboxPublisher.publish(
                        "inventory.sku.v1", "sku-999", "SkuDeleted", Map.of("skuId", "sku-999"));
                    throw new RuntimeException("Simulated business transaction failure");
                  });
            })
        .hasMessageContaining("Simulated business transaction failure");

    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox", Integer.class);
    assertThat(count).isZero();
  }

  @Test
  @DisplayName("Auto-populates correlationId and traceparent from MDC when available")
  void autoPopulatesTraceMetadataFromMdc() {
    outboxProperties.setDeleteImmediately(false);
    MDC.put("correlation_id", "corr-test-1234");
    MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
    MDC.put("spanId", "00f067aa0ba902b7");

    UUID eventId = UUID.randomUUID();
    OutboxEvent event =
        OutboxEvent.builder()
            .id(eventId)
            .aggregateType("payments.charge.v1")
            .aggregateId("charge-777")
            .eventType("PaymentAuthorized")
            .payload(Map.of("amount", 5000))
            .build();

    transactionTemplate.executeWithoutResult(
        status -> {
          outboxPublisher.publish(event);
        });

    Map<String, Object> row =
        jdbcTemplate.queryForMap("SELECT * FROM outbox WHERE id = ?", eventId);
    assertThat(row.get("correlation_id")).isEqualTo("corr-test-1234");
    assertThat((String) row.get("traceparent"))
        .isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
  }
}
