package team.ylab.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import team.ylab.observability.trace.TraceConstants;

/**
 * Spring Cloud Gateway {@link GlobalFilter} that ensures {@code traceparent} and {@code X-Trace-Id}
 * headers are attached to every proxied downstream request while preserving {@code Idempotency-Key}
 * unchanged (SDD v1.1 Section 4.1).
 */
@Component
public class TraceRelayGlobalFilter implements GlobalFilter, Ordered {

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 10;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String traceId = exchange.getAttribute(TraceContextWebFilter.ATTR_TRACE_ID);
    String traceparent = exchange.getAttribute(TraceContextWebFilter.ATTR_TRACEPARENT);

    if (traceId == null && traceparent == null) {
      return chain.filter(exchange);
    }

    ServerHttpRequest mutatedRequest =
        exchange
            .getRequest()
            .mutate()
            .headers(
                headers -> {
                  if (traceId != null) {
                    headers.set(TraceConstants.TRACE_ID_HEADER, traceId);
                  }
                  if (traceparent != null) {
                    headers.set(TraceConstants.TRACEPARENT_HEADER, traceparent);
                  }
                })
            .build();

    return chain.filter(exchange.mutate().request(mutatedRequest).build());
  }
}
