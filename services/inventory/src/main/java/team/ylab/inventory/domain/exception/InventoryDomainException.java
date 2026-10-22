package team.ylab.inventory.domain.exception;

/** Base runtime exception for inventory domain business rule violations. */
public class InventoryDomainException extends RuntimeException {

  public InventoryDomainException(String message) {
    super(message);
  }

  public InventoryDomainException(String message, Throwable cause) {
    super(message, cause);
  }
}
