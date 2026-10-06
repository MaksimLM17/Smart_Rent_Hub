package team.ylab.observability.trace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class TraceContextHolderTest {

  @BeforeEach
  @AfterEach
  void cleanMdc() {
    TraceContextHolder.clear();
  }

  @Test
  void shouldSetAndGetTraceId() {
    TraceContextHolder.setTraceId("trace-123");
    assertThat(TraceContextHolder.getTraceId()).isEqualTo("trace-123");
    assertThat(MDC.get(TraceConstants.MDC_TRACE_ID)).isEqualTo("trace-123");

    TraceContextHolder.setTraceId(null);
    assertThat(TraceContextHolder.getTraceId()).isNull();
    assertThat(MDC.get(TraceConstants.MDC_TRACE_ID)).isNull();
  }

  @Test
  void shouldSetAndGetSpanId() {
    TraceContextHolder.setSpanId("span-456");
    assertThat(TraceContextHolder.getSpanId()).isEqualTo("span-456");

    TraceContextHolder.setSpanId(null);
    assertThat(TraceContextHolder.getSpanId()).isNull();
  }

  @Test
  void shouldSetAndGetDomainContext() {
    TraceContextHolder.setOrderId("order-999");
    TraceContextHolder.setProcessInstanceId("proc-888");
    TraceContextHolder.setUserId("user-777");

    assertThat(TraceContextHolder.getOrderId()).isEqualTo("order-999");
    assertThat(TraceContextHolder.getProcessInstanceId()).isEqualTo("proc-888");
    assertThat(TraceContextHolder.getUserId()).isEqualTo("user-777");

    TraceContextHolder.clear();
    assertThat(TraceContextHolder.getOrderId()).isNull();
    assertThat(TraceContextHolder.getProcessInstanceId()).isNull();
    assertThat(TraceContextHolder.getUserId()).isNull();
  }
}
