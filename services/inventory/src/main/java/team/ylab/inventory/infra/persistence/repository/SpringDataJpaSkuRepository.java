package team.ylab.inventory.infra.persistence.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import team.ylab.inventory.infra.persistence.entity.SkuJpaEntity;

/** Spring Data JPA repository for {@link SkuJpaEntity}. */
@Repository
public interface SpringDataJpaSkuRepository extends JpaRepository<SkuJpaEntity, UUID> {}
