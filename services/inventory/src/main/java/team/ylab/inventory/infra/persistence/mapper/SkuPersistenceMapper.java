package team.ylab.inventory.infra.persistence.mapper;

import java.util.HashMap;
import org.springframework.stereotype.Component;
import team.ylab.inventory.domain.model.Sku;
import team.ylab.inventory.infra.persistence.entity.SkuJpaEntity;

/** Mapper between {@link Sku} domain model and {@link SkuJpaEntity}. */
@Component
public class SkuPersistenceMapper {

  public Sku toDomain(SkuJpaEntity entity) {
    if (entity == null) {
      return null;
    }
    return new Sku(
        entity.getSkuId(),
        entity.getCategoryId(),
        entity.getBrand(),
        entity.getModel(),
        entity.getName(),
        entity.getDescription(),
        entity.getAttributes() != null ? new HashMap<>(entity.getAttributes()) : new HashMap<>(),
        entity.getReplacementValue(),
        entity.isSameDayTurnaround(),
        entity.getExpectedLifeWearDays(),
        entity.getResidualValuePercent(),
        entity.getRepairRatePerWearDay(),
        entity.getVersion(),
        entity.getUpdatedAt());
  }

  public SkuJpaEntity toEntity(Sku domain) {
    if (domain == null) {
      return null;
    }
    SkuJpaEntity entity = new SkuJpaEntity();
    entity.setSkuId(domain.getSkuId());
    entity.setCategoryId(domain.getCategoryId());
    entity.setBrand(domain.getBrand());
    entity.setModel(domain.getModel());
    entity.setName(domain.getName());
    entity.setDescription(domain.getDescription());
    entity.setAttributes(new HashMap<>(domain.getAttributes()));
    entity.setReplacementValue(domain.getReplacementValue());
    entity.setSameDayTurnaround(domain.isSameDayTurnaround());
    entity.setExpectedLifeWearDays(domain.getExpectedLifeWearDays());
    entity.setResidualValuePercent(domain.getResidualValuePercent());
    entity.setRepairRatePerWearDay(domain.getRepairRatePerWearDay());
    entity.setVersion(domain.getVersion());
    entity.setUpdatedAt(domain.getUpdatedAt());
    return entity;
  }
}
