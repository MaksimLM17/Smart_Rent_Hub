package team.ylab.gateway.ratelimit;

import java.net.InetSocketAddress;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Key resolver for Redis rate limiting in Smart Rent Hub Gateway (SDD v1.1 Section 4.1, 7.2).
 *
 * <p>Isolates token buckets using a multi-tiered strategy:
 *
 * <ol>
 *   <li>Authenticated principal: {@code user:<username>}
 *   <li>Active session: {@code session:<sessionId>}
 *   <li>Anonymous fallback: {@code ip:<clientIp>}
 * </ol>
 */
@Component
public class SrhRateLimitKeyResolver implements KeyResolver {

  @Override
  public Mono<String> resolve(ServerWebExchange exchange) {
    // 1. Authenticated user principal
    return exchange
        .getPrincipal()
        .filter(p -> StringUtils.hasText(p.getName()))
        .map(p -> "user:" + p.getName())
        // 2. Active WebSession ID
        .switchIfEmpty(
            exchange
                .getSession()
                .filter(s -> s.isStarted() && StringUtils.hasText(s.getId()))
                .map(s -> "session:" + s.getId()))
        // 3. Fallback to client IP
        .switchIfEmpty(Mono.fromSupplier(() -> "ip:" + resolveClientIp(exchange)));
  }

  private String resolveClientIp(ServerWebExchange exchange) {
    String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
    if (StringUtils.hasText(xForwardedFor)) {
      String firstIp = xForwardedFor.split(",")[0].trim();
      if (StringUtils.hasText(firstIp)) {
        return firstIp;
      }
    }
    InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
    if (remoteAddress != null && remoteAddress.getAddress() != null) {
      return remoteAddress.getAddress().getHostAddress();
    }
    return "unknown";
  }
}
