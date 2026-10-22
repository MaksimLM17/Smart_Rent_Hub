package team.ylab.inventory.domain.exception;

/**
 * Thrown when an optimistic concurrency check fails (e.g. outdated version or mismatched If-Match
 * ETag), resulting in RFC 9457 412 Precondition Failed.
 */
public class VersionConflictException extends InventoryDomainException {

  private final Long expectedVersion;
  private final Long actualVersion;

  public VersionConflictException(String message) {
    super(message);
    this.expectedVersion = null;
    this.actualVersion = null;
  }

  public VersionConflictException(String message, Long expectedVersion, Long actualVersion) {
    super(message);
    this.expectedVersion = expectedVersion;
    this.actualVersion = actualVersion;
  }

  public Long getExpectedVersion() {
    return expectedVersion;
  }

  public Long getActualVersion() {
    return actualVersion;
  }
}
