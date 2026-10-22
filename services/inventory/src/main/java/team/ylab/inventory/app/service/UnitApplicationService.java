package team.ylab.inventory.app.service;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.ylab.inventory.app.port.in.UnitUseCase;
import team.ylab.inventory.app.port.out.UnitRepositoryPort;
import team.ylab.inventory.domain.exception.EntityNotFoundException;
import team.ylab.inventory.domain.model.Unit;

/** Application service orchestrating physical unit use cases. */
@Service
@Transactional
public class UnitApplicationService implements UnitUseCase {

  private final UnitRepositoryPort unitRepository;

  public UnitApplicationService(UnitRepositoryPort unitRepository) {
    this.unitRepository = Objects.requireNonNull(unitRepository, "unitRepository cannot be null");
  }

  @Override
  @Transactional(readOnly = true)
  public Unit getUnit(UUID unitId) {
    return unitRepository
        .findById(unitId)
        .orElseThrow(() -> new EntityNotFoundException("Unit not found with id: " + unitId));
  }
}
