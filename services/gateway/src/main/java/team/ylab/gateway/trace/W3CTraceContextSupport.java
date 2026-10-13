package team.ylab.gateway.trace;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper utility for W3C Trace Context ({@code traceparent}) and {@code X-Trace-Id} parsing,
 * validation, and formatting.
 */
public final class W3CTraceContextSupport {

  private static final Pattern TRACEPARENT_PATTERN =
      Pattern.compile("^([0-9a-f]{2})-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$");

  private static final Pattern TRACE_ID_PATTERN = Pattern.compile("^[0-9a-f]{32}$");

  private static final String ALL_ZERO_TRACE_ID = "00000000000000000000000000000000";
  private static final String ALL_ZERO_SPAN_ID = "0000000000000000";

  private W3CTraceContextSupport() {}

  /**
   * Attempts to extract a remote OpenTelemetry parent {@link Context} from a W3C {@code
   * traceparent} header or a fallback {@code X-Trace-Id} header.
   *
   * @param traceparent raw {@code traceparent} header value (may be null)
   * @param traceIdHeader raw {@code X-Trace-Id} header value (may be null)
   * @return optional OpenTelemetry {@link Context} containing the remote parent {@link SpanContext}
   */
  public static Optional<Context> extractParentContext(String traceparent, String traceIdHeader) {
    if (traceparent != null && !traceparent.isBlank()) {
      Matcher matcher = TRACEPARENT_PATTERN.matcher(traceparent.trim().toLowerCase(Locale.ROOT));
      if (matcher.matches()) {
        String version = matcher.group(1);
        String traceId = matcher.group(2);
        String parentSpanId = matcher.group(3);
        String flagsHex = matcher.group(4);

        if (!"ff".equals(version)
            && !ALL_ZERO_TRACE_ID.equals(traceId)
            && !ALL_ZERO_SPAN_ID.equals(parentSpanId)) {
          TraceFlags flags = TraceFlags.fromHex(flagsHex, 0);
          SpanContext remoteContext =
              SpanContext.createFromRemoteParent(
                  traceId, parentSpanId, flags, TraceState.getDefault());
          if (remoteContext.isValid()) {
            return Optional.of(Context.root().with(Span.wrap(remoteContext)));
          }
        }
      }
    }

    if (isValidTraceId(traceIdHeader)) {
      String normalizedTraceId = traceIdHeader.trim().toLowerCase(Locale.ROOT);
      String syntheticParentSpanId = generateSpanIdHex();
      SpanContext remoteContext =
          SpanContext.createFromRemoteParent(
              normalizedTraceId,
              syntheticParentSpanId,
              TraceFlags.getSampled(),
              TraceState.getDefault());
      if (remoteContext.isValid()) {
        return Optional.of(Context.root().with(Span.wrap(remoteContext)));
      }
    }

    return Optional.empty();
  }

  /** Checks whether the given string is a valid 32-character non-zero hexadecimal trace ID. */
  public static boolean isValidTraceId(String candidate) {
    if (candidate == null || candidate.isBlank()) {
      return false;
    }
    String normalized = candidate.trim().toLowerCase(Locale.ROOT);
    return TRACE_ID_PATTERN.matcher(normalized).matches() && !ALL_ZERO_TRACE_ID.equals(normalized);
  }

  /** Formats a {@link SpanContext} as a W3C {@code traceparent} header value. */
  public static String formatTraceparent(SpanContext spanContext) {
    return String.format(
        "00-%s-%s-%s",
        spanContext.getTraceId(), spanContext.getSpanId(), spanContext.getTraceFlags().asHex());
  }

  /** Formats explicit {@code traceId} and {@code spanId} into a sampled W3C {@code traceparent}. */
  public static String formatTraceparent(String traceId, String spanId) {
    return String.format("00-%s-%s-01", traceId, spanId);
  }

  /** Generates a random non-zero 32-character lowercase hex trace ID. */
  public static String generateTraceIdHex() {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    long high = random.nextLong();
    long low = random.nextLong();
    if (high == 0 && low == 0) {
      low = 1L;
    }
    return String.format("%016x%016x", high, low);
  }

  /** Generates a random non-zero 16-character lowercase hex span ID. */
  public static String generateSpanIdHex() {
    long value = ThreadLocalRandom.current().nextLong();
    if (value == 0L) {
      value = 1L;
    }
    return String.format("%016x", value);
  }
}
