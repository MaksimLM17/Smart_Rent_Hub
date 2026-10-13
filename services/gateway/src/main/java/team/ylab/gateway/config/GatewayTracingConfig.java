package team.ylab.gateway.config;

import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.util.Collections;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import team.ylab.observability.autoconfigure.ObservabilityProperties;

/**
 * Configures OpenTelemetry SDK and Micrometer Tracing bridge for Gateway so that incoming requests
 * generate W3C traces and export spans to Jaeger via OTel Collector.
 */
@Configuration
public class GatewayTracingConfig {

  @Bean(destroyMethod = "close")
  @ConditionalOnMissingBean
  public SdkTracerProvider sdkTracerProvider(
      ObjectProvider<SpanExporter> spanExporterProvider,
      ObservabilityProperties observabilityProperties,
      @Value("${spring.application.name:gateway}") String applicationName) {
    String serviceName =
        observabilityProperties.getServiceName() != null
                && !observabilityProperties.getServiceName().isBlank()
            ? observabilityProperties.getServiceName()
            : applicationName;

    Resource resource =
        Resource.getDefault()
            .merge(
                Resource.create(
                    Attributes.of(AttributeKey.stringKey("service.name"), serviceName)));

    SdkTracerProviderBuilder builder =
        SdkTracerProvider.builder()
            .setResource(resource)
            .setSampler(
                Sampler.traceIdRatioBased(observabilityProperties.getTracing().getProbability()));

    SpanExporter exporter = spanExporterProvider.getIfAvailable();
    if (exporter != null) {
      builder.addSpanProcessor(SimpleSpanProcessor.create(exporter));
    }
    return builder.build();
  }

  @Bean
  @ConditionalOnMissingBean
  public OpenTelemetry openTelemetry(SdkTracerProvider sdkTracerProvider) {
    return OpenTelemetrySdk.builder()
        .setTracerProvider(sdkTracerProvider)
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .build();
  }

  @Bean
  @ConditionalOnMissingBean(Tracer.class)
  public Tracer otelTracer(
      OpenTelemetry openTelemetry,
      @Value("${spring.application.name:gateway}") String applicationName) {
    return openTelemetry.getTracer(applicationName);
  }

  @Bean
  @ConditionalOnMissingBean(io.micrometer.tracing.Tracer.class)
  public io.micrometer.tracing.Tracer micrometerTracer(Tracer otelTracer) {
    OtelCurrentTraceContext currentTraceContext = new OtelCurrentTraceContext();
    return new OtelTracer(
        otelTracer,
        currentTraceContext,
        event -> {},
        new OtelBaggageManager(
            currentTraceContext, Collections.emptyList(), Collections.emptyList()));
  }
}
