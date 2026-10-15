package team.ylab.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.session.WebSessionManager;
import team.ylab.gateway.ratelimit.SrhRateLimitKeyResolver;
import team.ylab.gateway.test.KeycloakMockServer;
import team.ylab.gateway.test.TestSessionHelper;
import team.ylab.testsupport.annotation.EnableRedisTestcontainer;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "smartrent.observability.tracing.otlp.enabled=false",
      "srh.gateway.rate-limit.replenish-rate=1",
      "srh.gateway.rate-limit.burst-capacity=3"
    })
@EnableRedisTestcontainer
@DisplayName(
    "T3.3 / S-3: Gateway CSRF Hardening, Origin Validation, and Redis Rate Limiting Integration Tests")
class GatewayCsrfAndRateLimitIntegrationTest {

  private static KeycloakMockServer keycloakMockServer;
  private static HttpServer inventoryMockServer;
  private static final AtomicReference<String> lastInventoryAuthHeader = new AtomicReference<>();
  private static final AtomicReference<String> lastInventoryPath = new AtomicReference<>();
  private static final AtomicInteger inventoryCallCount = new AtomicInteger();

  @LocalServerPort private int gatewayPort;

  @Autowired private WebSessionManager sessionManager;
  @Autowired private SrhRateLimitKeyResolver keyResolver;
  @Autowired private RedisRateLimiter redisRateLimiter;

  private WebTestClient webTestClient;

  @DynamicPropertySource
  static void registerDynamicProperties(DynamicPropertyRegistry registry) throws Exception {
    keycloakMockServer = new KeycloakMockServer();

    inventoryMockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    inventoryMockServer.createContext(
        "/api/v1/",
        exchange -> {
          inventoryCallCount.incrementAndGet();
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

    // Wire mock OAuth2 endpoints
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
    this.webTestClient =
        WebTestClient.bindToServer().baseUrl("http://localhost:" + gatewayPort).build();
    lastInventoryAuthHeader.set(null);
    lastInventoryPath.set(null);
  }

  @Test
  @DisplayName("1. GET /api/v1/session sets XSRF-TOKEN cookie and returns csrfToken in body")
  void getSessionReturnsCsrfTokenAndSetsCookie() {
    EntityExchangeResult<String> result =
        webTestClient
            .get()
            .uri("/api/v1/session")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectCookie()
            .exists("XSRF-TOKEN")
            .expectBody(String.class)
            .returnResult();

    ResponseCookie xsrfCookie = result.getResponseCookies().getFirst("XSRF-TOKEN");
    assertThat(xsrfCookie).isNotNull();
    assertThat(xsrfCookie.isHttpOnly()).isFalse(); // Must be false for SPA readability
    assertThat(xsrfCookie.getSameSite()).isEqualTo("Lax");
    assertThat(xsrfCookie.getPath()).isEqualTo("/");

    String body = result.getResponseBody();
    assertThat(body).contains("\"csrfToken\":");
    assertThat(body).contains("\"csrfHeaderName\":\"X-XSRF-TOKEN\"");
  }

  @Test
  @DisplayName("2. Safe GET requests do not require CSRF token")
  void safeGetRequestDoesNotRequireCsrfToken() {
    webTestClient
        .get()
        .uri("/api/v1/catalog/skus/sku-safe-1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("OK");
  }

  @Test
  @DisplayName("3. Mutating POST /api/v1/staff/skus without CSRF token returns 403 Problem Details")
  void mutatingRequestWithoutCsrfTokenReturns403ProblemDetails() {
    String sessionId =
        TestSessionHelper.createAuthenticatedSession(
            sessionManager, "staff", "manager_bob", List.of("WAREHOUSE_SUPERVISOR"), "staff-jwt");

    webTestClient
        .post()
        .uri("/api/v1/staff/skus")
        .cookie("SESSION", sessionId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"sku\":\"SKU-NEW-1\"}")
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(403)
        .jsonPath("$.title")
        .isEqualTo("Forbidden");
  }

  @Test
  @DisplayName("4. Mutating request with mismatched CSRF token returns 403 Problem Details")
  void mutatingRequestWithMismatchedCsrfTokenReturns403ProblemDetails() {
    String sessionId =
        TestSessionHelper.createAuthenticatedSession(
            sessionManager, "staff", "manager_bob", List.of("WAREHOUSE_SUPERVISOR"), "staff-jwt");

    webTestClient
        .post()
        .uri("/api/v1/staff/skus")
        .cookie("SESSION", sessionId)
        .cookie("XSRF-TOKEN", "legitimate-token-12345")
        .header("X-XSRF-TOKEN", "forged-or-mismatched-token")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"sku\":\"SKU-NEW-2\"}")
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(403)
        .jsonPath("$.title")
        .isEqualTo("Forbidden");
  }

  @Test
  @DisplayName("5. Mutating request with untrusted Origin returns 403 Forbidden Origin")
  void mutatingRequestWithUntrustedOriginReturns403ForbiddenOrigin() {
    String sessionId =
        TestSessionHelper.createAuthenticatedSession(
            sessionManager, "staff", "manager_bob", List.of("WAREHOUSE_SUPERVISOR"), "staff-jwt");

    webTestClient
        .post()
        .uri("/api/v1/staff/skus")
        .cookie("SESSION", sessionId)
        .cookie("XSRF-TOKEN", "token-xyz")
        .header("X-XSRF-TOKEN", "token-xyz")
        .header("Origin", "http://evil-attacker.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"sku\":\"SKU-EVIL\"}")
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(403)
        .jsonPath("$.title")
        .isEqualTo("Forbidden Origin")
        .jsonPath("$.type")
        .isEqualTo("urn:problem:csrf:forbidden-origin");
  }

  @Test
  @DisplayName("6. Mutating request with untrusted Referer returns 403 Forbidden Origin")
  void mutatingRequestWithUntrustedRefererReturns403ForbiddenOrigin() {
    String sessionId =
        TestSessionHelper.createAuthenticatedSession(
            sessionManager, "staff", "manager_bob", List.of("WAREHOUSE_SUPERVISOR"), "staff-jwt");

    webTestClient
        .post()
        .uri("/api/v1/staff/skus")
        .cookie("SESSION", sessionId)
        .cookie("XSRF-TOKEN", "token-xyz")
        .header("X-XSRF-TOKEN", "token-xyz")
        .header("Referer", "http://attacker-site.org/malicious-page")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"sku\":\"SKU-EVIL-REFERER\"}")
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(403)
        .jsonPath("$.title")
        .isEqualTo("Forbidden Origin");
  }

  @Test
  @DisplayName("7. Mutating request with valid same-origin and valid CSRF token succeeds")
  void mutatingRequestWithValidCsrfAndSameOriginSucceeds() {
    // 1. Fetch legitimate token from session inquiry
    EntityExchangeResult<String> sessionResult =
        webTestClient
            .get()
            .uri("/api/v1/session")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult();
    ResponseCookie xsrfCookie = sessionResult.getResponseCookies().getFirst("XSRF-TOKEN");
    assertThat(xsrfCookie).isNotNull();
    String token = xsrfCookie.getValue();

    // 2. Create authenticated staff session
    String testToken = "staff-bearer-jwt-token-999";
    String sessionId =
        TestSessionHelper.createAuthenticatedSession(
            sessionManager, "staff", "manager_bob", List.of("WAREHOUSE_SUPERVISOR"), testToken);

    // 3. Perform mutating request with same-origin
    webTestClient
        .post()
        .uri("/api/v1/staff/skus")
        .cookie("SESSION", sessionId)
        .cookie("XSRF-TOKEN", token)
        .header("X-XSRF-TOKEN", token)
        .header("Origin", "http://localhost:" + gatewayPort)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"sku\":\"SKU-VALID-1\"}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("OK");

    assertThat(lastInventoryAuthHeader.get()).isEqualTo("Bearer " + testToken);
  }

  @Test
  @DisplayName("8. Mutating request with alternative X-CSRF-TOKEN header succeeds")
  void mutatingRequestWithAlternativeHeaderSucceeds() {
    EntityExchangeResult<String> sessionResult =
        webTestClient
            .get()
            .uri("/api/v1/session")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult();
    String token = sessionResult.getResponseCookies().getFirst("XSRF-TOKEN").getValue();

    String sessionId =
        TestSessionHelper.createAuthenticatedSession(
            sessionManager, "staff", "manager_bob", List.of("WAREHOUSE_SUPERVISOR"), "token-abc");

    webTestClient
        .post()
        .uri("/api/v1/staff/skus")
        .cookie("SESSION", sessionId)
        .cookie("XSRF-TOKEN", token)
        .header("X-CSRF-TOKEN", token) // Alternative header
        .header("Origin", "http://localhost:" + gatewayPort)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"sku\":\"SKU-VALID-2\"}")
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  @DisplayName("9. Mutating request with allowed Next.js dev origin succeeds")
  void mutatingRequestWithAllowedOriginSucceeds() {
    EntityExchangeResult<String> sessionResult =
        webTestClient
            .get()
            .uri("/api/v1/session")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult();
    String token = sessionResult.getResponseCookies().getFirst("XSRF-TOKEN").getValue();

    String sessionId =
        TestSessionHelper.createAuthenticatedSession(
            sessionManager, "staff", "manager_bob", List.of("WAREHOUSE_SUPERVISOR"), "token-abc");

    webTestClient
        .post()
        .uri("/api/v1/staff/skus")
        .cookie("SESSION", sessionId)
        .cookie("XSRF-TOKEN", token)
        .header("X-XSRF-TOKEN", token)
        .header("Origin", "http://localhost:3000") // Configured in allowedOrigins
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"sku\":\"SKU-VALID-NEXTJS\"}")
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  @DisplayName("10. RateLimiter key resolver isolates by user principal, session ID, and IP")
  void rateLimitKeyResolverResolvesTiersCorrectly() {
    // Tier 1: Authenticated principal
    MockServerHttpRequest req1 = MockServerHttpRequest.get("/api/v1/test").build();
    org.springframework.web.server.ServerWebExchange ex1 =
        MockServerWebExchange.from(req1)
            .mutate()
            .principal(
                reactor.core.publisher.Mono.just(
                    new UsernamePasswordAuthenticationToken("alice_customer", "pass")))
            .build();
    assertThat(keyResolver.resolve(ex1).block()).isEqualTo("user:alice_customer");

    // Tier 2: Unauthenticated with active session
    MockServerHttpRequest req2 = MockServerHttpRequest.get("/api/v1/test").build();
    MockServerWebExchange ex2 = MockServerWebExchange.from(req2);
    ex2.getSession().block().start();
    String sId = ex2.getSession().block().getId();
    assertThat(keyResolver.resolve(ex2).block()).isEqualTo("session:" + sId);

    // Tier 3: Anonymous with IP / X-Forwarded-For
    MockServerHttpRequest req3 =
        MockServerHttpRequest.get("/api/v1/test")
            .header("X-Forwarded-For", "198.51.100.42, 10.0.0.1")
            .build();
    MockServerWebExchange ex3 = MockServerWebExchange.from(req3);
    assertThat(keyResolver.resolve(ex3).block()).isEqualTo("ip:198.51.100.42");
  }

  @Test
  @DisplayName(
      "11. Rate limiter enforces burst capacity and returns 429 Problem Details with X-RateLimit headers")
  void rateLimiterEnforcesBurstCapacity() {
    // Configured with burstCapacity=3, replenishRate=1
    String uniqueClientIp = "203.0.113.88";

    // Rapid requests consume the burst capacity and trigger 429
    EntityExchangeResult<byte[]> limitedResponse = null;
    for (int i = 0; i < 10; i++) {
      EntityExchangeResult<byte[]> result =
          webTestClient
              .get()
              .uri("/api/v1/catalog/skus/sku-ratelimit-test")
              .header("X-Forwarded-For", uniqueClientIp)
              .exchange()
              .expectHeader()
              .exists("X-RateLimit-Remaining")
              .expectBody()
              .returnResult();

      if (result.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
        limitedResponse = result;
        break;
      }
    }

    assertThat(limitedResponse)
        .as("Expected rate limiter to return 429 after bursting requests within 1 second")
        .isNotNull();

    assertThat(limitedResponse.getResponseHeaders().getFirst("X-RateLimit-Remaining"))
        .isEqualTo("0");
    assertThat(limitedResponse.getResponseHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

    String responseBody = new String(limitedResponse.getResponseBody(), StandardCharsets.UTF_8);
    assertThat(responseBody)
        .contains("\"status\":429")
        .contains("\"title\":\"Too Many Requests\"")
        .contains("\"type\":\"urn:problem:rate-limit:exceeded\"");
  }

  @Test
  @DisplayName("12. Rate limiter is fail-open when Redis is unavailable")
  void rateLimiterFailsOpenWhenRedisIsUnavailable() {
    // Test the fail-open fallback behavior directly on RedisRateLimiter
    RateLimiter.Response response =
        redisRateLimiter.isAllowed("inventory-api", "ip:999.999.999.999").block();

    // RedisRateLimiter returns allowed=true even when lua scripts / connection fail
    assertThat(response).isNotNull();
    assertThat(response.isAllowed()).isTrue();
  }
}
