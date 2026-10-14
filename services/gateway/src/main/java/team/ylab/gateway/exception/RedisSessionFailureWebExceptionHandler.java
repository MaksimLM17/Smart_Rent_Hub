package team.ylab.gateway.exception;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;
import team.ylab.gateway.config.SrhGatewayProperties;

/**
 * Exception handler providing graceful degradation when Redis session storage experiences an outage
 * (SDD v1.1 Section 11, Spike S-3).
 *
 * <p>Rather than failing with HTTP 500 Internal Server Error, expires the session cookie and
 * redirects browser requests to the appropriate login screen (or returns 401 Problem Details for
 * API callers).
 */
@Slf4j
@Component
@Order(-2)
public class RedisSessionFailureWebExceptionHandler implements WebExceptionHandler {

  private final SrhGatewayProperties gatewayProperties;

  public RedisSessionFailureWebExceptionHandler(SrhGatewayProperties gatewayProperties) {
    this.gatewayProperties = gatewayProperties;
  }

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
    if (!isRedisError(ex)) {
      return Mono.error(ex);
    }

    log.warn(
        "Redis session store failure detected ({}: {}). Initiating graceful login redirect fallback.",
        ex.getClass().getSimpleName(),
        ex.getMessage());

    ServerHttpRequest request = exchange.getRequest();
    ServerHttpResponse response = exchange.getResponse();

    // Invalidate and expire session cookie on the client
    ResponseCookie expiredCookie =
        ResponseCookie.from(gatewayProperties.getSession().getCookieName(), "")
            .path(gatewayProperties.getSession().getCookiePath())
            .maxAge(0)
            .httpOnly(true)
            .sameSite(gatewayProperties.getSession().getCookieSameSite())
            .build();
    response.addCookie(expiredCookie);

    String path = request.getURI().getPath();
    String accept = request.getHeaders().getFirst(HttpHeaders.ACCEPT);

    if (path.startsWith("/api/")
        || (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE))) {
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
      byte[] body =
          "{\"type\":\"about:blank\",\"title\":\"Session Store Unavailable\",\"status\":401,\"detail\":\"Session store is temporarily unavailable, please re-authenticate\"}"
              .getBytes(StandardCharsets.UTF_8);
      DataBuffer buffer = response.bufferFactory().wrap(body);
      return response.writeWith(Mono.just(buffer));
    }

    String redirectPath =
        path.startsWith("/staff")
            ? "/oauth2/authorization/staff"
            : "/oauth2/authorization/customer";

    response.setStatusCode(HttpStatus.FOUND);
    response.getHeaders().setLocation(URI.create(redirectPath));
    return response.setComplete();
  }

  private boolean isRedisError(Throwable ex) {
    Throwable current = ex;
    while (current != null) {
      String className = current.getClass().getName();
      if (className.contains("Redis")
          || className.contains("lettuce")
          || className.contains("Lettuce")) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
