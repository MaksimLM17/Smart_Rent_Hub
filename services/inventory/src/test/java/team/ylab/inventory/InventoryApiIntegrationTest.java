package team.ylab.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import team.ylab.inventory.api.dto.CreateSkuRequest;
import team.ylab.inventory.api.dto.UpdateSkuRequest;
import team.ylab.inventory.app.port.in.SkuUseCase;
import team.ylab.inventory.app.port.in.command.CreateSkuCommand;
import team.ylab.inventory.domain.model.Sku;
import team.ylab.security.test.WithMockSrhUser;
import team.ylab.testsupport.annotation.EnablePostgresTestcontainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnablePostgresTestcontainer
@DisplayName("Inventory API & Optimistic Concurrency Integration Test")
class InventoryApiIntegrationTest {

  @Autowired private WebApplicationContext context;
  private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private SkuUseCase skuUseCase;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  @DisplayName("GET /api/v1/catalog/skus/{id} is public and returns ETag")
  void publicCatalogSkuAccessReturnsETag() throws Exception {
    UUID skuId = UUID.randomUUID();
    Sku sku =
        skuUseCase.createSku(
            new CreateSkuCommand(
                skuId,
                UUID.randomUUID(),
                "Canon",
                "EOS R5",
                "Canon EOS R5 Mirrorless Camera",
                "8K video capable",
                Map.of("mount", "RF"),
                new BigDecimal("320000.00"),
                false,
                500,
                new BigDecimal("25.00"),
                new BigDecimal("120.00")));

    mockMvc
        .perform(get("/api/v1/catalog/skus/{skuId}", sku.getSkuId()))
        .andExpect(status().isOk())
        .andExpect(header().exists(HttpHeaders.ETAG))
        .andExpect(header().string(HttpHeaders.ETAG, "\"" + sku.getVersion() + "\""))
        .andExpect(jsonPath("$.skuId").value(sku.getSkuId().toString()))
        .andExpect(jsonPath("$.brand").value("Canon"))
        .andExpect(jsonPath("$.model").value("EOS R5"));
  }

  @Test
  @DisplayName("POST /api/v1/staff/skus without token returns 401 Unauthorized")
  void createSkuUnauthenticatedFails() throws Exception {
    CreateSkuRequest request =
        new CreateSkuRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Sony",
            "A7SIII",
            "Sony A7S III",
            "Low-light cinema camera",
            Map.of(),
            new BigDecimal("280000.00"),
            true,
            600,
            new BigDecimal("20.00"),
            new BigDecimal("100.00"));

    mockMvc
        .perform(
            post("/api/v1/staff/skus")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockSrhUser(roles = "CUSTOMER")
  @DisplayName("POST /api/v1/staff/skus with CUSTOMER role returns 403 Forbidden")
  void createSkuWithCustomerRoleForbidden() throws Exception {
    CreateSkuRequest request =
        new CreateSkuRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Sony",
            "A7SIII",
            "Sony A7S III",
            "Low-light cinema camera",
            Map.of(),
            new BigDecimal("280000.00"),
            true,
            600,
            new BigDecimal("20.00"),
            new BigDecimal("100.00"));

    mockMvc
        .perform(
            post("/api/v1/staff/skus")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockSrhUser(roles = "CATALOG_EDITOR")
  @DisplayName(
      "POST and PUT /api/v1/staff/skus with CATALOG_EDITOR role succeeds and checks If-Match")
  void createAndUpdateSkuWithIfMatchETag() throws Exception {
    UUID skuId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();

    CreateSkuRequest createRequest =
        new CreateSkuRequest(
            skuId,
            categoryId,
            "Blackmagic",
            "Pocket 6K",
            "BMPCC 6K Pro",
            "Pocket Cinema Camera 6K Pro",
            Map.of("mount", "EF"),
            new BigDecimal("250000.00"),
            true,
            400,
            new BigDecimal("15.00"),
            new BigDecimal("80.00"));

    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/v1/staff/skus")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andExpect(header().exists(HttpHeaders.ETAG))
            .andExpect(jsonPath("$.skuId").value(skuId.toString()))
            .andReturn();

    String etag = createResult.getResponse().getHeader(HttpHeaders.ETAG);
    assertThat(etag).isNotNull();

    // 1. Mismatched If-Match -> 412 Precondition Failed
    UpdateSkuRequest updateRequest =
        new UpdateSkuRequest(
            categoryId,
            "Blackmagic",
            "Pocket 6K Pro",
            "BMPCC 6K Pro Updated",
            "Updated cinema camera description",
            Map.of("mount", "EF"),
            new BigDecimal("260000.00"),
            true,
            450,
            new BigDecimal("15.00"),
            new BigDecimal("85.00"));

    mockMvc
        .perform(
            put("/api/v1/staff/skus/{skuId}", skuId)
                .header(HttpHeaders.IF_MATCH, "\"999999\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isPreconditionFailed())
        .andExpect(jsonPath("$.title").value("VERSION_CONFLICT"));

    // 2. Matching If-Match -> 200 OK and incremented version
    mockMvc
        .perform(
            put("/api/v1/staff/skus/{skuId}", skuId)
                .header(HttpHeaders.IF_MATCH, etag)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk())
        .andExpect(header().exists(HttpHeaders.ETAG))
        .andExpect(jsonPath("$.name").value("BMPCC 6K Pro Updated"))
        .andExpect(jsonPath("$.version").value(1));
  }
}
