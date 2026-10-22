package team.ylab.inventory.app.port.out;

import java.util.Optional;
import java.util.UUID;
import team.ylab.inventory.domain.model.Sku;

/** Outbound port for SKU persistence. */
public interface SkuRepositoryPort {

  Sku save(Sku sku);

  Optional<Sku> findById(UUID skuId);

  boolean existsById(UUID skuId);
}
