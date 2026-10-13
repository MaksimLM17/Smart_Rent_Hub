package team.ylab.gateway.filter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import team.ylab.gateway.config.GatewayRoutingConfig;
import team.ylab.gateway.trace.W3CTraceContextSupport;
import team.ylab.observability.autoconfigure.ObservabilityProperties;
import team.ylab.observability.trace.TraceConstants;
import team.ylab.observability.trace.TraceContextHolder;

/**
 * High-precedence reactive {@link WebFilter} that accepts or creates W3C {@code traceparent},
 * starts an OpenTelemetry server span for Jaeger export, propagates {@code traceparent} and {@code
 * X-Trace-Id} downstream, and guarantees {@code X-Trace-Id} in the HTTP response (SDD v1.1 Section
 * 4.1).
 */
@Component
public class TraceContextWebFilter implements WebFilter, Ordered {

  public static final String ATTR_TRACE_ID = "srh.traceId";
  public static final String ATTR_SPAN_ID = "srh.spanId";
  public static final String ATTR_TRACEPARENT = "srh.traceparent";

  private final Tracer otelTracer;
  private final ObservabilityProperties observabilityProperties;

  public TraceContextWebFilter(Tracer otelTracer, ObservabilityProperties observabilityProperties) {
    this.otelTracer = otelTracer;
    this.observabilityProperties = observabilityProperties;
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 5;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    String traceIdHeaderName = observabilityProperties.getTracing().getTraceIdHeader();

    String incomingTraceparent = request.getHeaders().getFirst(TraceConstants.TRACEPARENT_HEADER);
    String incomingTraceId = request.getHeaders().getFirst(traceIdHeaderName);
    if (incomingTraceId == null || incomingTraceId.isBlank()) {
      incomingTraceId = request.getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER);
    }

    String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
    String originalPath = request.getURI().getRawPath();

    SpanBuilder spanBuilder =
        otelTracer
            .spanBuilder("HTTP " + method + " " + originalPath)
            .setSpanKind(SpanKind.SERVER)
            .setAttribute("http.request.method", method)
            .setAttribute("url.path", originalPath);

    W3CTraceContextSupport.extractParentContext(incomingTraceparent, incomingTraceId)
        .ifPresent(spanBuilder::setParent);

    Span span = spanBuilder.startSpan();
    SpanContext spanContext = span.getSpanContext();

    String traceId =
        spanContext.isValid()
            ? spanContext.getTraceId()
            : (W3CTraceContextSupport.isValidTraceId(incomingTraceId)
                ? incomingTraceId.trim().toLowerCase()
                : W3CTraceContextSupport.generateTraceIdHex());
    String spanId =
        spanContext.isValid()
            ? spanContext.getSpanId()
            : W3CTraceContextSupport.generateSpanIdHex();
    String outboundTraceparent =
        spanContext.isValid()
            ? W3CTraceContextSupport.formatTraceparent(spanContext)
            : W3CTraceContextSupport.formatTraceparent(traceId, spanId);

    exchange.getAttributes().put(ATTR_TRACE_ID, traceId);
    exchange.getAttributes().put(ATTR_SPAN_ID, spanId);
    exchange.getAttributes().put(ATTR_TRACEPARENT, outboundTraceparent);

    ServerHttpResponse response = exchange.getResponse();
    if (observabilityProperties.getLogging().isIncludeResponseHeader()) {
      response.getHeaders().set(traceIdHeaderName, traceId);
      if (!TraceConstants.TRACE_ID_HEADER.equalsIgnoreCase(traceIdHeaderName)) {
        response.getHeaders().set(TraceConstants.TRACE_ID_HEADER, traceId);
      }
      response.beforeCommit(
          () -> {
            response.getHeaders().set(traceIdHeaderName, traceId);
            if (!TraceConstants.TRACE_ID_HEADER.equalsIgnoreCase(traceIdHeaderName)) {
              response.getHeaders().set(TraceConstants.TRACE_ID_HEADER, traceId);
            }
            return Mono.empty();
          });
    }

    ServerHttpRequest mutatedRequest =
        request
            .mutate()
            .headers(
                headers -> {
                  headers.set(traceIdHeaderName, traceId);
                  headers.set(TraceConstants.TRACE_ID_HEADER, traceId);
                  headers.set(TraceConstants.TRACEPARENT_HEADER, outboundTraceparent);
                  if (headers.getFirst(GatewayRoutingConfig.ORIG_REQUEST_PATH_HEADER) == null) {
                    headers.set(GatewayRoutingConfig.ORIG_REQUEST_PATH_HEADER, originalPath);
                  }
                })
            .build();

    ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

    return Mono.defer(
            () -> {
              if (observabilityProperties.getLogging().isMdcEnabled()) {
                TraceContextHolder.setTraceId(traceId);
                TraceContextHolder.setSpanId(spanId);
              }
              try (Scope ignored = Context.current().with(span).makeCurrent()) {
                return chain.filter(mutatedExchange);
              }
            })
        .doOnError(
            error -> {
              span.recordException(error);
              span.setStatus(StatusCode.ERROR, error.getMessage());
            })
        .doFinally(
            signalType -> {
              HttpStatusCode statusCode = mutatedExchange.getResponse().getStatusCode();
              if (statusCode != null) {
                span.setAttribute("http.response.status_code", (long) statusCode.value());
                if (statusCode.is5xxServerError()) {
                  span.setStatus(StatusCode.ERROR);
                }
              }
              span.end();
              if (observabilityProperties.getLogging().isMdcEnabled()) {
                TraceContextHolder.clear();
              }
            });
  }
}
