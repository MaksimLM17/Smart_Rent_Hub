package team.ylab.outbox.inbox;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Service for idempotent consumer handling (Inbox pattern). Manages tracking of processed events in
 * the {@code processed_event} table to ensure exactly-once processing semantics for consumers.
 */
public interface InboxService {

  /**
   * Attempts to mark an event as processed for the given consumer name.
   *
   * @param consumer name of the consumer group / service
   * @param eventId unique ID of the event
   * @return {@code true} if this event was not previously processed and was successfully recorded,
   *     {@code false} if the event was already processed (duplicate).
   */
  boolean markProcessed(String consumer, UUID eventId);

  /** Checks whether the event has already been recorded as processed. */
  boolean isProcessed(String consumer, UUID eventId);

  /**
   * Executes the given action within a transaction only if the event was not already processed. If
   * the event was already processed, the action is skipped.
   *
   * @param consumer name of the consumer
   * @param eventId event UUID
   * @param action business action to execute
   * @return {@code true} if action was executed, {@code false} if event was skipped as duplicate
   */
  boolean executeIfNotProcessed(String consumer, UUID eventId, Runnable action);

  /**
   * Executes the supplier within a transaction only if the event was not already processed.
   *
   * @param consumer name of the consumer
   * @param eventId event UUID
   * @param action business supplier producing a result
   * @param <T> result type
   * @return Optional containing result if processed, or empty Optional if skipped as duplicate
   */
  <T> Optional<T> executeIfNotProcessed(String consumer, UUID eventId, Supplier<T> action);
}
