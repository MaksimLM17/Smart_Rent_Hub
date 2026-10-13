package team.ylab.gateway.stub;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import team.ylab.gateway.config.GatewayRoutingConfig;
import team.ylab.gateway.filter.TraceContextWebFilter;
import team.ylab.observability.trace.TraceConstants;

/**
 * Built-in fallback stub endpoints representing the {@code customer-web} and {@code staff-web}
 * Next.js applications behind the single Gateway origin before Epic E5 (T5.1) is deployed.
 */
@RestController
@RequestMapping("/__stub")
public class NextJsStubController {

  @RequestMapping(
      value = {"/customer-web", "/customer-web/**"},
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<Map<String, Object>> customerWebStub(ServerWebExchange exchange) {
    return Mono.just(buildStubPayload("customer-web", exchange));
  }

  @RequestMapping(
      value = {"/staff-web", "/staff-web/**"},
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<Map<String, Object>> staffWebStub(ServerWebExchange exchange) {
    return Mono.just(buildStubPayload("staff-web", exchange));
  }

  private Map<String, Object> buildStubPayload(String appName, ServerWebExchange exchange) {
    ServerHttpRequest request = exchange.getRequest();
    String originalPath =
        request.getHeaders().getFirst(GatewayRoutingConfig.ORIG_REQUEST_PATH_HEADER);
    if (originalPath == null || originalPath.isBlank()) {
      originalPath = request.getURI().getRawPath();
    }
    String traceId = exchange.getAttribute(TraceContextWebFilter.ATTR_TRACE_ID);
    if (traceId == null) {
      traceId = request.getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER);
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("app", appName);
    body.put("framework", "Next.js (stub)");
    body.put("status", "OK");
    body.put("path", originalPath);
    body.put("traceId", traceId);
    return body;
  }
}
