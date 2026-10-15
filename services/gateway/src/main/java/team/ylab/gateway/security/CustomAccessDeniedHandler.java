package team.ylab.gateway.security;

import java.nio.charset.StandardCharsets;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import team.ylab.gateway.filter.TraceContextWebFilter;

/**
 * Access denied handler for CSRF token failures and authorization errors in the Gateway BFF.
 *
 * <p>Returns RFC 9457 Problem Details JSON (403 Forbidden) with trace information.
 */
public class CustomAccessDeniedHandler implements ServerAccessDeniedHandler {

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(HttpStatus.FORBIDDEN);
    response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

    String traceId = exchange.getAttribute(TraceContextWebFilter.ATTR_TRACE_ID);
    String detail =
        denied != null && denied.getMessage() != null
            ? denied.getMessage()
            : "Access Denied: CSRF validation failed or insufficient permissions";

    String body =
        String.format(
            "{\"type\":\"urn:problem:csrf:forbidden\",\"title\":\"Forbidden\",\"status\":403,\"detail\":\"%s\",\"traceId\":\"%s\"}",
            detail.replace("\"", "\\\""), traceId != null ? traceId : "");

    DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
    return response.writeWith(Mono.just(buffer));
  }
}
