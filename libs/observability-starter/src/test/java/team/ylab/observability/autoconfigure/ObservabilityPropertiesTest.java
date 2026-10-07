package team.ylab.observability.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ObservabilityPropertiesTest {

  @Test
  void shouldHaveCorrectDefaults() {
    ObservabilityProperties properties = new ObservabilityProperties();

    assertThat(properties.isEnabled()).isTrue();
    assertThat(properties.getTracing().isEnabled()).isTrue();
    assertThat(properties.getTracing().getTraceIdHeader()).isEqualTo("X-Trace-Id");
    assertThat(properties.getTracing().getProbability()).isEqualTo(1.0);

    assertThat(properties.getTracing().getOtlp().isEnabled()).isTrue();
    assertThat(properties.getTracing().getOtlp().getEndpoint())
        .isEqualTo("http://localhost:4318/v1/traces");
    assertThat(properties.getTracing().getOtlp().getProtocol()).isEqualTo("http/protobuf");
    assertThat(properties.getTracing().getOtlp().getTimeout()).isEqualTo(Duration.ofSeconds(5));

    assertThat(properties.getLogging().isMdcEnabled()).isTrue();
    assertThat(properties.getLogging().isIncludeResponseHeader()).isTrue();
  }
}
