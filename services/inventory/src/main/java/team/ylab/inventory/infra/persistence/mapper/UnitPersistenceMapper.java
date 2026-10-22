package team.ylab.inventory.infra.persistence.mapper;

import org.springframework.stereotype.Component;
import team.ylab.inventory.domain.model.Unit;
import team.ylab.inventory.infra.persistence.entity.UnitJpaEntity;

/** Mapper between {@link Unit} domain model and {@link UnitJpaEntity}. */
@Component
public class UnitPersistenceMapper {

  public Unit toDomain(UnitJpaEntity entity) {
    if (entity == null) {
      return null;
    }
    return new Unit(
        entity.getUnitId(),
        entity.getSkuId(),
        entity.getSerialNumber(),
        entity.getStatus(),
        entity.getMaintenanceKind(),
        entity.getGrade(),
        entity.getAssignedOrderId(),
        entity.getTotalWearDays(),
        entity.getServiceWearDays(),
        entity.getAcquisitionCost(),
        entity.getAcquisitionDate(),
        entity.getResidualValuePercent(),
        entity.getExpectedLifeWearDays(),
        entity.getVersion(),
        entity.getUpdatedAt());
  }

  public UnitJpaEntity toEntity(Unit domain) {
    if (domain == null) {
      return null;
    }
    UnitJpaEntity entity = new UnitJpaEntity();
    entity.setUnitId(domain.getUnitId());
    entity.setSkuId(domain.getSkuId());
    entity.setSerialNumber(domain.getSerialNumber());
    entity.setStatus(domain.getStatus());
    entity.setMaintenanceKind(domain.getMaintenanceKind());
    entity.setGrade(domain.getGrade());
    entity.setAssignedOrderId(domain.getAssignedOrderId());
    entity.setTotalWearDays(domain.getTotalWearDays());
    entity.setServiceWearDays(domain.getServiceWearDays());
    entity.setAcquisitionCost(domain.getAcquisitionCost());
    entity.setAcquisitionDate(domain.getAcquisitionDate());
    entity.setResidualValuePercent(domain.getResidualValuePercent());
    entity.setExpectedLifeWearDays(domain.getExpectedLifeWearDays());
    entity.setVersion(domain.getVersion());
    entity.setUpdatedAt(domain.getUpdatedAt());
    return entity;
  }
}
