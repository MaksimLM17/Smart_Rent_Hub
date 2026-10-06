package team.ylab.observability.trace;

/** Common observability constants for trace headers and MDC keys. */
public final class TraceConstants {

  private TraceConstants() {}

  /** HTTP header for propagating the business trace ID. */
  public static final String TRACE_ID_HEADER = "X-Trace-Id";

  /** W3C Trace Context standard HTTP header. */
  public static final String TRACEPARENT_HEADER = "traceparent";

  /** MDC key for the trace identifier. */
  public static final String MDC_TRACE_ID = "traceId";

  /** MDC key for the span identifier. */
  public static final String MDC_SPAN_ID = "spanId";

  /** MDC key for domain order / booking identifier. */
  public static final String MDC_ORDER_ID = "orderId";

  /** MDC key for Camunda / Operaton BPMN process instance identifier. */
  public static final String MDC_PROCESS_INSTANCE_ID = "processInstanceId";

  /** MDC key for authenticated user identifier. */
  public static final String MDC_USER_ID = "userId";
}
