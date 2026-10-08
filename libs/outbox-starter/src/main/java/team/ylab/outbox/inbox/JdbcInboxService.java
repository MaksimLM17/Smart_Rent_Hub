package team.ylab.outbox.inbox;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import team.ylab.outbox.autoconfigure.OutboxProperties;

/**
 * JDBC implementation of {@link InboxService}. Records processed event IDs in {@code
 * processed_event} table using composite primary key {@code (consumer, event_id)}.
 */
@Slf4j
public class JdbcInboxService implements InboxService {

  private final JdbcTemplate jdbcTemplate;
  private final OutboxProperties properties;

  public JdbcInboxService(JdbcTemplate jdbcTemplate, OutboxProperties properties) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
  }

  @Override
  @Transactional
  public boolean markProcessed(String consumer, UUID eventId) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    Objects.requireNonNull(eventId, "eventId must not be null");

    String sql =
        "INSERT INTO "
            + properties.getInboxTableName()
            + " (consumer, event_id, processed_at) VALUES (?, ?, ?)";

    try {
      jdbcTemplate.update(sql, consumer.trim(), eventId, Timestamp.from(Instant.now()));
      log.debug("Marked event {} as processed for consumer [{}]", eventId, consumer);
      return true;
    } catch (DuplicateKeyException e) {
      log.info(
          "Duplicate event detected: eventId {} has already been processed by consumer [{}]. Skipping.",
          eventId,
          consumer);
      return false;
    }
  }

  @Override
  public boolean isProcessed(String consumer, UUID eventId) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    Objects.requireNonNull(eventId, "eventId must not be null");

    String sql =
        "SELECT COUNT(*) FROM "
            + properties.getInboxTableName()
            + " WHERE consumer = ? AND event_id = ?";

    Integer count = jdbcTemplate.queryForObject(sql, Integer.class, consumer.trim(), eventId);
    return count != null && count > 0;
  }

  @Override
  @Transactional
  public boolean executeIfNotProcessed(String consumer, UUID eventId, Runnable action) {
    Objects.requireNonNull(action, "action must not be null");
    boolean isNew = markProcessed(consumer, eventId);
    if (isNew) {
      action.run();
      return true;
    }
    return false;
  }

  @Override
  @Transactional
  public <T> Optional<T> executeIfNotProcessed(String consumer, UUID eventId, Supplier<T> action) {
    Objects.requireNonNull(action, "action must not be null");
    boolean isNew = markProcessed(consumer, eventId);
    if (isNew) {
      return Optional.ofNullable(action.get());
    }
    return Optional.empty();
  }
}
