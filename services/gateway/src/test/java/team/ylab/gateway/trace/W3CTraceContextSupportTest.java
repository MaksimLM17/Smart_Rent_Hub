package team.ylab.gateway.trace;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class W3CTraceContextSupportTest {

  @Test
  void extractsValidW3CTraceparent() {
    String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    Optional<Context> contextOpt = W3CTraceContextSupport.extractParentContext(traceparent, null);

    assertThat(contextOpt).isPresent();
    SpanContext spanContext = Span.fromContext(contextOpt.get()).getSpanContext();
    assertThat(spanContext.isValid()).isTrue();
    assertThat(spanContext.getTraceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    assertThat(spanContext.getSpanId()).isEqualTo("00f067aa0ba902b7");
    assertThat(spanContext.isSampled()).isTrue();
  }

  @Test
  void fallsBackToValidXTraceIdWhenTraceparentAbsent() {
    String traceId = "abcdef0123456789abcdef0123456789";

    Optional<Context> contextOpt = W3CTraceContextSupport.extractParentContext(null, traceId);

    assertThat(contextOpt).isPresent();
    SpanContext spanContext = Span.fromContext(contextOpt.get()).getSpanContext();
    assertThat(spanContext.getTraceId()).isEqualTo(traceId);
    assertThat(spanContext.getSpanId()).hasSize(16);
  }

  @Test
  void rejectsInvalidOrAllZeroTraceparent() {
    assertThat(
            W3CTraceContextSupport.extractParentContext(
                "00-00000000000000000000000000000000-00f067aa0ba902b7-01", null))
        .isEmpty();
    assertThat(W3CTraceContextSupport.extractParentContext("malformed", "not-a-hex-id")).isEmpty();
  }

  @Test
  void generatesAndFormatsValidTraceContext() {
    String traceId = W3CTraceContextSupport.generateTraceIdHex();
    String spanId = W3CTraceContextSupport.generateSpanIdHex();

    assertThat(W3CTraceContextSupport.isValidTraceId(traceId)).isTrue();
    assertThat(spanId).matches("^[0-9a-f]{16}$");
    assertThat(W3CTraceContextSupport.formatTraceparent(traceId, spanId))
        .isEqualTo("00-" + traceId + "-" + spanId + "-01");
  }
}
