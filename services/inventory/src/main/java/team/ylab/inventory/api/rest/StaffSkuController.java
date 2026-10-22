package team.ylab.inventory.api.rest;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.ylab.inventory.api.dto.CreateSkuRequest;
import team.ylab.inventory.api.dto.SkuResponse;
import team.ylab.inventory.api.dto.UpdateSkuRequest;
import team.ylab.inventory.app.port.in.SkuUseCase;
import team.ylab.inventory.app.port.in.command.CreateSkuCommand;
import team.ylab.inventory.app.port.in.command.UpdateSkuCommand;
import team.ylab.inventory.domain.exception.InventoryDomainException;
import team.ylab.inventory.domain.model.Sku;

/** Staff management controller for SKUs (SDD Appendix B, PRD 5.1). */
@RestController
@RequestMapping("/api/v1/staff/skus")
public class StaffSkuController {

  private final SkuUseCase skuUseCase;

  public StaffSkuController(SkuUseCase skuUseCase) {
    this.skuUseCase = Objects.requireNonNull(skuUseCase, "skuUseCase cannot be null");
  }

  @PostMapping
  @PreAuthorize("hasAuthority('sku:create')")
  public ResponseEntity<SkuResponse> createSku(@Valid @RequestBody CreateSkuRequest request) {
    CreateSkuCommand command =
        new CreateSkuCommand(
            request.skuId(),
            request.categoryId(),
            request.brand(),
            request.model(),
            request.name(),
            request.description(),
            request.attributes(),
            request.replacementValue(),
            request.sameDayTurnaround(),
            request.expectedLifeWearDays(),
            request.residualValuePercent(),
            request.repairRatePerWearDay());

    Sku created = skuUseCase.createSku(command);
    SkuResponse response = SkuResponse.fromDomain(created);

    return ResponseEntity.created(URI.create("/api/v1/catalog/skus/" + created.getSkuId()))
        .eTag("\"" + created.getVersion() + "\"")
        .body(response);
  }

  @PutMapping("/{skuId}")
  @PreAuthorize("hasAuthority('sku:update')")
  public ResponseEntity<SkuResponse> updateSku(
      @PathVariable("skuId") UUID skuId,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @Valid @RequestBody UpdateSkuRequest request) {

    if (ifMatch == null || ifMatch.trim().isEmpty()) {
      throw new InventoryDomainException("If-Match header is required for SKU updates");
    }

    Long expectedVersion = parseETag(ifMatch);

    UpdateSkuCommand command =
        new UpdateSkuCommand(
            request.categoryId(),
            request.brand(),
            request.model(),
            request.name(),
            request.description(),
            request.attributes(),
            request.replacementValue(),
            request.sameDayTurnaround(),
            request.expectedLifeWearDays(),
            request.residualValuePercent(),
            request.repairRatePerWearDay());

    Sku updated = skuUseCase.updateSku(skuId, command, expectedVersion);
    SkuResponse response = SkuResponse.fromDomain(updated);

    return ResponseEntity.ok().eTag("\"" + updated.getVersion() + "\"").body(response);
  }

  private Long parseETag(String etag) {
    String cleaned = etag.trim().replace("\"", "").replace("W/", "");
    try {
      return Long.parseLong(cleaned);
    } catch (NumberFormatException e) {
      throw new InventoryDomainException("Invalid If-Match ETag version format: " + etag);
    }
  }
}
