package team.ylab.inventory.infra.persistence.adapter;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import team.ylab.inventory.app.port.out.SkuRepositoryPort;
import team.ylab.inventory.domain.model.Sku;
import team.ylab.inventory.infra.persistence.entity.SkuJpaEntity;
import team.ylab.inventory.infra.persistence.mapper.SkuPersistenceMapper;
import team.ylab.inventory.infra.persistence.repository.SpringDataJpaSkuRepository;

/** Adapter implementing {@link SkuRepositoryPort} via Spring Data JPA. */
@Component
public class SkuRepositoryAdapter implements SkuRepositoryPort {

  private final SpringDataJpaSkuRepository repository;
  private final SkuPersistenceMapper mapper;

  public SkuRepositoryAdapter(SpringDataJpaSkuRepository repository, SkuPersistenceMapper mapper) {
    this.repository = Objects.requireNonNull(repository, "repository cannot be null");
    this.mapper = Objects.requireNonNull(mapper, "mapper cannot be null");
  }

  @Override
  public Sku save(Sku sku) {
    SkuJpaEntity entity = mapper.toEntity(sku);
    SkuJpaEntity saved = repository.saveAndFlush(entity);
    return mapper.toDomain(saved);
  }

  @Override
  public Optional<Sku> findById(UUID skuId) {
    return repository.findById(skuId).map(mapper::toDomain);
  }

  @Override
  public boolean existsById(UUID skuId) {
    return repository.existsById(skuId);
  }
}
