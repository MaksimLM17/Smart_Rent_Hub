package team.ylab.outbox.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties for Transactional Outbox and Inbox starters. */
@Data
@ConfigurationProperties(prefix = "srh.outbox")
public class OutboxProperties {

  /** Whether Outbox and Inbox features are enabled. */
  private boolean enabled = true;

  /** Name of the Outbox table. Default is {@code outbox}. */
  private String tableName = "outbox";

  /** Name of the Inbox table for deduplication. Default is {@code processed_event}. */
  private String inboxTableName = "processed_event";

  /**
   * When true, inserted outbox events are deleted within the same database transaction. Debezium
   * captures the row from PostgreSQL WAL without bloating the physical table. Default is true (per
   * SDD v1.1 Section 5.3).
   */
  private boolean deleteImmediately = true;
}
