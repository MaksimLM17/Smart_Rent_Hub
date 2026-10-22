package team.ylab.inventory.app.port.in.command;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Command to create a new SKU. */
public record CreateSkuCommand(
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
    BigDecimal repairRatePerWearDay) {}
