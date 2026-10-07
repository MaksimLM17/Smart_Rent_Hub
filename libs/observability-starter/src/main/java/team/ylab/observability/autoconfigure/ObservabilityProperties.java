package team.ylab.observability.autoconfigure;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties for Smart Rent Hub observability. */
@Getter
@Setter
@ConfigurationProperties(prefix = "smartrent.observability")
public class ObservabilityProperties {

  /** Whether the observability starter auto-configuration is enabled. */
  private boolean enabled = true;

  /** Service name tag to report to OTel and metrics. */
  private String serviceName;

  /** Tracing specific settings. */
  private TracingProperties tracing = new TracingProperties();

  /** Logging specific settings. */
  private LoggingProperties logging = new LoggingProperties();

  @Getter
  @Setter
  public static class TracingProperties {
    /** Whether tracing is enabled. */
    private boolean enabled = true;

    /** HTTP header name for propagating trace ID to downstream callers. */
    private String traceIdHeader = "X-Trace-Id";

    /** Sampling probability (0.0 to 1.0). Default is 1.0 (100% in dev/stage). */
    private double probability = 1.0;

    /** OTLP exporter configuration. */
    private OtlpProperties otlp = new OtlpProperties();
  }

  @Getter
  @Setter
  public static class OtlpProperties {
    /** Whether OTLP span exporting is enabled. */
    private boolean enabled = true;

    /** OTLP collector endpoint URL (HTTP or gRPC). */
    private String endpoint = "http://localhost:4318/v1/traces";

    /** Protocol to use: http/protobuf or grpc. */
    private String protocol = "http/protobuf";

    /** Timeout for exporting spans. */
    private Duration timeout = Duration.ofSeconds(5);
  }

  @Getter
  @Setter
  public static class LoggingProperties {
    /** Whether automatic MDC synchronization of traceId and spanId is enabled. */
    private boolean mdcEnabled = true;

    /** Whether to include trace ID in HTTP response headers. */
    private boolean includeResponseHeader = true;
  }
}
