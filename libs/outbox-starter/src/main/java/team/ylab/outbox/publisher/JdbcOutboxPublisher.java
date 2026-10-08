package team.ylab.outbox.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import team.ylab.outbox.autoconfigure.OutboxProperties;
import team.ylab.outbox.model.OutboxEvent;

/**
 * JDBC implementation of {@link OutboxPublisher}. Writes events directly into the database {@code
 * outbox} table within the current transactional context.
 */
@Slf4j
public class JdbcOutboxPublisher implements OutboxPublisher {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final OutboxProperties properties;
  private volatile Boolean isPostgres;

  public JdbcOutboxPublisher(
      JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, OutboxProperties properties) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void publish(OutboxEvent event) {
    Objects.requireNonNull(event, "event must not be null");
    Objects.requireNonNull(event.getAggregateType(), "aggregateType must not be null");
    Objects.requireNonNull(event.getAggregateId(), "aggregateId must not be null");
    Objects.requireNonNull(event.getEventType(), "eventType must not be null");
    Objects.requireNonNull(event.getPayload(), "payload must not be null");

    if (event.getId() == null) {
      event.setId(UUID.randomUUID());
    }
    if (event.getOccurredAt() == null) {
      event.setOccurredAt(Instant.now());
    }
    if (event.getCreatedAt() == null) {
      event.setCreatedAt(Instant.now());
    }

    // Auto-populate trace metadata if missing
    if (event.getCorrelationId() == null || event.getCorrelationId().isBlank()) {
      event.setCorrelationId(extractCorrelationId());
    }
    if (event.getTraceparent() == null || event.getTraceparent().isBlank()) {
      event.setTraceparent(extractTraceparent());
    }

    String jsonPayload = serializePayload(event.getPayload());
    String insertSql = getInsertSql();

    jdbcTemplate.update(
        insertSql,
        event.getId(),
        event.getAggregateType(),
        event.getAggregateId(),
        event.getEventType(),
        event.getSchemaVersion(),
        event.getCorrelationId(),
        event.getTraceparent(),
        Timestamp.from(event.getOccurredAt()),
        jsonPayload,
        Timestamp.from(event.getCreatedAt()));

    log.debug(
        "Inserted outbox event {} for aggregate [{}:{}]",
        event.getId(),
        event.getAggregateType(),
        event.getAggregateId());

    if (properties.isDeleteImmediately()) {
      String deleteSql = "DELETE FROM " + properties.getTableName() + " WHERE id = ?";
      jdbcTemplate.update(deleteSql, event.getId());
      log.debug("Deleted outbox event {} in same transaction (WAL captured)", event.getId());
    }
  }

  private String serializePayload(Object payload) {
    if (payload instanceof String str) {
      return str;
    }
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to serialize outbox event payload to JSON", e);
    }
  }

  private String extractCorrelationId() {
    String cid = MDC.get("correlation_id");
    if (cid != null && !cid.isBlank()) {
      return cid;
    }
    return MDC.get("correlationId");
  }

  private String extractTraceparent() {
    String tp = MDC.get("traceparent");
    if (tp != null && !tp.isBlank()) {
      return tp;
    }
    String traceId = MDC.get("traceId");
    if (traceId != null && !traceId.isBlank()) {
      String spanId = MDC.get("spanId");
      if (spanId == null || spanId.isBlank()) {
        spanId = "0000000000000000";
      }
      return "00-" + traceId + "-" + spanId + "-01";
    }
    return null;
  }

  private String getInsertSql() {
    String tableName = properties.getTableName();
    if (isPostgreSqlDatabase()) {
      return "INSERT INTO "
          + tableName
          + " (id, aggregate_type, aggregate_id, event_type, schema_version, correlation_id, traceparent, occurred_at, payload, created_at) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)";
    }
    return "INSERT INTO "
        + tableName
        + " (id, aggregate_type, aggregate_id, event_type, schema_version, correlation_id, traceparent, occurred_at, payload, created_at) "
        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
  }

  private boolean isPostgreSqlDatabase() {
    if (isPostgres == null) {
      synchronized (this) {
        if (isPostgres == null) {
          try {
            isPostgres =
                jdbcTemplate.execute(
                    (Connection conn) -> {
                      DatabaseMetaData meta = conn.getMetaData();
                      String name = meta.getDatabaseProductName();
                      return name != null && name.toLowerCase().contains("postgres");
                    });
          } catch (Exception e) {
            log.warn("Could not determine database product name, falling back to standard SQL", e);
            isPostgres = false;
          }
        }
      }
    }
    return Boolean.TRUE.equals(isPostgres);
  }
}
