package team.ylab.outbox.publisher;

import team.ylab.outbox.model.OutboxEvent;

/**
 * Interface for publishing domain events via the Transactional Outbox pattern. Events are written
 * to the {@code outbox} table in the same database transaction as the business entity changes.
 */
public interface OutboxPublisher {

  /** Publishes a fully configured {@link OutboxEvent}. */
  void publish(OutboxEvent event);

  /** Publishes an event with default schema version 1. */
  default void publish(String aggregateType, String aggregateId, String eventType, Object payload) {
    publish(aggregateType, aggregateId, eventType, 1, payload);
  }

  /** Publishes an event with the specified schema version. */
  default void publish(
      String aggregateType,
      String aggregateId,
      String eventType,
      int schemaVersion,
      Object payload) {
    OutboxEvent event =
        OutboxEvent.builder()
            .aggregateType(aggregateType)
            .aggregateId(aggregateId)
            .eventType(eventType)
            .schemaVersion(schemaVersion)
            .payload(payload)
            .build();
    publish(event);
  }
}
