package team.ylab.spikes.s1.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import team.ylab.spikes.s1.entity.SpikeEntity;

@Repository
public interface SpikeRepository extends JpaRepository<SpikeEntity, Long> {
  Optional<SpikeEntity> findByBusinessKey(String businessKey);
}
