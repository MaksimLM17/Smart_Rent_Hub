package team.ylab.inventory.infra.persistence.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import team.ylab.inventory.infra.persistence.entity.UnitJpaEntity;

/** Spring Data JPA repository for {@link UnitJpaEntity}. */
@Repository
public interface SpringDataJpaUnitRepository extends JpaRepository<UnitJpaEntity, UUID> {

  Optional<UnitJpaEntity> findBySerialNumber(String serialNumber);

  boolean existsBySerialNumber(String serialNumber);
}
