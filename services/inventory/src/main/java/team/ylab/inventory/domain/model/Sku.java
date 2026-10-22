package team.ylab.inventory.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import team.ylab.inventory.domain.exception.InventoryDomainException;

/**
 * Pure domain model representing a Stock Keeping Unit (catalog item). Independent of JPA or
 * framework annotations.
 */
public class Sku {

  private final UUID skuId;
  private UUID categoryId;
  private String brand;
  private String model;
  private String name;
  private String description;
  private Map<String, Object> attributes;
  private BigDecimal replacementValue;
  private boolean sameDayTurnaround;
  private Integer expectedLifeWearDays;
  private BigDecimal residualValuePercent;
  private BigDecimal repairRatePerWearDay;
  private Long version;
  private Instant updatedAt;

  public Sku(
      UUID skuId,
      UUID categoryId,
      String brand,
      String model,
      String name,
      String description,
      Map<String, Object> attributes,
      BigDecimal replacementValue,
      boolean sameDayTurnaround,
      Integer expectedLifeWearDays,
      BigDecimal residualValuePercent,
      BigDecimal repairRatePerWearDay,
      Long version,
      Instant updatedAt) {
    this.skuId = Objects.requireNonNull(skuId, "skuId cannot be null");
    this.categoryId = Objects.requireNonNull(categoryId, "categoryId cannot be null");
    this.brand = validateNonBlank(brand, "brand cannot be blank");
    this.model = validateNonBlank(model, "model cannot be blank");
    this.name = validateNonBlank(name, "name cannot be blank");
    this.description = description;
    this.attributes = attributes != null ? new HashMap<>(attributes) : new HashMap<>();
    this.replacementValue =
        Objects.requireNonNull(replacementValue, "replacementValue cannot be null");
    if (this.replacementValue.compareTo(BigDecimal.ZERO) < 0) {
      throw new InventoryDomainException("replacementValue must be non-negative");
    }
    this.sameDayTurnaround = sameDayTurnaround;
    this.expectedLifeWearDays = expectedLifeWearDays;
    this.residualValuePercent = residualValuePercent;
    this.repairRatePerWearDay = repairRatePerWearDay;
    this.version = version;
    this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
  }

  public static Sku create(
      UUID skuId,
      UUID categoryId,
      String brand,
      String model,
      String name,
      String description,
      Map<String, Object> attributes,
      BigDecimal replacementValue,
      boolean sameDayTurnaround,
      Integer expectedLifeWearDays,
      BigDecimal residualValuePercent,
      BigDecimal repairRatePerWearDay) {
    return new Sku(
        skuId != null ? skuId : UUID.randomUUID(),
        categoryId,
        brand,
        model,
        name,
        description,
        attributes,
        replacementValue,
        sameDayTurnaround,
        expectedLifeWearDays,
        residualValuePercent,
        repairRatePerWearDay,
        null,
        Instant.now());
  }

  public void update(
      UUID categoryId,
      String brand,
      String model,
      String name,
      String description,
      Map<String, Object> attributes,
      BigDecimal replacementValue,
      boolean sameDayTurnaround,
      Integer expectedLifeWearDays,
      BigDecimal residualValuePercent,
      BigDecimal repairRatePerWearDay) {
    this.categoryId = Objects.requireNonNull(categoryId, "categoryId cannot be null");
    this.brand = validateNonBlank(brand, "brand cannot be blank");
    this.model = validateNonBlank(model, "model cannot be blank");
    this.name = validateNonBlank(name, "name cannot be blank");
    this.description = description;
    this.attributes = attributes != null ? new HashMap<>(attributes) : new HashMap<>();
    this.replacementValue =
        Objects.requireNonNull(replacementValue, "replacementValue cannot be null");
    if (this.replacementValue.compareTo(BigDecimal.ZERO) < 0) {
      throw new InventoryDomainException("replacementValue must be non-negative");
    }
    this.sameDayTurnaround = sameDayTurnaround;
    this.expectedLifeWearDays = expectedLifeWearDays;
    this.residualValuePercent = residualValuePercent;
    this.repairRatePerWearDay = repairRatePerWearDay;
    this.updatedAt = Instant.now();
  }

  private static String validateNonBlank(String value, String message) {
    if (value == null || value.trim().isEmpty()) {
      throw new InventoryDomainException(message);
    }
    return value.trim();
  }

  public UUID getSkuId() {
    return skuId;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public String getBrand() {
    return brand;
  }

  public String getModel() {
    return model;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public Map<String, Object> getAttributes() {
    return Collections.unmodifiableMap(attributes);
  }

  public BigDecimal getReplacementValue() {
    return replacementValue;
  }

  public boolean isSameDayTurnaround() {
    return sameDayTurnaround;
  }

  public Integer getExpectedLifeWearDays() {
    return expectedLifeWearDays;
  }

  public BigDecimal getResidualValuePercent() {
    return residualValuePercent;
  }

  public BigDecimal getRepairRatePerWearDay() {
    return repairRatePerWearDay;
  }

  public Long getVersion() {
    return version;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
