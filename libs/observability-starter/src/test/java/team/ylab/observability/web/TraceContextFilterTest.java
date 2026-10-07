package team.ylab.observability.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import team.ylab.observability.autoconfigure.ObservabilityProperties;
import team.ylab.observability.trace.TraceConstants;
import team.ylab.observability.trace.TraceContextHolder;

class TraceContextFilterTest {

  private ObservabilityProperties properties;
  private Tracer tracer;
  private TraceContextFilter filter;

  @BeforeEach
  void setUp() {
    TraceContextHolder.clear();
    properties = new ObservabilityProperties();
    tracer = mock(Tracer.class);
    filter = new TraceContextFilter(properties, tracer);
  }

  @AfterEach
  void tearDown() {
    TraceContextHolder.clear();
  }

  @Test
  void shouldPropagateIncomingTraceId() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(TraceConstants.TRACE_ID_HEADER, "incoming-trace-abc");
    MockHttpServletResponse response = new MockHttpServletResponse();

    AtomicReference<String> mdcTraceIdDuringExecution = new AtomicReference<>();

    MockFilterChain filterChain =
        new MockFilterChain() {
          @Override
          public void doFilter(
              jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
            mdcTraceIdDuringExecution.set(TraceContextHolder.getTraceId());
          }
        };

    filter.doFilter(request, response, filterChain);

    assertThat(mdcTraceIdDuringExecution.get()).isEqualTo("incoming-trace-abc");
    assertThat(response.getHeader(TraceConstants.TRACE_ID_HEADER)).isEqualTo("incoming-trace-abc");
    // Ensure MDC is cleaned up after request completes
    assertThat(TraceContextHolder.getTraceId()).isNull();
  }

  @Test
  void shouldGenerateNewTraceIdIfAbsent() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    AtomicReference<String> mdcTraceIdDuringExecution = new AtomicReference<>();

    MockFilterChain filterChain =
        new MockFilterChain() {
          @Override
          public void doFilter(
              jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
            mdcTraceIdDuringExecution.set(TraceContextHolder.getTraceId());
          }
        };

    filter.doFilter(request, response, filterChain);

    String generatedTraceId = mdcTraceIdDuringExecution.get();
    assertThat(generatedTraceId).isNotNull().isNotBlank();
    assertThat(response.getHeader(TraceConstants.TRACE_ID_HEADER)).isEqualTo(generatedTraceId);
    assertThat(TraceContextHolder.getTraceId()).isNull();
  }
}
