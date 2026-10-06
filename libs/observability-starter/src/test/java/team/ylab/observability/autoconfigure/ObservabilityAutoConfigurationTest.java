package team.ylab.observability.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

class ObservabilityAutoConfigurationTest {

  private final WebApplicationContextRunner webContextRunner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfiguration.class));

  private final ApplicationContextRunner nonWebContextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfiguration.class));

  @Test
  void shouldAutoConfigureBeansInWebApplication() {
    webContextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(SpanExporter.class);
          assertThat(context).hasSingleBean(FilterRegistrationBean.class);
          assertThat(context).hasSingleBean(ObservabilityProperties.class);
        });
  }

  @Test
  void shouldNotConfigureFilterInNonWebApplication() {
    nonWebContextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(SpanExporter.class);
          assertThat(context).doesNotHaveBean(FilterRegistrationBean.class);
        });
  }

  @Test
  void shouldBackOffWhenDisabled() {
    webContextRunner
        .withPropertyValues("smartrent.observability.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(SpanExporter.class);
              assertThat(context).doesNotHaveBean(FilterRegistrationBean.class);
            });
  }
}
