package team.ylab.observability.trace;

import org.slf4j.MDC;

/** Utility class for managing observability attributes in the logging MDC. */
public final class TraceContextHolder {

  private TraceContextHolder() {}

  public static void setTraceId(String traceId) {
    if (traceId != null && !traceId.isBlank()) {
      MDC.put(TraceConstants.MDC_TRACE_ID, traceId);
    } else {
      MDC.remove(TraceConstants.MDC_TRACE_ID);
    }
  }

  public static String getTraceId() {
    return MDC.get(TraceConstants.MDC_TRACE_ID);
  }

  public static void setSpanId(String spanId) {
    if (spanId != null && !spanId.isBlank()) {
      MDC.put(TraceConstants.MDC_SPAN_ID, spanId);
    } else {
      MDC.remove(TraceConstants.MDC_SPAN_ID);
    }
  }

  public static String getSpanId() {
    return MDC.get(TraceConstants.MDC_SPAN_ID);
  }

  public static void setOrderId(String orderId) {
    if (orderId != null && !orderId.isBlank()) {
      MDC.put(TraceConstants.MDC_ORDER_ID, orderId);
    } else {
      MDC.remove(TraceConstants.MDC_ORDER_ID);
    }
  }

  public static String getOrderId() {
    return MDC.get(TraceConstants.MDC_ORDER_ID);
  }

  public static void setProcessInstanceId(String processInstanceId) {
    if (processInstanceId != null && !processInstanceId.isBlank()) {
      MDC.put(TraceConstants.MDC_PROCESS_INSTANCE_ID, processInstanceId);
    } else {
      MDC.remove(TraceConstants.MDC_PROCESS_INSTANCE_ID);
    }
  }

  public static String getProcessInstanceId() {
    return MDC.get(TraceConstants.MDC_PROCESS_INSTANCE_ID);
  }

  public static void setUserId(String userId) {
    if (userId != null && !userId.isBlank()) {
      MDC.put(TraceConstants.MDC_USER_ID, userId);
    } else {
      MDC.remove(TraceConstants.MDC_USER_ID);
    }
  }

  public static String getUserId() {
    return MDC.get(TraceConstants.MDC_USER_ID);
  }

  /** Clears all Smart Rent Hub observability fields from MDC. */
  public static void clear() {
    MDC.remove(TraceConstants.MDC_TRACE_ID);
    MDC.remove(TraceConstants.MDC_SPAN_ID);
    MDC.remove(TraceConstants.MDC_ORDER_ID);
    MDC.remove(TraceConstants.MDC_PROCESS_INSTANCE_ID);
    MDC.remove(TraceConstants.MDC_USER_ID);
  }
}
