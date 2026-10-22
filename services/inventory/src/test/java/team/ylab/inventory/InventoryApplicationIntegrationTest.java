package team.ylab.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import team.ylab.inventory.app.port.in.SkuUseCase;
import team.ylab.inventory.app.port.in.command.CreateSkuCommand;
import team.ylab.inventory.domain.model.Sku;
import team.ylab.testsupport.annotation.EnablePostgresTestcontainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnablePostgresTestcontainer
@DisplayName("Inventory Application Skeleton & Liquibase Integration Test")
class InventoryApplicationIntegrationTest {

  @Autowired private SkuUseCase skuUseCase;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("Context loads and Liquibase creates all required inventory tables")
  void contextLoadsAndLiquibaseCreatesTables() {
    // Verify Liquibase applied outbox tables
    Integer outboxCount = jdbcTemplate.queryForObject("SELECT count(*) FROM outbox", Integer.class);
    assertThat(outboxCount).isNotNull().isGreaterThanOrEqualTo(0);

    Integer processedEventCount =
        jdbcTemplate.queryForObject("SELECT count(*) FROM processed_event", Integer.class);
    assertThat(processedEventCount).isNotNull().isGreaterThanOrEqualTo(0);

    // Verify Liquibase applied domain tables from Appendix A.2
    Integer skuCount = jdbcTemplate.queryForObject("SELECT count(*) FROM sku", Integer.class);
    assertThat(skuCount).isNotNull().isGreaterThanOrEqualTo(0);

    Integer unitCount = jdbcTemplate.queryForObject("SELECT count(*) FROM unit", Integer.class);
    assertThat(unitCount).isNotNull().isGreaterThanOrEqualTo(0);

    Integer mediaCount = jdbcTemplate.queryForObject("SELECT count(*) FROM media", Integer.class);
    assertThat(mediaCount).isNotNull().isGreaterThanOrEqualTo(0);

    Integer claimCount = jdbcTemplate.queryForObject("SELECT count(*) FROM claim", Integer.class);
    assertThat(claimCount).isNotNull().isGreaterThanOrEqualTo(0);
  }

  @Test
  @DisplayName("SKU can be created and retrieved through hexagonal application ports")
  void createAndRetrieveSkuViaPorts() {
    UUID skuId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();

    CreateSkuCommand command =
        new CreateSkuCommand(
            skuId,
            categoryId,
            "Sony",
            "FX3",
            "Cinema Line FX3 Full-frame Camera",
            "Professional cinema camera",
            Map.of("mount", "E-mount", "sensor", "Full Frame"),
            new BigDecimal("350000.00"),
            true,
            730,
            new BigDecimal("20.00"),
            new BigDecimal("150.00"));

    Sku created = skuUseCase.createSku(command);

    assertThat(created).isNotNull();
    assertThat(created.getSkuId()).isEqualTo(skuId);
    assertThat(created.getName()).isEqualTo("Cinema Line FX3 Full-frame Camera");
    assertThat(created.getVersion()).isZero();

    Sku fetched = skuUseCase.getSku(skuId);
    assertThat(fetched).isNotNull();
    assertThat(fetched.getBrand()).isEqualTo("Sony");
    assertThat(fetched.getModel()).isEqualTo("FX3");
    assertThat(fetched.getAttributes()).containsEntry("mount", "E-mount");
  }
}
