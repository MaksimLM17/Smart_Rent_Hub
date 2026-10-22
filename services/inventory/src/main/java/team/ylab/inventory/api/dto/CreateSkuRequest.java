package team.ylab.inventory.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** DTO request to create a new SKU. */
public record CreateSkuRequest(
    UUID skuId,
    @NotNull(message = "categoryId is required") UUID categoryId,
    @NotBlank(message = "brand is required") String brand,
    @NotBlank(message = "model is required") String model,
    @NotBlank(message = "name is required") String name,
    String description,
    Map<String, Object> attributes,
    @NotNull(message = "replacementValue is required")
        @DecimalMin(
            value = "0.0",
            inclusive = true,
            message = "replacementValue must be non-negative")
        BigDecimal replacementValue,
    boolean sameDayTurnaround,
    Integer expectedLifeWearDays,
    BigDecimal residualValuePercent,
    BigDecimal repairRatePerWearDay) {}
