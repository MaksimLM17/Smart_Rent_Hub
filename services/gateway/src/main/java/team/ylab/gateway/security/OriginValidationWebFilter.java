package team.ylab.gateway.security;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import team.ylab.gateway.config.SrhGatewayProperties;
import team.ylab.gateway.filter.TraceContextWebFilter;

/**
 * Validates the {@code Origin} and {@code Referer} headers for mutating HTTP requests and WebSocket
 * upgrades (SDD v1.1 Section 4.1, 9.7).
 *
 * <p>Executes before Spring Security (order {@code -110}). If a mutating request contains an {@code
 * Origin} or {@code Referer} header that does not match the Gateway's own host or the configured
 * allowed origins, the request is immediately rejected with HTTP 403 Forbidden (RFC 9457 Problem
 * Details).
 */
@Slf4j
@Component
@Order(-110)
public class OriginValidationWebFilter implements WebFilter {

  private final SrhGatewayProperties gatewayProperties;

  public OriginValidationWebFilter(SrhGatewayProperties gatewayProperties) {
    this.gatewayProperties = gatewayProperties;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    HttpMethod method = request.getMethod();

    boolean isMutating =
        method == HttpMethod.POST
            || method == HttpMethod.PUT
            || method == HttpMethod.DELETE
            || method == HttpMethod.PATCH;

    boolean isWebSocketUpgrade = "websocket".equalsIgnoreCase(request.getHeaders().getUpgrade());

    boolean isEventStream =
        request.getHeaders().getAccept().stream()
                .anyMatch(mediaType -> mediaType.isCompatibleWith(MediaType.TEXT_EVENT_STREAM))
            || request.getPath().value().startsWith("/api/v1/sse");

    if (!isMutating && !isWebSocketUpgrade && !isEventStream) {
      return chain.filter(exchange);
    }

    String origin = request.getHeaders().getFirst(HttpHeaders.ORIGIN);
    if (StringUtils.hasText(origin)) {
      if (!isOriginAllowed(origin.trim(), exchange)) {
        log.warn("Blocked request with forbidden Origin: {} on path {}", origin, request.getPath());
        return rejectForbiddenOrigin(exchange, origin.trim());
      }
      return chain.filter(exchange);
    }

    String referer = request.getHeaders().getFirst(HttpHeaders.REFERER);
    if (StringUtils.hasText(referer)) {
      String refererOrigin = extractOrigin(referer.trim());
      if (refererOrigin != null && !isOriginAllowed(refererOrigin, exchange)) {
        log.warn(
            "Blocked request with forbidden Referer: {} on path {}", referer, request.getPath());
        return rejectForbiddenOrigin(exchange, refererOrigin);
      }
    }

    return chain.filter(exchange);
  }

  private boolean isOriginAllowed(String origin, ServerWebExchange exchange) {
    // 1. Check against configured allowed origins
    List<String> allowedOrigins = gatewayProperties.getSecurity().getCsrf().getAllowedOrigins();
    if (allowedOrigins != null) {
      for (String allowed : allowedOrigins) {
        if (origin.equalsIgnoreCase(allowed.trim())) {
          return true;
        }
      }
    }

    // 2. Check against request's own Host header and scheme
    ServerHttpRequest request = exchange.getRequest();
    String hostHeader = request.getHeaders().getFirst(HttpHeaders.HOST);
    URI uri = request.getURI();
    String scheme = uri.getScheme() != null ? uri.getScheme() : "http";

    if (StringUtils.hasText(hostHeader)) {
      String selfOrigin = scheme + "://" + hostHeader.trim();
      if (origin.equalsIgnoreCase(selfOrigin)) {
        return true;
      }
    }

    // 3. Fallback comparison of URI host and port
    try {
      URI originUri = URI.create(origin);
      if (originUri.getHost() != null && originUri.getHost().equalsIgnoreCase(uri.getHost())) {
        int originPort =
            originUri.getPort() != -1 ? originUri.getPort() : getDefaultPort(originUri.getScheme());
        int reqPort = uri.getPort() != -1 ? uri.getPort() : getDefaultPort(uri.getScheme());
        if (originPort == reqPort) {
          return true;
        }
      }
    } catch (Exception ignored) {
      // Invalid origin URI format is considered untrusted
    }

    return false;
  }

  private String extractOrigin(String url) {
    try {
      URI uri = URI.create(url);
      if (uri.getScheme() != null && uri.getAuthority() != null) {
        return uri.getScheme() + "://" + uri.getAuthority();
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private int getDefaultPort(String scheme) {
    if ("https".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme)) {
      return 443;
    }
    return 80;
  }

  private Mono<Void> rejectForbiddenOrigin(ServerWebExchange exchange, String origin) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(HttpStatus.FORBIDDEN);
    response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

    String traceId = exchange.getAttribute(TraceContextWebFilter.ATTR_TRACE_ID);
    String body =
        String.format(
            "{\"type\":\"urn:problem:csrf:forbidden-origin\",\"title\":\"Forbidden Origin\",\"status\":403,\"detail\":\"Untrusted or mismatched Origin: %s\",\"traceId\":\"%s\"}",
            origin, traceId != null ? traceId : "");

    DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
    return response.writeWith(Mono.just(buffer));
  }
}
