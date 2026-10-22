package team.ylab.inventory.api.rest;

import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.ylab.inventory.api.dto.SkuResponse;
import team.ylab.inventory.app.port.in.SkuUseCase;
import team.ylab.inventory.domain.model.Sku;

/** Public catalog controller for browsing SKUs (SDD Appendix B). */
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

  private final SkuUseCase skuUseCase;

  public CatalogController(SkuUseCase skuUseCase) {
    this.skuUseCase = Objects.requireNonNull(skuUseCase, "skuUseCase cannot be null");
  }

  @GetMapping("/skus/{skuId}")
  public ResponseEntity<SkuResponse> getSku(@PathVariable("skuId") UUID skuId) {
    Sku sku = skuUseCase.getSku(skuId);
    SkuResponse response = SkuResponse.fromDomain(sku);
    return ResponseEntity.ok().eTag("\"" + sku.getVersion() + "\"").body(response);
  }
}
