package team.ylab.inventory.app.port.out;

import java.util.Optional;
import java.util.UUID;
import team.ylab.inventory.domain.model.Unit;

/** Outbound port for physical unit persistence. */
public interface UnitRepositoryPort {

  Unit save(Unit unit);

  Optional<Unit> findById(UUID unitId);

  Optional<Unit> findBySerialNumber(String serialNumber);

  boolean existsBySerialNumber(String serialNumber);
}
