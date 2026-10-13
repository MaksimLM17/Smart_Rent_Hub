package team.ylab.businesscalendar.model;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;

/**
 * Pickup point calendar configuration projection (SDD v1.1 Section 4.3, Appendix A {@code
 * pickup_config}, PRD v3.2 Section 5.2).
 *
 * @param timeZone pickup point time zone (default {@code Europe/Moscow})
 * @param workStart daily opening time (default {@code 09:00})
 * @param workEnd daily closing time (default {@code 21:00})
 * @param slotMinutes duration of a single pickup/return slot in minutes (default {@code 60})
 * @param slotCapacity maximum concurrent handovers/returns per slot (default {@code 10})
 * @param weekendDays days of the week when the pickup point or back-office is closed
 * @param holidays specific non-working holiday dates in {@code timeZone}
 */
public record PickupCalendarConfig(
    ZoneId timeZone,
    LocalTime workStart,
    LocalTime workEnd,
    int slotMinutes,
    int slotCapacity,
    Set<DayOfWeek> weekendDays,
    Set<LocalDate> holidays) {

  public static final ZoneId DEFAULT_TIME_ZONE = ZoneId.of("Europe/Moscow");
  public static final LocalTime DEFAULT_WORK_START = LocalTime.of(9, 0);
  public static final LocalTime DEFAULT_WORK_END = LocalTime.of(21, 0);
  public static final int DEFAULT_SLOT_MINUTES = 60;
  public static final int DEFAULT_SLOT_CAPACITY = 10;

  public PickupCalendarConfig {
    Objects.requireNonNull(timeZone, "timeZone must not be null");
    Objects.requireNonNull(workStart, "workStart must not be null");
    Objects.requireNonNull(workEnd, "workEnd must not be null");
    if (!workStart.isBefore(workEnd)) {
      throw new IllegalArgumentException(
          "workStart (" + workStart + ") must be strictly before workEnd (" + workEnd + ")");
    }
    if (slotMinutes <= 0) {
      throw new IllegalArgumentException("slotMinutes must be positive, got: " + slotMinutes);
    }
    long dailyWorkMinutes = Duration.between(workStart, workEnd).toMinutes();
    if (slotMinutes > dailyWorkMinutes) {
      throw new IllegalArgumentException(
          "slotMinutes ("
              + slotMinutes
              + ") must not exceed daily working minutes ("
              + dailyWorkMinutes
              + ")");
    }
    if (slotCapacity <= 0) {
      throw new IllegalArgumentException("slotCapacity must be positive, got: " + slotCapacity);
    }
    weekendDays = weekendDays == null ? Set.of() : Set.copyOf(weekendDays);
    if (weekendDays.size() >= 7) {
      throw new IllegalArgumentException("weekendDays cannot contain all 7 days of the week");
    }
    holidays = holidays == null ? Set.of() : Set.copyOf(holidays);
  }

  /**
   * Returns the default PRD v3.2 Section 5.2 configuration: {@code Europe/Moscow}, {@code
   * 09:00–21:00}, 60-minute slots, capacity 10, open 7 days a week (no weekend days by default), no
   * holidays.
   */
  public static PickupCalendarConfig defaultConfig() {
    return new PickupCalendarConfig(
        DEFAULT_TIME_ZONE,
        DEFAULT_WORK_START,
        DEFAULT_WORK_END,
        DEFAULT_SLOT_MINUTES,
        DEFAULT_SLOT_CAPACITY,
        Set.of(),
        Set.of());
  }

  /** Returns the daily working duration between {@code workStart} and {@code workEnd}. */
  public Duration dailyWorkingDuration() {
    return Duration.between(workStart, workEnd);
  }
}
