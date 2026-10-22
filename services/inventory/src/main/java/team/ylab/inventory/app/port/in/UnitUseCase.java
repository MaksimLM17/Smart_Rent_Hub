package team.ylab.inventory.app.port.in;

import java.util.UUID;
import team.ylab.inventory.domain.model.Unit;

/** Inbound use case port for Unit operations. */
public interface UnitUseCase {

  Unit getUnit(UUID unitId);
}
