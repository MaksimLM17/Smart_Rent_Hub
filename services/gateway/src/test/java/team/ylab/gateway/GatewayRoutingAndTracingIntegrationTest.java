package team.ylab.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import team.ylab.observability.trace.TraceConstants;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {
      GatewayApplication.class,
      GatewayRoutingAndTracingIntegrationTest.TestSpanExporterConfig.class
    })
class GatewayRoutingAndTracingIntegrationTest {

  private static HttpServer inventoryMockServer;
  private static final AtomicReference<String> lastInventoryTraceId = new AtomicReference<>();
  private static final AtomicReference<String> lastInventoryTraceparent = new AtomicReference<>();
  private static final AtomicReference<String> lastInventoryIdempotencyKey =
      new AtomicReference<>();
  private static final AtomicReference<String> lastInventoryPath = new AtomicReference<>();

  @LocalServerPort private int gatewayPort;

  @Autowired private InMemorySpanExporter spanExporter;

  private WebTestClient webTestClient;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) throws IOException {
    inventoryMockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    inventoryMockServer.createContext(
        "/api/v1/",
        exchange -> {
          lastInventoryPath.set(exchange.getRequestURI().getPath());
          lastInventoryTraceId.set(
              exchange.getRequestHeaders().getFirst(TraceConstants.TRACE_ID_HEADER));
          lastInventoryTraceparent.set(
              exchange.getRequestHeaders().getFirst(TraceConstants.TRACEPARENT_HEADER));
          lastInventoryIdempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));

          byte[] body =
              ("{\"service\":\"inventory\",\"path\":\""
                      + exchange.getRequestURI().getPath()
                      + "\"}")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        });
    inventoryMockServer.start();

    int inventoryPort = inventoryMockServer.getAddress().getPort();
    registry.add("srh.gateway.routes.inventory-uri", () -> "http://127.0.0.1:" + inventoryPort);
  }

  @AfterAll
  static void stopInventoryServer() {
    if (inventoryMockServer != null) {
      inventoryMockServer.stop(0);
    }
  }

  @BeforeEach
  void setUp() {
    spanExporter.reset();
    lastInventoryTraceId.set(null);
    lastInventoryTraceparent.set(null);
    lastInventoryIdempotencyKey.set(null);
    lastInventoryPath.set(null);
    webTestClient = WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + gatewayPort).build();
  }

  @Test
  void routesInventoryRequestGeneratesTraceIdPropagatesHeadersAndExportsSpan() {
    EntityExchangeResult<String> result =
        webTestClient
            .get()
            .uri("/api/v1/catalog/skus/sku-42")
            .header("Idempotency-Key", "idem-key-123")
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .exists(TraceConstants.TRACE_ID_HEADER)
            .expectBody(String.class)
            .returnResult();

    String responseTraceId = result.getResponseHeaders().getFirst(TraceConstants.TRACE_ID_HEADER);
    assertThat(responseTraceId).isNotNull().matches("^[0-9a-f]{32}$");
    assertThat(result.getResponseBody())
        .contains("\"service\":\"inventory\"")
        .contains("/api/v1/catalog/skus/sku-42");

    // Verify headers received by downstream inventory service
    assertThat(lastInventoryPath.get()).isEqualTo("/api/v1/catalog/skus/sku-42");
    assertThat(lastInventoryTraceId.get()).isEqualTo(responseTraceId);
    assertThat(lastInventoryTraceparent.get())
        .isNotNull()
        .matches("^00-" + responseTraceId + "-[0-9a-f]{16}-0[13]$");
    assertThat(lastInventoryIdempotencyKey.get()).isEqualTo("idem-key-123");

    // Verify span exported to OpenTelemetry SpanExporter (Jaeger)
    assertThat(spanExporter.getExportedSpans())
        .anySatisfy(
            span -> {
              assertThat(span.getTraceId()).isEqualTo(responseTraceId);
              assertThat(span.getName()).isEqualTo("HTTP GET /api/v1/catalog/skus/sku-42");
            });
  }

  @Test
  void preservesIncomingW3CTraceparentAcrossGatewayAndInventory() {
    String incomingTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";
    String incomingParentSpanId = "00f067aa0ba902b7";
    String incomingTraceparent = "00-" + incomingTraceId + "-" + incomingParentSpanId + "-01";

    webTestClient
        .get()
        .uri("/api/v1/staff/skus")
        .header(TraceConstants.TRACEPARENT_HEADER, incomingTraceparent)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals(TraceConstants.TRACE_ID_HEADER, incomingTraceId)
        .expectBody(String.class)
        .returnResult();

    assertThat(lastInventoryTraceId.get()).isEqualTo(incomingTraceId);
    assertThat(lastInventoryTraceparent.get())
        .startsWith("00-" + incomingTraceId + "-")
        .doesNotContain(incomingParentSpanId);

    assertThat(spanExporter.getExportedSpans())
        .anySatisfy(
            span -> {
              assertThat(span.getTraceId()).isEqualTo(incomingTraceId);
              assertThat(span.getParentSpanId()).isEqualTo(incomingParentSpanId);
            });
  }

  @Test
  void routesRootAndStaffPathsToBuiltInNextJsStubsWithTraceId() {
    EntityExchangeResult<String> customerResult =
        webTestClient
            .get()
            .uri("/catalog")
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .exists(TraceConstants.TRACE_ID_HEADER)
            .expectBody(String.class)
            .returnResult();

    String customerTraceId =
        customerResult.getResponseHeaders().getFirst(TraceConstants.TRACE_ID_HEADER);
    assertThat(customerTraceId).matches("^[0-9a-f]{32}$");
    assertThat(customerResult.getResponseBody())
        .contains("\"app\":\"customer-web\"")
        .contains("\"path\":\"/catalog\"")
        .contains(customerTraceId);

    EntityExchangeResult<String> staffResult =
        webTestClient
            .get()
            .uri("/staff/skus")
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .exists(TraceConstants.TRACE_ID_HEADER)
            .expectBody(String.class)
            .returnResult();

    String staffTraceId = staffResult.getResponseHeaders().getFirst(TraceConstants.TRACE_ID_HEADER);
    assertThat(staffTraceId).matches("^[0-9a-f]{32}$");
    assertThat(staffResult.getResponseBody())
        .contains("\"app\":\"staff-web\"")
        .contains("\"path\":\"/staff/skus\"")
        .contains(staffTraceId);
  }

  @TestConfiguration
  static class TestSpanExporterConfig {

    @Bean
    @Primary
    public InMemorySpanExporter inMemorySpanExporter() {
      return new InMemorySpanExporter();
    }
  }

  static class InMemorySpanExporter implements SpanExporter {
    private final List<SpanData> spans = new CopyOnWriteArrayList<>();

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
      this.spans.addAll(spans);
      return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
      return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
      this.spans.clear();
      return CompletableResultCode.ofSuccess();
    }

    List<SpanData> getExportedSpans() {
      return List.copyOf(spans);
    }

    void reset() {
      spans.clear();
    }
  }
}
