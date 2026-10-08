package team.ylab.outbox.model;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Domain event representation for Transactional Outbox pattern. Mapped to the {@code outbox} table
 * as specified in SDD v1.1 Section 5.3 and Appendix A.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

  /** Unique event identifier. If null when publishing, will be generated automatically. */
  private UUID id;

  /** Aggregate type / Kafka topic name (e.g. "booking.order.v1", "inventory.sku.v1"). */
  private String aggregateType;

  /** Partition key (e.g. orderId, skuId). */
  private String aggregateId;

  /** Domain event name (e.g. "OrderCreated", "SkuUpserted"). */
  private String eventType;

  /** Schema version for contract evolution (default 1). */
  @Builder.Default private int schemaVersion = 1;

  /** Correlation ID for tracing across distributed services. */
  private String correlationId;

  /** W3C traceparent (e.g. 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01). */
  private String traceparent;

  /** Timestamp when the business event occurred. */
  @Builder.Default private Instant occurredAt = Instant.now();

  /** Business payload (will be serialized to JSON/jsonb). */
  private Object payload;

  /** Timestamp of outbox record creation. */
  @Builder.Default private Instant createdAt = Instant.now();
}
