package team.ylab.inventory.app.service;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.ylab.inventory.app.port.in.SkuUseCase;
import team.ylab.inventory.app.port.in.command.CreateSkuCommand;
import team.ylab.inventory.app.port.in.command.UpdateSkuCommand;
import team.ylab.inventory.app.port.out.SkuRepositoryPort;
import team.ylab.inventory.domain.exception.EntityNotFoundException;
import team.ylab.inventory.domain.exception.VersionConflictException;
import team.ylab.inventory.domain.model.Sku;

/** Application service orchestrating SKU use cases. */
@Service
@Transactional
public class SkuApplicationService implements SkuUseCase {

  private final SkuRepositoryPort skuRepository;

  public SkuApplicationService(SkuRepositoryPort skuRepository) {
    this.skuRepository = Objects.requireNonNull(skuRepository, "skuRepository cannot be null");
  }

  @Override
  public Sku createSku(CreateSkuCommand command) {
    Sku sku =
        Sku.create(
            command.skuId(),
            command.categoryId(),
            command.brand(),
            command.model(),
            command.name(),
            command.description(),
            command.attributes(),
            command.replacementValue(),
            command.sameDayTurnaround(),
            command.expectedLifeWearDays(),
            command.residualValuePercent(),
            command.repairRatePerWearDay());
    return skuRepository.save(sku);
  }

  @Override
  @Transactional(readOnly = true)
  public Sku getSku(UUID skuId) {
    return skuRepository
        .findById(skuId)
        .orElseThrow(() -> new EntityNotFoundException("SKU not found with id: " + skuId));
  }

  @Override
  public Sku updateSku(UUID skuId, UpdateSkuCommand command, Long expectedVersion) {
    Sku existing = getSku(skuId);

    if (expectedVersion != null && !Objects.equals(existing.getVersion(), expectedVersion)) {
      throw new VersionConflictException(
          "Version conflict for SKU "
              + skuId
              + ": expected "
              + expectedVersion
              + " but was "
              + existing.getVersion(),
          expectedVersion,
          existing.getVersion());
    }

    existing.update(
        command.categoryId(),
        command.brand(),
        command.model(),
        command.name(),
        command.description(),
        command.attributes(),
        command.replacementValue(),
        command.sameDayTurnaround(),
        command.expectedLifeWearDays(),
        command.residualValuePercent(),
        command.repairRatePerWearDay());

    return skuRepository.save(existing);
  }
}
