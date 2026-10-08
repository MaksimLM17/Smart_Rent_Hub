package team.ylab.outbox.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import team.ylab.outbox.inbox.InboxService;
import team.ylab.outbox.inbox.JdbcInboxService;
import team.ylab.outbox.publisher.JdbcOutboxPublisher;
import team.ylab.outbox.publisher.OutboxPublisher;

/** Spring Boot AutoConfiguration for Transactional Outbox and Inbox starters. */
@AutoConfiguration(
    afterName = "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration")
@ConditionalOnClass(JdbcTemplate.class)
@EnableConfigurationProperties(OutboxProperties.class)
@ConditionalOnProperty(
    prefix = "srh.outbox",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OutboxAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "outboxObjectMapper")
  public ObjectMapper outboxObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return mapper;
  }

  @Bean
  @ConditionalOnMissingBean
  public OutboxPublisher outboxPublisher(
      JdbcTemplate jdbcTemplate, ObjectMapper outboxObjectMapper, OutboxProperties properties) {
    return new JdbcOutboxPublisher(jdbcTemplate, outboxObjectMapper, properties);
  }

  @Bean
  @ConditionalOnMissingBean
  public InboxService inboxService(JdbcTemplate jdbcTemplate, OutboxProperties properties) {
    return new JdbcInboxService(jdbcTemplate, properties);
  }
}
