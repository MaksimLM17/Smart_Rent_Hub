package team.ylab.businesscalendar.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import team.ylab.businesscalendar.BusinessCalendar;
import team.ylab.businesscalendar.BusinessCalendarConfigProvider;
import team.ylab.businesscalendar.DefaultBusinessCalendar;
import team.ylab.businesscalendar.model.PickupCalendarConfig;

class BusinessCalendarAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(BusinessCalendarAutoConfiguration.class));

  @Test
  void registersDefaultBeansWhenEnabled() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(BusinessCalendar.class);
          assertThat(context).hasSingleBean(BusinessCalendarConfigProvider.class);

          BusinessCalendar calendar = context.getBean(BusinessCalendar.class);
          assertThat(calendar.getConfig()).isEqualTo(PickupCalendarConfig.defaultConfig());
        });
  }

  @Test
  void bindsCustomProperties() {
    contextRunner
        .withPropertyValues(
            "srh.business-calendar.time-zone=Europe/Moscow",
            "srh.business-calendar.work-start=10:00",
            "srh.business-calendar.work-end=20:00",
            "srh.business-calendar.slot-minutes=30",
            "srh.business-calendar.slot-capacity=5",
            "srh.business-calendar.weekend-days=SUNDAY",
            "srh.business-calendar.holidays=2026-01-01,2026-05-01")
        .run(
            context -> {
              BusinessCalendar calendar = context.getBean(BusinessCalendar.class);
              PickupCalendarConfig config = calendar.getConfig();

              assertThat(config.timeZone()).isEqualTo(ZoneId.of("Europe/Moscow"));
              assertThat(config.workStart()).isEqualTo(LocalTime.of(10, 0));
              assertThat(config.workEnd()).isEqualTo(LocalTime.of(20, 0));
              assertThat(config.slotMinutes()).isEqualTo(30);
              assertThat(config.slotCapacity()).isEqualTo(5);
              assertThat(config.weekendDays()).containsExactly(DayOfWeek.SUNDAY);
              assertThat(config.holidays())
                  .containsExactlyInAnyOrder(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 1));
            });
  }

  @Test
  void backsOffWhenDisabled() {
    contextRunner
        .withPropertyValues("srh.business-calendar.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(BusinessCalendar.class);
              assertThat(context).doesNotHaveBean(BusinessCalendarConfigProvider.class);
            });
  }

  @Test
  void backsOffWhenCustomBusinessCalendarBeanProvided() {
    BusinessCalendar custom = new DefaultBusinessCalendar();
    contextRunner
        .withBean(BusinessCalendar.class, () -> custom)
        .run(
            context -> {
              assertThat(context).hasSingleBean(BusinessCalendar.class);
              assertThat(context.getBean(BusinessCalendar.class)).isSameAs(custom);
            });
  }
}
