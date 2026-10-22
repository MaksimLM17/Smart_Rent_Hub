package team.ylab.inventory.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** JPA entity representing a SKU record in the database. */
@Entity
@Table(name = "sku")
@Getter
@Setter
@NoArgsConstructor
public class SkuJpaEntity {

  @Id
  @Column(name = "sku_id", nullable = false)
  private UUID skuId;

  @Column(name = "category_id", nullable = false)
  private UUID categoryId;

  @Column(name = "brand", nullable = false)
  private String brand;

  @Column(name = "model", nullable = false)
  private String model;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "description")
  private String description;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "attributes", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> attributes = new HashMap<>();

  @Column(name = "replacement_value", nullable = false, precision = 12, scale = 2)
  private BigDecimal replacementValue;

  @Column(name = "same_day_turnaround", nullable = false)
  private boolean sameDayTurnaround;

  @Column(name = "expected_life_wear_days")
  private Integer expectedLifeWearDays;

  @Column(name = "residual_value_percent", precision = 5, scale = 2)
  private BigDecimal residualValuePercent;

  @Column(name = "repair_rate_per_wear_day", precision = 10, scale = 2)
  private BigDecimal repairRatePerWearDay;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
