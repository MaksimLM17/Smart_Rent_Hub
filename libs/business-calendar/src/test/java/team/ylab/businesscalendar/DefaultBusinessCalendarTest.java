package team.ylab.businesscalendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import team.ylab.businesscalendar.model.PickupCalendarConfig;
import team.ylab.businesscalendar.model.TimeSlot;

class DefaultBusinessCalendarTest {

  private static final ZoneId MSK = ZoneId.of("Europe/Moscow");

  @Test
  void defaultConfigMatchesPrdDefaults() {
    DefaultBusinessCalendar calendar = new DefaultBusinessCalendar();
    PickupCalendarConfig config = calendar.getConfig();

    assertThat(config.timeZone()).isEqualTo(MSK);
    assertThat(config.workStart()).isEqualTo(LocalTime.of(9, 0));
    assertThat(config.workEnd()).isEqualTo(LocalTime.of(21, 0));
    assertThat(config.slotMinutes()).isEqualTo(60);
    assertThat(config.slotCapacity()).isEqualTo(10);
    assertThat(config.weekendDays()).isEmpty();
    assertThat(config.holidays()).isEmpty();
    assertThat(config.dailyWorkingDuration()).isEqualTo(Duration.ofHours(12));
  }

  @Test
  void isWorkingDayRespectsWeekendsAndHolidays() {
    LocalDate holiday = LocalDate.of(2026, 5, 1); // Friday
    LocalDate saturday = LocalDate.of(2026, 5, 2);
    LocalDate monday = LocalDate.of(2026, 5, 4);

    PickupCalendarConfig config =
        new PickupCalendarConfig(
            MSK,
            LocalTime.of(9, 0),
            LocalTime.of(21, 0),
            60,
            10,
            Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            Set.of(holiday));
    BusinessCalendar calendar = new DefaultBusinessCalendar(config);

    assertThat(calendar.isWorkingDay(holiday)).isFalse();
    assertThat(calendar.isWorkingDay(saturday)).isFalse();
    assertThat(calendar.isWorkingDay(monday)).isTrue();
  }

  @Test
  void isWorkingTimeChecksBoundsAndWorkingDay() {
    LocalDate holiday = LocalDate.of(2026, 5, 1);
    LocalDate workDay = LocalDate.of(2026, 5, 4);

    PickupCalendarConfig config =
        new PickupCalendarConfig(
            MSK, LocalTime.of(9, 0), LocalTime.of(21, 0), 60, 10, Set.of(), Set.of(holiday));
    BusinessCalendar calendar = new DefaultBusinessCalendar(config);

    Instant atOpen = ZonedDateTime.of(workDay, LocalTime.of(9, 0), MSK).toInstant();
    Instant midDay = ZonedDateTime.of(workDay, LocalTime.of(15, 30), MSK).toInstant();
    Instant atClose = ZonedDateTime.of(workDay, LocalTime.of(21, 0), MSK).toInstant();
    Instant beforeOpen = ZonedDateTime.of(workDay, LocalTime.of(8, 59), MSK).toInstant();
    Instant onHoliday = ZonedDateTime.of(holiday, LocalTime.of(12, 0), MSK).toInstant();

    assertThat(calendar.isWorkingTime(atOpen)).isTrue();
    assertThat(calendar.isWorkingTime(midDay)).isTrue();
    assertThat(calendar.isWorkingTime(atClose)).isFalse();
    assertThat(calendar.isWorkingTime(beforeOpen)).isFalse();
    assertThat(calendar.isWorkingTime(onHoliday)).isFalse();
  }

  @Test
  void getAvailableSlotsAndIsValidSlotGenerateHourlySlotsOnWorkingDays() {
    LocalDate workDay = LocalDate.of(2026, 5, 4);
    LocalDate holiday = LocalDate.of(2026, 5, 9);

    PickupCalendarConfig config =
        new PickupCalendarConfig(
            MSK, LocalTime.of(9, 0), LocalTime.of(21, 0), 60, 10, Set.of(), Set.of(holiday));
    BusinessCalendar calendar = new DefaultBusinessCalendar(config);

    List<TimeSlot> slots = calendar.getAvailableSlots(workDay);
    assertThat(slots).hasSize(12);
    assertThat(slots.getFirst().start())
        .isEqualTo(ZonedDateTime.of(workDay, LocalTime.of(9, 0), MSK).toInstant());
    assertThat(slots.getLast().end())
        .isEqualTo(ZonedDateTime.of(workDay, LocalTime.of(21, 0), MSK).toInstant());

    Instant validSlotStart = ZonedDateTime.of(workDay, LocalTime.of(14, 0), MSK).toInstant();
    Instant misalignedSlotStart = ZonedDateTime.of(workDay, LocalTime.of(14, 30), MSK).toInstant();
    Instant closingInstant = ZonedDateTime.of(workDay, LocalTime.of(21, 0), MSK).toInstant();

    assertThat(calendar.isValidSlot(validSlotStart)).isTrue();
    assertThat(calendar.isValidSlot(misalignedSlotStart)).isFalse();
    assertThat(calendar.isValidSlot(closingInstant)).isFalse();
    assertThat(calendar.getAvailableSlots(holiday)).isEmpty();
  }

  @Test
  void addWorkingDurationCalculatesInspectionSlaAcrossOvernightAndHolidays() {
    // PRD 5.6 / S7: 4 working hours inspection SLA
    LocalDate thursday = LocalDate.of(2026, 4, 30);
    LocalDate fridayHoliday = LocalDate.of(2026, 5, 1);
    LocalDate saturday = LocalDate.of(2026, 5, 2);

    PickupCalendarConfig config =
        new PickupCalendarConfig(
            MSK, LocalTime.of(9, 0), LocalTime.of(21, 0), 60, 10, Set.of(), Set.of(fridayHoliday));
    BusinessCalendar calendar = new DefaultBusinessCalendar(config);

    // Returned Thursday at 19:00 MSK -> 2h left on Thursday (19:00-21:00), Friday is holiday,
    // remaining 2h on Saturday (09:00-11:00 MSK)
    Instant returnedAt = ZonedDateTime.of(thursday, LocalTime.of(19, 0), MSK).toInstant();
    Instant deadline = calendar.addWorkingDuration(returnedAt, Duration.ofHours(4));

    Instant expectedDeadline = ZonedDateTime.of(saturday, LocalTime.of(11, 0), MSK).toInstant();
    assertThat(deadline).isEqualTo(expectedDeadline);
  }

  @Test
  void addWorkingDurationAlignsStartWhenSubmittedOutsideWorkingHours() {
    // PRD 6.5: 2 working hours high-risk review SLA submitted at 22:15 MSK
    LocalDate monday = LocalDate.of(2026, 5, 4);
    LocalDate tuesday = LocalDate.of(2026, 5, 5);

    BusinessCalendar calendar = new DefaultBusinessCalendar();
    Instant submittedAfterHours = ZonedDateTime.of(monday, LocalTime.of(22, 15), MSK).toInstant();

    Instant deadline = calendar.addWorkingDuration(submittedAfterHours, Duration.ofHours(2));
    Instant expected = ZonedDateTime.of(tuesday, LocalTime.of(11, 0), MSK).toInstant();
    assertThat(deadline).isEqualTo(expected);
  }

  @Test
  void addWorkingDaysSkipsWeekendsAndHolidays() {
    // PRD 5.9: 5 working days claim resolution SLA
    LocalDate thursday = LocalDate.of(2026, 4, 30);
    LocalDate fridayHoliday = LocalDate.of(2026, 5, 1);

    PickupCalendarConfig config =
        new PickupCalendarConfig(
            MSK,
            LocalTime.of(9, 0),
            LocalTime.of(21, 0),
            60,
            10,
            Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            Set.of(fridayHoliday));
    BusinessCalendar calendar = new DefaultBusinessCalendar(config);

    // Start Apr 30 (Thu). Fri May 1 = holiday, Sat May 2 & Sun May 3 = weekend.
    // Working days: Mon May 4 (1), Tue May 5 (2), Wed May 6 (3), Thu May 7 (4), Fri May 8 (5).
    LocalDate due = calendar.addWorkingDays(thursday, 5);
    assertThat(due).isEqualTo(LocalDate.of(2026, 5, 8));
  }

  @Test
  void workingDurationBetweenCountsOnlyWorkingWindows() {
    LocalDate thursday = LocalDate.of(2026, 4, 30);
    LocalDate fridayHoliday = LocalDate.of(2026, 5, 1);
    LocalDate saturday = LocalDate.of(2026, 5, 2);

    PickupCalendarConfig config =
        new PickupCalendarConfig(
            MSK, LocalTime.of(9, 0), LocalTime.of(21, 0), 60, 10, Set.of(), Set.of(fridayHoliday));
    BusinessCalendar calendar = new DefaultBusinessCalendar(config);

    Instant start = ZonedDateTime.of(thursday, LocalTime.of(18, 30), MSK).toInstant();
    Instant end = ZonedDateTime.of(saturday, LocalTime.of(11, 0), MSK).toInstant();

    // Thu 18:30-21:00 = 2h30m; Fri = 0h (holiday); Sat 09:00-11:00 = 2h -> total 4h30m
    assertThat(calendar.workingDurationBetween(start, end))
        .isEqualTo(Duration.ofHours(4).plusMinutes(30));
  }

  @Test
  void configProviderUpdateDynamicallyChangesCalendarBehavior() {
    InMemoryBusinessCalendarConfigProvider provider = new InMemoryBusinessCalendarConfigProvider();
    BusinessCalendar calendar = new DefaultBusinessCalendar(provider);
    LocalDate targetDate = LocalDate.of(2026, 6, 12);

    assertThat(calendar.isWorkingDay(targetDate)).isTrue();

    provider.updateConfig(
        new PickupCalendarConfig(
            MSK, LocalTime.of(10, 0), LocalTime.of(20, 0), 30, 5, Set.of(), Set.of(targetDate)));

    assertThat(calendar.isWorkingDay(targetDate)).isFalse();
    assertThat(calendar.getAvailableSlots(targetDate.plusDays(1))).hasSize(20);
  }

  @Test
  void validatesConfigAndSlotInvariants() {
    assertThatThrownBy(
            () ->
                new PickupCalendarConfig(
                    MSK, LocalTime.of(21, 0), LocalTime.of(9, 0), 60, 10, Set.of(), Set.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("workStart");

    assertThatThrownBy(
            () ->
                new PickupCalendarConfig(
                    MSK, LocalTime.of(9, 0), LocalTime.of(21, 0), 0, 10, Set.of(), Set.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("slotMinutes");

    Instant now = Instant.parse("2026-05-04T10:00:00Z");
    assertThatThrownBy(() -> new TimeSlot(now, now)).isInstanceOf(IllegalArgumentException.class);
  }
}
