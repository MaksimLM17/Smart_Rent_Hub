package team.ylab.inventory.domain.exception;

/** Thrown when a requested inventory entity cannot be found. */
public class EntityNotFoundException extends InventoryDomainException {

  public EntityNotFoundException(String message) {
    super(message);
  }
}
