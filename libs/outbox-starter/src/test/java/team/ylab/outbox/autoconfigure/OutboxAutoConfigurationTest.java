package team.ylab.outbox.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import team.ylab.outbox.inbox.InboxService;
import team.ylab.outbox.publisher.OutboxPublisher;

@DisplayName("OutboxAutoConfiguration Tests")
class OutboxAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  DataSourceAutoConfiguration.class,
                  JdbcTemplateAutoConfiguration.class,
                  OutboxAutoConfiguration.class))
          .withPropertyValues(
              "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
              "spring.datasource.driver-class-name=org.h2.Driver");

  @Test
  @DisplayName(
      "Registers OutboxPublisher and InboxService beans by default when DataSource is present")
  void registersBeansByDefault() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(OutboxPublisher.class);
          assertThat(context).hasSingleBean(InboxService.class);
        });
  }

  @Test
  @DisplayName("Does not register beans when srh.outbox.enabled=false")
  void disabledWhenPropertyFalse() {
    contextRunner
        .withPropertyValues("srh.outbox.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(OutboxPublisher.class);
              assertThat(context).doesNotHaveBean(InboxService.class);
            });
  }
}
