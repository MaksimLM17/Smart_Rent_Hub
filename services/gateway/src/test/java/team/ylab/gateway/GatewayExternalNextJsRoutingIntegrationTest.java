package team.ylab.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import team.ylab.observability.trace.TraceConstants;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "smartrent.observability.tracing.otlp.enabled=false")
class GatewayExternalNextJsRoutingIntegrationTest {

  private static HttpServer customerWebServer;
  private static HttpServer staffWebServer;
  private static final AtomicReference<String> customerWebTraceId = new AtomicReference<>();
  private static final AtomicReference<String> staffWebTraceId = new AtomicReference<>();

  @LocalServerPort private int gatewayPort;

  private WebTestClient webTestClient;

  @DynamicPropertySource
  static void registerNextJsProperties(DynamicPropertyRegistry registry) throws IOException {
    customerWebServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    customerWebServer.createContext(
        "/",
        exchange -> {
          customerWebTraceId.set(
              exchange.getRequestHeaders().getFirst(TraceConstants.TRACE_ID_HEADER));
          byte[] body =
              ("{\"upstream\":\"customer-web-nextjs\",\"path\":\""
                      + exchange.getRequestURI().getPath()
                      + "\"}")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        });
    customerWebServer.start();

    staffWebServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    staffWebServer.createContext(
        "/staff",
        exchange -> {
          staffWebTraceId.set(
              exchange.getRequestHeaders().getFirst(TraceConstants.TRACE_ID_HEADER));
          byte[] body =
              ("{\"upstream\":\"staff-web-nextjs\",\"path\":\""
                      + exchange.getRequestURI().getPath()
                      + "\"}")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        });
    staffWebServer.start();

    registry.add(
        "srh.gateway.routes.customer-web-uri",
        () -> "http://127.0.0.1:" + customerWebServer.getAddress().getPort());
    registry.add(
        "srh.gateway.routes.staff-web-uri",
        () -> "http://127.0.0.1:" + staffWebServer.getAddress().getPort());
  }

  @AfterAll
  static void stopServers() {
    if (customerWebServer != null) {
      customerWebServer.stop(0);
    }
    if (staffWebServer != null) {
      staffWebServer.stop(0);
    }
  }

  @BeforeEach
  void setUp() {
    customerWebTraceId.set(null);
    staffWebTraceId.set(null);
    webTestClient = WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + gatewayPort).build();
  }

  @Test
  void proxiesCustomerAndStaffWebRequestsToExternalNextJsServers() {
    EntityExchangeResult<String> customerRes =
        webTestClient
            .get()
            .uri("/")
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .exists(TraceConstants.TRACE_ID_HEADER)
            .expectBody(String.class)
            .returnResult();

    String cTrace = customerRes.getResponseHeaders().getFirst(TraceConstants.TRACE_ID_HEADER);
    assertThat(customerRes.getResponseBody()).contains("\"upstream\":\"customer-web-nextjs\"");
    assertThat(customerWebTraceId.get()).isEqualTo(cTrace);

    EntityExchangeResult<String> staffRes =
        webTestClient
            .get()
            .uri("/staff/orders")
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .exists(TraceConstants.TRACE_ID_HEADER)
            .expectBody(String.class)
            .returnResult();

    String sTrace = staffRes.getResponseHeaders().getFirst(TraceConstants.TRACE_ID_HEADER);
    assertThat(staffRes.getResponseBody())
        .contains("\"upstream\":\"staff-web-nextjs\"")
        .contains("\"/staff/orders\"");
    assertThat(staffWebTraceId.get()).isEqualTo(sTrace);
  }
}
