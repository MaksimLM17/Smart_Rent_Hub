package team.ylab.observability.web;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;
import team.ylab.observability.autoconfigure.ObservabilityProperties;
import team.ylab.observability.trace.TraceConstants;
import team.ylab.observability.trace.TraceContextHolder;

/**
 * Servlet filter that captures or initializes the business trace ID, synchronizes it with MDC, and
 * guarantees the response contains the trace header.
 */
@Slf4j
public class TraceContextFilter extends OncePerRequestFilter {

  private final ObservabilityProperties properties;
  private final Tracer tracer;

  public TraceContextFilter(ObservabilityProperties properties, Tracer tracer) {
    this.properties = properties;
    this.tracer = tracer;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String headerName = properties.getTracing().getTraceIdHeader();
    String traceId = request.getHeader(headerName);

    if (traceId == null || traceId.isBlank()) {
      traceId = request.getHeader(TraceConstants.TRACE_ID_HEADER);
    }

    // If still null, try extracting from active Micrometer tracer
    if ((traceId == null || traceId.isBlank()) && tracer != null && tracer.currentSpan() != null) {
      traceId = tracer.currentSpan().context().traceId();
    }

    // If still not present, generate a new trace ID (32 hex characters)
    if (traceId == null || traceId.isBlank()) {
      traceId = UUID.randomUUID().toString().replace("-", "");
    }

    // Extract spanId if available
    String spanId = null;
    if (tracer != null && tracer.currentSpan() != null) {
      spanId = tracer.currentSpan().context().spanId();
    }
    if (spanId == null || spanId.isBlank()) {
      spanId = traceId.substring(0, Math.min(16, traceId.length()));
    }

    if (properties.getLogging().isMdcEnabled()) {
      TraceContextHolder.setTraceId(traceId);
      TraceContextHolder.setSpanId(spanId);
    }

    if (properties.getLogging().isIncludeResponseHeader()) {
      response.setHeader(headerName, traceId);
    }

    try {
      filterChain.doFilter(request, response);
    } finally {
      if (properties.getLogging().isMdcEnabled()) {
        TraceContextHolder.clear();
      }
    }
  }
}
