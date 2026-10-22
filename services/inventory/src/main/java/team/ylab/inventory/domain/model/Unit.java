package team.ylab.inventory.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import team.ylab.inventory.domain.exception.InventoryDomainException;

/** Pure domain model representing an individual inventory unit with digital passport. */
public class Unit {

  private final UUID unitId;
  private final UUID skuId;
  private final String serialNumber;
  private UnitStatus status;
  private MaintenanceKind maintenanceKind;
  private Grade grade;
  private UUID assignedOrderId;
  private BigDecimal totalWearDays;
  private BigDecimal serviceWearDays;
  private BigDecimal acquisitionCost;
  private LocalDate acquisitionDate;
  private BigDecimal residualValuePercent;
  private Integer expectedLifeWearDays;
  private Long version;
  private Instant updatedAt;

  public Unit(
      UUID unitId,
      UUID skuId,
      String serialNumber,
      UnitStatus status,
      MaintenanceKind maintenanceKind,
      Grade grade,
      UUID assignedOrderId,
      BigDecimal totalWearDays,
      BigDecimal serviceWearDays,
      BigDecimal acquisitionCost,
      LocalDate acquisitionDate,
      BigDecimal residualValuePercent,
      Integer expectedLifeWearDays,
      Long version,
      Instant updatedAt) {
    this.unitId = Objects.requireNonNull(unitId, "unitId cannot be null");
    this.skuId = Objects.requireNonNull(skuId, "skuId cannot be null");
    this.serialNumber = Objects.requireNonNull(serialNumber, "serialNumber cannot be null");
    this.status = Objects.requireNonNull(status, "status cannot be null");
    this.maintenanceKind = maintenanceKind;
    this.grade = Objects.requireNonNull(grade, "grade cannot be null");
    this.assignedOrderId = assignedOrderId;
    this.totalWearDays = totalWearDays != null ? totalWearDays : BigDecimal.ZERO;
    this.serviceWearDays = serviceWearDays != null ? serviceWearDays : BigDecimal.ZERO;
    this.acquisitionCost =
        Objects.requireNonNull(acquisitionCost, "acquisitionCost cannot be null");
    this.acquisitionDate =
        Objects.requireNonNull(acquisitionDate, "acquisitionDate cannot be null");
    this.residualValuePercent =
        Objects.requireNonNull(residualValuePercent, "residualValuePercent cannot be null");
    this.expectedLifeWearDays =
        Objects.requireNonNull(expectedLifeWearDays, "expectedLifeWearDays cannot be null");
    this.version = version;
    this.updatedAt = updatedAt != null ? updatedAt : Instant.now();

    validateMaintenanceConsistency();
  }

  private void validateMaintenanceConsistency() {
    if (status == UnitStatus.MAINTENANCE && maintenanceKind == null) {
      throw new InventoryDomainException(
          "maintenanceKind is required when unit status is MAINTENANCE");
    }
    if (status != UnitStatus.MAINTENANCE && maintenanceKind != null) {
      throw new InventoryDomainException(
          "maintenanceKind must be null when unit status is not MAINTENANCE");
    }
  }

  public void assignToOrder(UUID orderId) {
    if (this.status != UnitStatus.AVAILABLE) {
      throw new InventoryDomainException("Cannot assign unit in status: " + this.status);
    }
    this.status = UnitStatus.ASSIGNED;
    this.assignedOrderId = Objects.requireNonNull(orderId, "orderId cannot be null");
    this.updatedAt = Instant.now();
  }

  public void markInUse() {
    if (this.status != UnitStatus.ASSIGNED) {
      throw new InventoryDomainException(
          "Cannot hand over unit not in ASSIGNED status: " + this.status);
    }
    this.status = UnitStatus.IN_USE;
    this.updatedAt = Instant.now();
  }

  public void returnPendingInspection() {
    if (this.status != UnitStatus.IN_USE) {
      throw new InventoryDomainException("Cannot return unit not in IN_USE status: " + this.status);
    }
    this.status = UnitStatus.RETURNED_PENDING_INSPECTION;
    this.updatedAt = Instant.now();
  }

  public void completeInspection(boolean passed) {
    if (this.status != UnitStatus.RETURNED_PENDING_INSPECTION) {
      throw new InventoryDomainException(
          "Cannot complete inspection for unit not in RETURNED_PENDING_INSPECTION status: "
              + this.status);
    }
    if (passed) {
      this.status = UnitStatus.AVAILABLE;
      this.assignedOrderId = null;
    } else {
      this.status = UnitStatus.IN_REPAIR;
    }
    this.updatedAt = Instant.now();
  }

  public UUID getUnitId() {
    return unitId;
  }

  public UUID getSkuId() {
    return skuId;
  }

  public String getSerialNumber() {
    return serialNumber;
  }

  public UnitStatus getStatus() {
    return status;
  }

  public MaintenanceKind getMaintenanceKind() {
    return maintenanceKind;
  }

  public Grade getGrade() {
    return grade;
  }

  public UUID getAssignedOrderId() {
    return assignedOrderId;
  }

  public BigDecimal getTotalWearDays() {
    return totalWearDays;
  }

  public BigDecimal getServiceWearDays() {
    return serviceWearDays;
  }

  public BigDecimal getAcquisitionCost() {
    return acquisitionCost;
  }

  public LocalDate getAcquisitionDate() {
    return acquisitionDate;
  }

  public BigDecimal getResidualValuePercent() {
    return residualValuePercent;
  }

  public Integer getExpectedLifeWearDays() {
    return expectedLifeWearDays;
  }

  public Long getVersion() {
    return version;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
