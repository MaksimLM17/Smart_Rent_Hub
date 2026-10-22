package team.ylab.inventory.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import team.ylab.inventory.domain.model.Sku;

/** DTO response representing a SKU item. */
public record SkuResponse(
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

  public static SkuResponse fromDomain(Sku sku) {
    return new SkuResponse(
        sku.getSkuId(),
        sku.getCategoryId(),
        sku.getBrand(),
        sku.getModel(),
        sku.getName(),
        sku.getDescription(),
        sku.getAttributes(),
        sku.getReplacementValue(),
        sku.isSameDayTurnaround(),
        sku.getExpectedLifeWearDays(),
        sku.getResidualValuePercent(),
        sku.getRepairRatePerWearDay(),
        sku.getVersion() != null ? sku.getVersion() : 0L,
        sku.getUpdatedAt());
  }
}
