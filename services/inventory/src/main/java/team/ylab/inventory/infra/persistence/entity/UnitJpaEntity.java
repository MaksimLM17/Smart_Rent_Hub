package team.ylab.inventory.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import team.ylab.inventory.domain.model.Grade;
import team.ylab.inventory.domain.model.MaintenanceKind;
import team.ylab.inventory.domain.model.UnitStatus;

/** JPA entity representing a physical unit record in the database. */
@Entity
@Table(name = "unit")
@Getter
@Setter
@NoArgsConstructor
public class UnitJpaEntity {

  @Id
  @Column(name = "unit_id", nullable = false)
  private UUID unitId;

  @Column(name = "sku_id", nullable = false)
  private UUID skuId;

  @Column(name = "serial_number", nullable = false, unique = true)
  private String serialNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private UnitStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "maintenance_kind")
  private MaintenanceKind maintenanceKind;

  @Enumerated(EnumType.STRING)
  @Column(name = "grade", nullable = false, length = 1)
  private Grade grade;

  @Column(name = "assigned_order_id")
  private UUID assignedOrderId;

  @Column(name = "total_wear_days", nullable = false, precision = 8, scale = 1)
  private BigDecimal totalWearDays;

  @Column(name = "service_wear_days", nullable = false, precision = 8, scale = 1)
  private BigDecimal serviceWearDays;

  @Column(name = "acquisition_cost", nullable = false, precision = 12, scale = 2)
  private BigDecimal acquisitionCost;

  @Column(name = "acquisition_date", nullable = false)
  private LocalDate acquisitionDate;

  @Column(name = "residual_value_percent", nullable = false, precision = 5, scale = 2)
  private BigDecimal residualValuePercent;

  @Column(name = "expected_life_wear_days", nullable = false)
  private Integer expectedLifeWearDays;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
