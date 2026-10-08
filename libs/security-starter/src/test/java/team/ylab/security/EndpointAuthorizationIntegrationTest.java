package team.ylab.security;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import team.ylab.security.rbac.SrhRole;
import team.ylab.security.test.SecurityTestHelper;
import team.ylab.security.test.WithMockSrhUser;

@SpringBootTest(classes = EndpointAuthorizationIntegrationTest.TestApplication.class)
@DisplayName("Endpoint Authorization Integration Test (Acceptance Criteria: sku:create)")
class EndpointAuthorizationIntegrationTest {

  @SpringBootApplication
  @org.springframework.context.annotation.Import(
      team.ylab.security.autoconfigure.SecurityAutoConfiguration.class)
  static class TestApplication {
    @RestController
    @RequestMapping("/api/v1/skus")
    static class SkuController {

      @PostMapping
      @PreAuthorize("hasAuthority('sku:create')")
      public ResponseEntity<String> createSku() {
        return ResponseEntity.ok("sku-created-successfully");
      }

      @GetMapping
      @PreAuthorize("hasAuthority('catalog:read')")
      public ResponseEntity<String> readCatalog() {
        return ResponseEntity.ok("catalog-read-successfully");
      }
    }
  }

  @Autowired private WebApplicationContext context;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  @DisplayName("Anonymous unauthenticated request is rejected with 401 Unauthorized")
  void unauthenticatedRequestIsRejected() throws Exception {
    mockMvc.perform(post("/api/v1/skus")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Role CATALOG_EDITOR can access POST /api/v1/skus (hasAuthority('sku:create'))")
  void catalogEditorCanCreateSku() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/skus")
                .with(SecurityTestHelper.withJwtUser("editor", SrhRole.CATALOG_EDITOR)))
        .andExpect(status().isOk())
        .andExpect(content().string("sku-created-successfully"));
  }

  @Test
  @DisplayName("Role CUSTOMER cannot access POST /api/v1/skus (403 Forbidden)")
  void customerCannotCreateSku() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/skus").with(SecurityTestHelper.withJwtUser("customer", SrhRole.CUSTOMER)))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockSrhUser(username = "editor_via_annotation", roles = "CATALOG_EDITOR")
  @DisplayName("WithMockSrhUser annotation grants sku:create for CATALOG_EDITOR")
  void annotationEditorCanCreateSku() throws Exception {
    mockMvc
        .perform(post("/api/v1/skus"))
        .andExpect(status().isOk())
        .andExpect(content().string("sku-created-successfully"));
  }

  @Test
  @WithMockSrhUser(username = "customer_via_annotation", roles = "CUSTOMER")
  @DisplayName("WithMockSrhUser annotation forbids CUSTOMER from creating SKU")
  void annotationCustomerCannotCreateSku() throws Exception {
    mockMvc.perform(post("/api/v1/skus")).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("CUSTOMER can access GET /api/v1/skus with catalog:read permission")
  void customerCanReadCatalog() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/skus").with(SecurityTestHelper.withJwtUser("customer", SrhRole.CUSTOMER)))
        .andExpect(status().isOk())
        .andExpect(content().string("catalog-read-successfully"));
  }

  @ParameterizedTest
  @EnumSource(value = SrhRole.class)
  @DisplayName("Verification across all roles: only CATALOG_EDITOR can access sku:create endpoint")
  void verifyAllRolesForSkuCreateEndpoint(SrhRole role) throws Exception {
    var request =
        post("/api/v1/skus").with(SecurityTestHelper.withJwtUser("user_" + role.name(), role));
    if (role == SrhRole.CATALOG_EDITOR) {
      mockMvc.perform(request).andExpect(status().isOk());
    } else {
      mockMvc.perform(request).andExpect(status().isForbidden());
    }
  }
}
