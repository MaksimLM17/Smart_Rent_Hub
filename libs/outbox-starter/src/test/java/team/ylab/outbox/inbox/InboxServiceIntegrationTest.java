package team.ylab.outbox.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import team.ylab.outbox.autoconfigure.OutboxAutoConfiguration;

@SpringBootTest(classes = InboxServiceIntegrationTest.TestConfig.class)
@DisplayName("Inbox Deduplication Service Integration Tests")
class InboxServiceIntegrationTest {

  @SpringBootApplication
  @Import(OutboxAutoConfiguration.class)
  static class TestConfig {}

  @Autowired private InboxService inboxService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute(
        "CREATE TABLE IF NOT EXISTS processed_event ("
            + "consumer VARCHAR(255) NOT NULL, "
            + "event_id UUID NOT NULL, "
            + "processed_at TIMESTAMP NOT NULL, "
            + "PRIMARY KEY (consumer, event_id))");

    jdbcTemplate.execute("DELETE FROM processed_event");
  }

  @Test
  @DisplayName(
      "markProcessed returns true on first call and false on duplicate (unique constraint)")
  void duplicateEventIsRejected() {
    String consumer = "notification-service";
    UUID eventId = UUID.randomUUID();

    boolean firstResult = inboxService.markProcessed(consumer, eventId);
    assertThat(firstResult).isTrue();
    assertThat(inboxService.isProcessed(consumer, eventId)).isTrue();

    // Second call with same consumer and eventId is rejected as duplicate
    boolean secondResult = inboxService.markProcessed(consumer, eventId);
    assertThat(secondResult).isFalse();

    // Another consumer can process the same eventId
    boolean anotherConsumerResult = inboxService.markProcessed("search-service", eventId);
    assertThat(anotherConsumerResult).isTrue();
  }

  @Test
  @DisplayName("executeIfNotProcessed executes action only once for duplicate event")
  void executeIfNotProcessedExecutesOnlyOnce() {
    String consumer = "booking-service";
    UUID eventId = UUID.randomUUID();
    AtomicInteger executionCount = new AtomicInteger(0);

    boolean executedFirst =
        inboxService.executeIfNotProcessed(
            consumer,
            eventId,
            () -> {
              executionCount.incrementAndGet();
            });
    assertThat(executedFirst).isTrue();
    assertThat(executionCount.get()).isEqualTo(1);

    // Duplicate call
    boolean executedSecond =
        inboxService.executeIfNotProcessed(
            consumer,
            eventId,
            () -> {
              executionCount.incrementAndGet();
            });
    assertThat(executedSecond).isFalse();
    assertThat(executionCount.get()).isEqualTo(1); // not incremented
  }

  @Test
  @DisplayName("executeIfNotProcessed with supplier returns result once and empty on duplicate")
  void executeIfNotProcessedWithSupplier() {
    String consumer = "inventory-service";
    UUID eventId = UUID.randomUUID();

    Optional<String> res1 =
        inboxService.executeIfNotProcessed(consumer, eventId, () -> "business-success");
    assertThat(res1).contains("business-success");

    Optional<String> res2 =
        inboxService.executeIfNotProcessed(consumer, eventId, () -> "business-duplicate");
    assertThat(res2).isEmpty();
  }

  @Test
  @DisplayName("If business action fails, markProcessed is rolled back allowing subsequent retry")
  void failedActionRollsBackDeduplicationRecord() {
    String consumer = "risk-service";
    UUID eventId = UUID.randomUUID();

    assertThatThrownBy(
            () -> {
              inboxService.executeIfNotProcessed(
                  consumer,
                  eventId,
                  () -> {
                    throw new IllegalStateException(
                        "Business execution failed, should retry later");
                  });
            })
        .hasMessageContaining("Business execution failed");

    // Event should NOT be marked as processed, allowing retry
    assertThat(inboxService.isProcessed(consumer, eventId)).isFalse();

    // Next attempt succeeds
    boolean retryResult = inboxService.executeIfNotProcessed(consumer, eventId, () -> {});
    assertThat(retryResult).isTrue();
  }
}
