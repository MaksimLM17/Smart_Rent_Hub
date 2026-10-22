package team.ylab.inventory.app.port.in;

import java.util.UUID;
import team.ylab.inventory.app.port.in.command.CreateSkuCommand;
import team.ylab.inventory.app.port.in.command.UpdateSkuCommand;
import team.ylab.inventory.domain.model.Sku;

/** Inbound use case port for SKU catalog operations. */
public interface SkuUseCase {

  Sku createSku(CreateSkuCommand command);

  Sku getSku(UUID skuId);

  Sku updateSku(UUID skuId, UpdateSkuCommand command, Long expectedVersion);
}
