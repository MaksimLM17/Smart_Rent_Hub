package team.ylab.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.server.session.WebSessionManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import team.ylab.gateway.test.KeycloakMockServer;
import team.ylab.gateway.test.TestSessionHelper;
import team.ylab.testsupport.annotation.EnableRedisTestcontainer;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"smartrent.observability.tracing.otlp.enabled=false"})
@EnableRedisTestcontainer
@DisplayName(
    "T3.4 / S-3: Gateway WebSocket and SSE with Session Cookie Authentication and Origin Validation")
class GatewayWebSocketAndSseIntegrationTest {

  private static KeycloakMockServer keycloakMockServer;
  private static DisposableServer downstreamWsServer;
  private static final AtomicReference<String> downstreamReceivedAuthHeader =
      new AtomicReference<>();

  @LocalServerPort private int gatewayPort;

  @Autowired private WebSessionManager sessionManager;

  private WebTestClient webTestClient;
  private ReactorNettyWebSocketClient webSocketClient;

  @DynamicPropertySource
  static void registerDynamicProperties(DynamicPropertyRegistry registry) throws Exception {
    keycloakMockServer = new KeycloakMockServer();

    // Downstream WebSocket service (simulating notification-live)
    downstreamWsServer =
        HttpServer.create()
            .host("127.0.0.1")
            .port(0)
            .handle(
                (req, res) -> {
                  if (req.uri().startsWith("/ws/staff")) {
                    String auth = req.requestHeaders().get("Authorization");
                    downstreamReceivedAuthHeader.set(auth);
                    return res.sendWebsocket(
                        (in, out) ->
                            out.sendString(
                                in.receive().asString().map(msg -> "downstream:" + msg)));
                  }
                  return res.sendNotFound();
                })
            .bindNow();

    registry.add(
        "srh.gateway.routes.websocket-uri", () -> "ws://127.0.0.1:" + downstreamWsServer.port());
  }

  @AfterAll
  static void tearDownAll() {
    if (downstreamWsServer != null) {
      downstreamWsServer.disposeNow();
    }
    if (keycloakMockServer != null) {
      keycloakMockServer.close();
    }
  }

  @BeforeEach
  void setUp() {
    this.webTestClient =
        WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + gatewayPort)
            .responseTimeout(Duration.ofSeconds(10))
            .build();
    this.webSocketClient = new ReactorNettyWebSocketClient();
    downstreamReceivedAuthHeader.set(null);
  }

  // =========================================================================
  // WebSocket Tests
  // =========================================================================

  @Test
  @DisplayName("WebSocket handshake to /ws/echo without session cookie is rejected with 401")
  void webSocketEcho_unauthenticated_rejectedWith401() {
    URI uri = URI.create("ws://localhost:" + gatewayPort + "/ws/echo");
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.ORIGIN, "http://localhost:3000");

    assertThatThrownBy(
            () ->
                webSocketClient
                    .execute(uri, headers, session -> Mono.empty())
                    .block(Duration.ofSeconds(5)))
        .satisfies(
            throwable -> {
              String msg = throwable.getMessage();
              assertThat(msg).containsAnyOf("401", "Unauthorized", "Invalid handshake response");
            });
  }

  @Test
  @DisplayName(
      "WebSocket handshake to downstream /ws/staff without session cookie is rejected with 401")
  void webSocketStaff_unauthenticated_rejectedWith401() {
    URI uri = URI.create("ws://localhost:" + gatewayPort + "/ws/staff");
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.ORIGIN, "http://localhost:3000");

    assertThatThrownBy(
            () ->
                webSocketClient
                    .execute(uri, headers, session -> Mono.empty())
                    .block(Duration.ofSeconds(5)))
        .satisfies(
            throwable -> {
              String msg = throwable.getMessage();
              assertThat(msg).containsAnyOf("401", "Unauthorized", "Invalid handshake response");
            });
  }

  @Test
  @DisplayName("WebSocket handshake with forbidden Origin is rejected with 403 Forbidden")
  void webSocketEcho_forbiddenOrigin_rejectedWith403() {
    String sessionId =
        TestSessionHelper.createAuthenticatedSession(
            sessionManager, "staff", "alice_staff", List.of("WAREHOUSE_OPERATOR"), "dummy-token");

    URI uri = URI.create("ws://localhost:" + gatewayPort + "/ws/echo");
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.COOKIE, "SESSION=" + sessionId);
    headers.add(HttpHeaders.ORIGIN, "http://malicious-site.com");

    assertThatThrownBy(
            () ->
                webSocketClient
                    .execute(uri, headers, session -> Mono.empty())
                    .block(Duration.ofSeconds(5)))
        .satisfies(
            throwable -> {
              String msg = throwable.getMessage();
              assertThat(msg).containsAnyOf("403", "Forbidden", "Invalid handshake response");
            });
  }

  @Test
  @DisplayName("Authenticated WebSocket handshake to /ws/echo succeeds and echoes text frames")
  void webSocketEcho_authenticatedAllowedOrigin_echoesMessages() {
    String sessionId =
        TestSessionHelper.createAuthenticatedSession(
            sessionManager, "staff", "alice_staff", List.of("WAREHOUSE_OPERATOR"), "dummy-token");

    URI uri = URI.create("ws://localhost:" + gatewayPort + "/ws/echo");
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.COOKIE, "SESSION=" + sessionId);
    headers.add(HttpHeaders.ORIGIN, "http://localhost:3000");

    List<String> receivedMessages = new CopyOnWriteArrayList<>();

    webSocketClient
        .execute(
            uri,
            headers,
            session -> {
              Mono<Void> send =
                  session.send(
                      Mono.just(session.textMessage("ping-test")).concatWith(Mono.never()));

              Mono<Void> receive =
                  session
                      .receive()
                      .take(1)
                      .doOnNext(msg -> receivedMessages.add(msg.getPayloadAsText()))
                      .then();

              return Mono.firstWithSignal(receive, send);
            })
        .block(Duration.ofSeconds(5));

    assertThat(receivedMessages).hasSize(1);
    assertThat(receivedMessages.get(0)).isEqualTo("echo: ping-test");
  }

  @Test
  @DisplayName(
      "Authenticated WebSocket to /ws/staff routes to downstream and injects TokenRelay Bearer token")
  void webSocketStaff_authenticated_routesDownstreamWithTokenRelay() {
    String accessToken = "jwt-access-token-for-downstream-ws";
    String sessionId =
        TestSessionHelper.createAuthenticatedSession(
            sessionManager, "staff", "bob_staff", List.of("WAREHOUSE_OPERATOR"), accessToken);

    URI uri = URI.create("ws://localhost:" + gatewayPort + "/ws/staff");
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.COOKIE, "SESSION=" + sessionId);
    headers.add(HttpHeaders.ORIGIN, "http://localhost:3000");

    List<String> receivedMessages = new CopyOnWriteArrayList<>();

    webSocketClient
        .execute(
            uri,
            headers,
            session -> {
              Mono<Void> send =
                  session.send(
                      Mono.just(session.textMessage("task-assigned")).concatWith(Mono.never()));

              Mono<Void> receive =
                  session
                      .receive()
                      .take(1)
                      .doOnNext(msg -> receivedMessages.add(msg.getPayloadAsText()))
                      .then();

              return Mono.firstWithSignal(receive, send);
            })
        .block(Duration.ofSeconds(5));

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertThat(receivedMessages).hasSize(1);
              assertThat(receivedMessages.get(0)).isEqualTo("downstream:task-assigned");
              assertThat(downstreamReceivedAuthHeader.get())
                  .isNotNull()
                  .isEqualTo("Bearer " + accessToken);
            });
  }

  // =========================================================================
  // SSE (Server-Sent Events) Tests
  // =========================================================================

  @Test
  @DisplayName("SSE stream request without session cookie is rejected with 401 Unauthorized")
  void sseStream_unauthenticated_rejectedWith401() {
    webTestClient
        .get()
        .uri("/api/v1/sse/stream")
        .accept(MediaType.TEXT_EVENT_STREAM)
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(401);
  }

  @Test
  @DisplayName("SSE stream request with forbidden Origin is rejected with 403 Forbidden")
  void sseStream_forbiddenOrigin_rejectedWith403() {
    String sessionId =
        TestSessionHelper.createAuthenticatedSession(
            sessionManager, "customer", "customer_user", List.of("CUSTOMER"), "token");

    webTestClient
        .get()
        .uri("/api/v1/sse/stream")
        .header(HttpHeaders.ORIGIN, "http://evil-attacker.com")
        .cookie("SESSION", sessionId)
        .accept(MediaType.TEXT_EVENT_STREAM)
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo("urn:problem:csrf:forbidden-origin");
  }

  @Test
  @DisplayName(
      "Authenticated SSE stream emits sequence of events with valid session and allowed Origin")
  void sseStream_authenticatedAllowedOrigin_emitsEvents() {
    String sessionId =
        TestSessionHelper.createAuthenticatedSession(
            sessionManager, "staff", "alice_staff", List.of("WAREHOUSE_OPERATOR"), "token");

    Flux<ServerSentEvent<String>> eventFlux =
        webTestClient
            .get()
            .uri("/api/v1/sse/stream?count=3&delayMs=20")
            .cookie("SESSION", sessionId)
            .header(HttpHeaders.ORIGIN, "http://localhost:3000")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
            .returnResult(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
            .getResponseBody();

    List<ServerSentEvent<String>> events = eventFlux.collectList().block(Duration.ofSeconds(5));

    assertThat(events).isNotNull().hasSize(3);
    assertThat(events.get(0).id()).isEqualTo("1");
    assertThat(events.get(0).event()).isEqualTo("srh.notification");
    assertThat(events.get(0).data()).contains("alice_staff").contains("Live event #1");

    assertThat(events.get(1).id()).isEqualTo("2");
    assertThat(events.get(1).data()).contains("Live event #2");

    assertThat(events.get(2).id()).isEqualTo("3");
    assertThat(events.get(2).data()).contains("Live event #3");
  }

  @Test
  @DisplayName("SSE stream respects Last-Event-ID header and resumes sequence")
  void sseStream_withLastEventId_resumesSequence() {
    String sessionId =
        TestSessionHelper.createAuthenticatedSession(
            sessionManager, "staff", "bob_staff", List.of("WAREHOUSE_OPERATOR"), "token");

    Flux<ServerSentEvent<String>> eventFlux =
        webTestClient
            .get()
            .uri("/api/v1/sse/stream?count=2&delayMs=20")
            .cookie("SESSION", sessionId)
            .header("Last-Event-ID", "10")
            .header(HttpHeaders.ORIGIN, "http://localhost:3000")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
            .getResponseBody();

    List<ServerSentEvent<String>> events = eventFlux.collectList().block(Duration.ofSeconds(5));

    assertThat(events).isNotNull().hasSize(2);
    assertThat(events.get(0).id()).isEqualTo("11");
    assertThat(events.get(0).data()).contains("Live event #11");

    assertThat(events.get(1).id()).isEqualTo("12");
    assertThat(events.get(1).data()).contains("Live event #12");
  }

  @Test
  @DisplayName("SSE echo endpoint returns single event echoing payload and authenticated user")
  void sseEcho_authenticated_returnsEchoEvent() {
    String sessionId =
        TestSessionHelper.createAuthenticatedSession(
            sessionManager, "customer", "john_customer", List.of("CUSTOMER"), "token");

    Flux<ServerSentEvent<String>> eventFlux =
        webTestClient
            .get()
            .uri("/api/v1/sse/echo?message=custom-message")
            .cookie("SESSION", sessionId)
            .header(HttpHeaders.ORIGIN, "http://localhost:3000")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
            .getResponseBody();

    List<ServerSentEvent<String>> events = eventFlux.collectList().block(Duration.ofSeconds(5));

    assertThat(events).isNotNull().hasSize(1);
    assertThat(events.get(0).event()).isEqualTo("echo");
    assertThat(events.get(0).data()).contains("echo: custom-message").contains("john_customer");
  }
}
