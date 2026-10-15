package team.ylab.gateway.exception;

import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;
import team.ylab.gateway.filter.TraceContextWebFilter;

/**
 * Global reactive exception handler that converts rate limit rejections (HTTP 429 Too Many
 * Requests) into RFC 9457 Problem Details responses while preserving {@code X-RateLimit-*} headers.
 */
@Slf4j
@Component
@Order(-1)
public class RateLimitWebExceptionHandler implements WebExceptionHandler {

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
    HttpStatusCode status = extractStatusCode(ex);
    if (status != null && status.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
      log.warn("Rate limit exceeded for path {}", exchange.getRequest().getPath());

      ServerHttpResponse response = exchange.getResponse();
      response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
      response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

      // Preserve any X-RateLimit headers attached to the exception
      HttpHeaders exHeaders = extractHeaders(ex);
      if (exHeaders != null) {
        exHeaders.forEach(
            (name, values) -> {
              if (name.toLowerCase().startsWith("x-ratelimit")) {
                response.getHeaders().put(name, values);
              }
            });
      }

      String traceId = exchange.getAttribute(TraceContextWebFilter.ATTR_TRACE_ID);
      String body =
          String.format(
              "{\"type\":\"urn:problem:rate-limit:exceeded\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Rate limit exceeded\",\"traceId\":\"%s\"}",
              traceId != null ? traceId : "");

      DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
      return response.writeWith(Mono.just(buffer));
    }

    return Mono.error(ex);
  }

  private HttpStatusCode extractStatusCode(Throwable ex) {
    if (ex instanceof HttpClientErrorException httpEx) {
      return httpEx.getStatusCode();
    }
    if (ex instanceof ResponseStatusException rse) {
      return rse.getStatusCode();
    }
    return null;
  }

  private HttpHeaders extractHeaders(Throwable ex) {
    if (ex instanceof HttpClientErrorException httpEx) {
      return httpEx.getResponseHeaders();
    }
    if (ex instanceof ResponseStatusException rse) {
      return rse.getHeaders();
    }
    return null;
  }
}
