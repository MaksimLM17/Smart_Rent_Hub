package team.ylab.gateway.security;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import team.ylab.gateway.filter.TraceContextWebFilter;

/**
 * Authentication entry point for unauthenticated requests in the Gateway BFF.
 *
 * <p>Returns RFC 9457 Problem Details JSON (401 Unauthorized) for API requests, and redirects
 * browser requests to {@code /oauth2/authorization/staff} for staff paths or {@code
 * /oauth2/authorization/customer} for customer paths.
 */
public class CustomAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

  @Override
  public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
    ServerHttpRequest request = exchange.getRequest();
    String path = request.getURI().getPath();
    String accept = request.getHeaders().getFirst(HttpHeaders.ACCEPT);

    if (path.startsWith("/api/")
        || (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE))) {
      ServerHttpResponse response = exchange.getResponse();
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

      String traceId = exchange.getAttribute(TraceContextWebFilter.ATTR_TRACE_ID);
      String body =
          String.format(
              "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"Authentication required\",\"traceId\":\"%s\"}",
              traceId != null ? traceId : "");
      DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
      return response.writeWith(Mono.just(buffer));
    }

    String redirectPath =
        path.startsWith("/staff")
            ? "/oauth2/authorization/staff"
            : "/oauth2/authorization/customer";

    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(HttpStatus.FOUND);
    response.getHeaders().setLocation(URI.create(redirectPath));
    return response.setComplete();
  }
}
