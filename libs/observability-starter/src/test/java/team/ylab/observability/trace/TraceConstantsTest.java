package team.ylab.observability.trace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TraceConstantsTest {

  @Test
  void shouldDefineCorrectConstants() {
    assertThat(TraceConstants.TRACE_ID_HEADER).isEqualTo("X-Trace-Id");
    assertThat(TraceConstants.TRACEPARENT_HEADER).isEqualTo("traceparent");
    assertThat(TraceConstants.MDC_TRACE_ID).isEqualTo("traceId");
    assertThat(TraceConstants.MDC_SPAN_ID).isEqualTo("spanId");
    assertThat(TraceConstants.MDC_ORDER_ID).isEqualTo("orderId");
    assertThat(TraceConstants.MDC_PROCESS_INSTANCE_ID).isEqualTo("processInstanceId");
    assertThat(TraceConstants.MDC_USER_ID).isEqualTo("userId");
  }
}
