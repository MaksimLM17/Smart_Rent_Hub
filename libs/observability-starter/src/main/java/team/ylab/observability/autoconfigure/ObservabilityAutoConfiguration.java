package team.ylab.observability.autoconfigure;

import io.micrometer.tracing.Tracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import team.ylab.observability.web.TraceContextFilter;

/**
 * Spring Boot Auto-configuration for Smart Rent Hub Observability.
 *
 * <p>Configures:
 *
 * <ul>
 *   <li>OTLP HTTP Span Exporter for Jaeger / OTel Collector
 *   <li>Servlet filter for trace header propagation and MDC synchronization
 *   <li>ObservationRegistry enhancements
 * </ul>
 */
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "smartrent.observability",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityAutoConfiguration {

  @Bean
  @ConditionalOnClass(OtlpHttpSpanExporter.class)
  @ConditionalOnProperty(
      prefix = "smartrent.observability.tracing.otlp",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  @ConditionalOnMissingBean(SpanExporter.class)
  public SpanExporter otlpHttpSpanExporter(ObservabilityProperties properties) {
    return OtlpHttpSpanExporter.builder()
        .setEndpoint(properties.getTracing().getOtlp().getEndpoint())
        .setTimeout(properties.getTracing().getOtlp().getTimeout())
        .build();
  }

  @Bean
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  @ConditionalOnMissingBean
  public FilterRegistrationBean<TraceContextFilter> traceContextFilterRegistration(
      ObservabilityProperties properties, ObjectProvider<Tracer> tracerProvider) {
    TraceContextFilter filter = new TraceContextFilter(properties, tracerProvider.getIfAvailable());
    FilterRegistrationBean<TraceContextFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
    registration.addUrlPatterns("/*");
    return registration;
  }
}
