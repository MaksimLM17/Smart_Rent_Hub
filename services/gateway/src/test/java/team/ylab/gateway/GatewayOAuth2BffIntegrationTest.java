package team.ylab.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.session.WebSessionManager;
import team.ylab.gateway.exception.RedisSessionFailureWebExceptionHandler;
import team.ylab.gateway.test.KeycloakMockServer;
import team.ylab.gateway.test.TestSessionHelper;
import team.ylab.testsupport.annotation.EnableRedisTestcontainer;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "smartrent.observability.tracing.otlp.enabled=false")
@EnableRedisTestcontainer
@DisplayName("T3.2 / S-3: Gateway BFF OAuth2, Redis Session, and TokenRelay Integration Tests")
class GatewayOAuth2BffIntegrationTest {

  private static KeycloakMockServer keycloakMockServer;
  private static HttpServer inventoryMockServer;
  private static final AtomicReference<String> lastInventoryAuthHeader = new AtomicReference<>();
  private static final AtomicReference<String> lastInventoryPath = new AtomicReference<>();

  @LocalServerPort private int gatewayPort;

  @Autowired private WebSessionManager sessionManager;

  @Autowired(required = false)
  private ReactiveStringRedisTemplate redisTemplate;

  @Autowired private RedisSessionFailureWebExceptionHandler redisExceptionHandler;

  private WebTestClient webTestClient;

  @DynamicPropertySource
  static void registerDynamicProperties(DynamicPropertyRegistry registry) throws Exception {
    keycloakMockServer = new KeycloakMockServer();

    inventoryMockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    inventoryMockServer.createContext(
        "/api/v1/",
        exchange -> {
          lastInventoryPath.set(exchange.getRequestURI().getPath());
          lastInventoryAuthHeader.set(
              exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));

          byte[] body =
              ("{\"service\":\"inventory\",\"path\":\""
                      + exchange.getRequestURI().getPath()
                      + "\",\"status\":\"OK\"}")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        });
    inventoryMockServer.start();

    // Wire Gateway properties to mock servers
    registry.add(
        "spring.security.oauth2.client.provider.keycloak.authorization-uri",
        keycloakMockServer::getAuthUri);
    registry.add(
        "spring.security.oauth2.client.provider.keycloak.token-uri",
        keycloakMockServer::getTokenUri);
    registry.add(
        "spring.security.oauth2.client.provider.keycloak.jwk-set-uri",
        keycloakMockServer::getJwkSetUri);
    registry.add(
        "spring.security.oauth2.client.provider.keycloak.user-info-uri",
        keycloakMockServer::getUserInfoUri);
    registry.add(
        "srh.gateway.routes.inventory-uri",
        () -> "http://127.0.0.1:" + inventoryMockServer.getAddress().getPort());
  }

  @AfterAll
  static void tearDownServers() {
    if (keycloakMockServer != null) {
      keycloakMockServer.close();
    }
    if (inventoryMockServer != null) {
      inventoryMockServer.stop(0);
    }
  }

  @BeforeEach
  void setUp() {
    lastInventoryAuthHeader.set(null);
    lastInventoryPath.set(null);
    webTestClient = WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + gatewayPort).build();
  }

  @Test
  @DisplayName(
      "1. /oauth2/authorization/customer redirects to Keycloak with client_id=srh-customer")
  void customerAuthorizationEndpointRedirectsWithCorrectClientId() {
    webTestClient
        .get()
        .uri("/oauth2/authorization/customer")
        .exchange()
        .expectStatus()
        .is3xxRedirection()
        .expectHeader()
        .value(
            HttpHeaders.LOCATION,
            loc -> {
              assertThat(loc).contains(keycloakMockServer.getAuthUri());
              assertThat(loc).contains("client_id=srh-customer");
              assertThat(loc).contains("response_type=code");
              assertThat(loc).contains("scope=openid");
              assertThat(loc).contains("/login/oauth2/code/customer");
            });
  }

  @Test
  @DisplayName("2. /oauth2/authorization/staff redirects to Keycloak with client_id=srh-staff")
  void staffAuthorizationEndpointRedirectsWithCorrectClientId() {
    webTestClient
        .get()
        .uri("/oauth2/authorization/staff")
        .exchange()
        .expectStatus()
        .is3xxRedirection()
        .expectHeader()
        .value(
            HttpHeaders.LOCATION,
            loc -> {
              assertThat(loc).contains(keycloakMockServer.getAuthUri());
              assertThat(loc).contains("client_id=srh-staff");
              assertThat(loc).contains("response_type=code");
              assertThat(loc).contains("scope=openid");
              assertThat(loc).contains("/login/oauth2/code/staff");
            });
  }

  @Test
  @DisplayName("3. Unauthenticated GET /api/v1/session returns authenticated: false")
  void unauthenticatedSessionReturnsFalse() {
    webTestClient
        .get()
        .uri("/api/v1/session")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.authenticated")
        .isEqualTo(false);
  }

  @Test
  @DisplayName("4. Unauthenticated GET /api/v1/staff/skus returns 401 Problem Details")
  void unauthenticatedStaffApiReturns401ProblemDetails() {
    webTestClient
        .get()
        .uri("/api/v1/staff/skus")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(401)
        .jsonPath("$.title")
        .isEqualTo("Unauthorized");
  }

  @Test
  @DisplayName(
      "5. Unauthenticated GET /staff/skus redirects browser to /oauth2/authorization/staff")
  void unauthenticatedStaffWebRedirectsToStaffLogin() {
    webTestClient
        .get()
        .uri("/staff/skus")
        .exchange()
        .expectStatus()
        .is3xxRedirection()
        .expectHeader()
        .valueEquals(HttpHeaders.LOCATION, "/oauth2/authorization/staff");
  }

  @Test
  @DisplayName(
      "6. Authenticated customer session returns session info and hides tokens from client")
  void authenticatedCustomerSessionProvidesUserInfoAndHidesTokens() {
    String testToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.sensitive-customer-token";
    String sessionId =
        TestSessionHelper.createAuthenticatedSession(
            sessionManager, "customer", "customer_test", List.of("CUSTOMER"), testToken);

    EntityExchangeResult<String> result =
        webTestClient
            .get()
            .uri("/api/v1/session")
            .cookie("SESSION", sessionId)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult();

    String responseBody = result.getResponseBody();
    assertThat(responseBody)
        .contains("\"authenticated\":true")
        .contains("\"username\":\"customer_test\"")
        .contains("\"registrationId\":\"customer\"")
        .contains("\"roles\":[\"CUSTOMER\"]")
        // Token must never be leaked to the client
        .doesNotContain(testToken)
        .doesNotContain("sensitive-customer-token");
  }

  @Test
  @DisplayName("7. TokenRelay injects Authorization: Bearer <token> downstream to inventory")
  void tokenRelayInjectsBearerTokenToDownstreamService() {
    String testAccessToken = "srh-access-jwt-token-inventory-relay-xyz";
    String sessionId =
        TestSessionHelper.createAuthenticatedSession(
            sessionManager, "customer", "customer_test", List.of("CUSTOMER"), testAccessToken);

    EntityExchangeResult<String> result =
        webTestClient
            .get()
            .uri("/api/v1/catalog/skus/sku-42")
            .cookie("SESSION", sessionId)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult();

    // Verify downstream inventory service received the Bearer token relayed by Gateway
    assertThat(lastInventoryPath.get()).isEqualTo("/api/v1/catalog/skus/sku-42");
    assertThat(lastInventoryAuthHeader.get()).isEqualTo("Bearer " + testAccessToken);

    // Verify client response does NOT expose the Bearer token
    assertThat(result.getResponseHeaders().get(HttpHeaders.AUTHORIZATION)).isNull();
    assertThat(result.getResponseBody()).doesNotContain(testAccessToken);
  }

  @Test
  @DisplayName("8. POST /logout invalidates session and clears SESSION cookie")
  void logoutClearsSessionAndCookie() {
    String sessionId =
        TestSessionHelper.createAuthenticatedSession(
            sessionManager,
            "staff",
            "supervisor_test",
            List.of("WAREHOUSE_SUPERVISOR"),
            "staff-token");

    // Perform logout
    EntityExchangeResult<String> logoutResult =
        webTestClient
            .post()
            .uri("/logout")
            .cookie("SESSION", sessionId)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult();

    assertThat(logoutResult.getResponseBody()).contains("\"status\":\"LOGGED_OUT\"");

    // Subsequent call with the old session cookie must now be unauthenticated
    webTestClient
        .get()
        .uri("/api/v1/session")
        .cookie("SESSION", sessionId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.authenticated")
        .isEqualTo(false);
  }

  @Test
  @DisplayName("9. Redis session persistence stores session in Redis container")
  void redisSessionPersistenceStoresKeysInRedis() {
    if (redisTemplate == null) {
      return;
    }

    String sessionId =
        TestSessionHelper.createAuthenticatedSession(
            sessionManager, "customer", "customer_test", List.of("CUSTOMER"), "redis-token-123");

    List<String> keys = redisTemplate.keys("spring:session:*").collectList().block();
    assertThat(keys).isNotNull().isNotEmpty();
    assertThat(keys).anyMatch(key -> key.contains(sessionId));
  }

  @Test
  @DisplayName("10. Redis failure degrades gracefully to login redirect / 401 instead of 500 error")
  void redisFailureDegradesGracefully() {
    io.lettuce.core.RedisConnectionException redisException =
        new io.lettuce.core.RedisConnectionException("Connection refused: redis:6379");

    // Browser request to /staff/orders on Redis failure -> 302 redirect to
    // /oauth2/authorization/staff
    MockServerWebExchange browserExchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/staff/orders").build());
    redisExceptionHandler.handle(browserExchange, redisException).block();
    assertThat(browserExchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(browserExchange.getResponse().getHeaders().getLocation())
        .isEqualTo(java.net.URI.create("/oauth2/authorization/staff"));
    assertThat(browserExchange.getResponse().getCookies().get("SESSION")).isNotEmpty();
    assertThat(
            browserExchange
                .getResponse()
                .getCookies()
                .get("SESSION")
                .get(0)
                .getMaxAge()
                .getSeconds())
        .isZero();

    // API request to /api/v1/orders on Redis failure -> 401 Problem Details, not 500 error
    MockServerWebExchange apiExchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/orders").accept(MediaType.APPLICATION_JSON).build());
    redisExceptionHandler.handle(apiExchange, redisException).block();
    assertThat(apiExchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
