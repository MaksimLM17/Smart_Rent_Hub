package team.ylab.businesscalendar.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import team.ylab.businesscalendar.BusinessCalendar;
import team.ylab.businesscalendar.BusinessCalendarConfigProvider;
import team.ylab.businesscalendar.DefaultBusinessCalendar;
import team.ylab.businesscalendar.InMemoryBusinessCalendarConfigProvider;

/** Spring Boot AutoConfiguration for the {@link BusinessCalendar} library. */
@AutoConfiguration
@EnableConfigurationProperties(BusinessCalendarProperties.class)
@ConditionalOnProperty(
    prefix = "srh.business-calendar",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class BusinessCalendarAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public BusinessCalendarConfigProvider businessCalendarConfigProvider(
      BusinessCalendarProperties properties) {
    return new InMemoryBusinessCalendarConfigProvider(properties.toPickupCalendarConfig());
  }

  @Bean
  @ConditionalOnMissingBean
  public BusinessCalendar businessCalendar(BusinessCalendarConfigProvider configProvider) {
    return new DefaultBusinessCalendar(configProvider);
  }
}
