package team.ylab.inventory.infra.persistence.adapter;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import team.ylab.inventory.app.port.out.UnitRepositoryPort;
import team.ylab.inventory.domain.model.Unit;
import team.ylab.inventory.infra.persistence.entity.UnitJpaEntity;
import team.ylab.inventory.infra.persistence.mapper.UnitPersistenceMapper;
import team.ylab.inventory.infra.persistence.repository.SpringDataJpaUnitRepository;

/** Adapter implementing {@link UnitRepositoryPort} via Spring Data JPA. */
@Component
public class UnitRepositoryAdapter implements UnitRepositoryPort {

  private final SpringDataJpaUnitRepository repository;
  private final UnitPersistenceMapper mapper;

  public UnitRepositoryAdapter(
      SpringDataJpaUnitRepository repository, UnitPersistenceMapper mapper) {
    this.repository = Objects.requireNonNull(repository, "repository cannot be null");
    this.mapper = Objects.requireNonNull(mapper, "mapper cannot be null");
  }

  @Override
  public Unit save(Unit unit) {
    UnitJpaEntity entity = mapper.toEntity(unit);
    UnitJpaEntity saved = repository.saveAndFlush(entity);
    return mapper.toDomain(saved);
  }

  @Override
  public Optional<Unit> findById(UUID unitId) {
    return repository.findById(unitId).map(mapper::toDomain);
  }

  @Override
  public Optional<Unit> findBySerialNumber(String serialNumber) {
    return repository.findBySerialNumber(serialNumber).map(mapper::toDomain);
  }

  @Override
  public boolean existsBySerialNumber(String serialNumber) {
    return repository.existsBySerialNumber(serialNumber);
  }
}
