package team.ylab.businesscalendar;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import team.ylab.businesscalendar.model.PickupCalendarConfig;
import team.ylab.businesscalendar.model.TimeSlot;

/**
 * Default deterministic implementation of {@link BusinessCalendar} backed by a {@link
 * BusinessCalendarConfigProvider}.
 */
public class DefaultBusinessCalendar implements BusinessCalendar {

  private static final int MAX_SEARCH_DAYS = 3660;

  private final BusinessCalendarConfigProvider configProvider;

  public DefaultBusinessCalendar(BusinessCalendarConfigProvider configProvider) {
    this.configProvider = Objects.requireNonNull(configProvider, "configProvider must not be null");
  }

  public DefaultBusinessCalendar(PickupCalendarConfig config) {
    this(new InMemoryBusinessCalendarConfigProvider(config));
  }

  public DefaultBusinessCalendar() {
    this(PickupCalendarConfig.defaultConfig());
  }

  @Override
  public PickupCalendarConfig getConfig() {
    return configProvider.getConfig();
  }

  @Override
  public boolean isWorkingDay(LocalDate date) {
    Objects.requireNonNull(date, "date must not be null");
    return isWorkingDay(date, getConfig());
  }

  private boolean isWorkingDay(LocalDate date, PickupCalendarConfig config) {
    return !config.weekendDays().contains(date.getDayOfWeek()) && !config.holidays().contains(date);
  }

  @Override
  public boolean isWorkingTime(Instant timestamp) {
    Objects.requireNonNull(timestamp, "timestamp must not be null");
    PickupCalendarConfig config = getConfig();
    ZonedDateTime zdt = timestamp.atZone(config.timeZone());
    if (!isWorkingDay(zdt.toLocalDate(), config)) {
      return false;
    }
    LocalTime time = zdt.toLocalTime();
    return !time.isBefore(config.workStart()) && time.isBefore(config.workEnd());
  }

  @Override
  public boolean isValidSlot(Instant slotStart) {
    Objects.requireNonNull(slotStart, "slotStart must not be null");
    PickupCalendarConfig config = getConfig();
    ZonedDateTime zdt = slotStart.atZone(config.timeZone());
    return getAvailableSlots(zdt.toLocalDate(), config).stream()
        .anyMatch(slot -> slot.start().equals(slotStart));
  }

  @Override
  public List<TimeSlot> getAvailableSlots(LocalDate date) {
    Objects.requireNonNull(date, "date must not be null");
    return getAvailableSlots(date, getConfig());
  }

  private List<TimeSlot> getAvailableSlots(LocalDate date, PickupCalendarConfig config) {
    if (!isWorkingDay(date, config)) {
      return List.of();
    }
    List<TimeSlot> slots = new ArrayList<>();
    Duration slotDuration = Duration.ofMinutes(config.slotMinutes());
    ZonedDateTime dayStart = date.atTime(config.workStart()).atZone(config.timeZone());
    ZonedDateTime dayEnd = date.atTime(config.workEnd()).atZone(config.timeZone());

    ZonedDateTime current = dayStart;
    while (!current.plus(slotDuration).isAfter(dayEnd)) {
      ZonedDateTime next = current.plus(slotDuration);
      slots.add(new TimeSlot(current.toInstant(), next.toInstant()));
      current = next;
    }
    return Collections.unmodifiableList(slots);
  }

  @Override
  public Instant addWorkingDuration(Instant from, Duration workingDuration) {
    Objects.requireNonNull(from, "from must not be null");
    Objects.requireNonNull(workingDuration, "workingDuration must not be null");
    if (workingDuration.isNegative()) {
      throw new IllegalArgumentException(
          "workingDuration must not be negative: " + workingDuration);
    }

    PickupCalendarConfig config = getConfig();
    ZonedDateTime current = alignToWorkingWindowStart(from.atZone(config.timeZone()), config);
    if (workingDuration.isZero()) {
      return current.toInstant();
    }

    Duration remaining = workingDuration;
    int daysSearched = 0;

    while (!remaining.isZero()) {
      if (daysSearched++ > MAX_SEARCH_DAYS) {
        throw new IllegalStateException(
            "Unable to find sufficient working time within " + MAX_SEARCH_DAYS + " days");
      }
      LocalDate date = current.toLocalDate();
      if (!isWorkingDay(date, config)) {
        current = date.plusDays(1).atTime(config.workStart()).atZone(config.timeZone());
        continue;
      }

      ZonedDateTime windowEnd = date.atTime(config.workEnd()).atZone(config.timeZone());
      Duration availableToday = Duration.between(current, windowEnd);
      if (remaining.compareTo(availableToday) <= 0) {
        return current.plus(remaining).toInstant();
      }

      remaining = remaining.minus(availableToday);
      current = date.plusDays(1).atTime(config.workStart()).atZone(config.timeZone());
    }

    return current.toInstant();
  }

  @Override
  public LocalDate addWorkingDays(LocalDate from, int workingDays) {
    Objects.requireNonNull(from, "from must not be null");
    if (workingDays < 0) {
      throw new IllegalArgumentException("workingDays must not be negative: " + workingDays);
    }

    PickupCalendarConfig config = getConfig();
    LocalDate current = from;
    int daysSearched = 0;

    // If starting on a non-working day and workingDays == 0, advance to the next working day
    while (!isWorkingDay(current, config)) {
      if (daysSearched++ > MAX_SEARCH_DAYS) {
        throw new IllegalStateException(
            "Unable to find working day within " + MAX_SEARCH_DAYS + " days");
      }
      current = current.plusDays(1);
    }

    int remaining = workingDays;
    while (remaining > 0) {
      if (daysSearched++ > MAX_SEARCH_DAYS) {
        throw new IllegalStateException(
            "Unable to find sufficient working days within " + MAX_SEARCH_DAYS + " days");
      }
      current = current.plusDays(1);
      if (isWorkingDay(current, config)) {
        remaining--;
      }
    }
    return current;
  }

  @Override
  public Duration workingDurationBetween(Instant start, Instant end) {
    Objects.requireNonNull(start, "start must not be null");
    Objects.requireNonNull(end, "end must not be null");
    if (end.isBefore(start)) {
      throw new IllegalArgumentException(
          "end (" + end + ") must not be before start (" + start + ")");
    }
    if (start.equals(end)) {
      return Duration.ZERO;
    }

    PickupCalendarConfig config = getConfig();
    ZonedDateTime zStart = start.atZone(config.timeZone());
    ZonedDateTime zEnd = end.atZone(config.timeZone());

    LocalDate currentDate = zStart.toLocalDate();
    LocalDate endDate = zEnd.toLocalDate();
    Duration total = Duration.ZERO;

    while (!currentDate.isAfter(endDate)) {
      if (isWorkingDay(currentDate, config)) {
        ZonedDateTime windowStart =
            currentDate.atTime(config.workStart()).atZone(config.timeZone());
        ZonedDateTime windowEnd = currentDate.atTime(config.workEnd()).atZone(config.timeZone());

        ZonedDateTime effectiveStart = zStart.isAfter(windowStart) ? zStart : windowStart;
        ZonedDateTime effectiveEnd = zEnd.isBefore(windowEnd) ? zEnd : windowEnd;

        if (effectiveStart.isBefore(effectiveEnd)) {
          total = total.plus(Duration.between(effectiveStart, effectiveEnd));
        }
      }
      currentDate = currentDate.plusDays(1);
    }

    return total;
  }

  private ZonedDateTime alignToWorkingWindowStart(ZonedDateTime zdt, PickupCalendarConfig config) {
    ZonedDateTime current = zdt;
    int daysSearched = 0;

    while (true) {
      if (daysSearched++ > MAX_SEARCH_DAYS) {
        throw new IllegalStateException(
            "Unable to find working window within " + MAX_SEARCH_DAYS + " days");
      }
      LocalDate date = current.toLocalDate();
      if (!isWorkingDay(date, config)) {
        current = date.plusDays(1).atTime(config.workStart()).atZone(config.timeZone());
        continue;
      }

      LocalTime time = current.toLocalTime();
      if (time.isBefore(config.workStart())) {
        return date.atTime(config.workStart()).atZone(config.timeZone());
      }
      if (!time.isBefore(config.workEnd())) {
        current = date.plusDays(1).atTime(config.workStart()).atZone(config.timeZone());
        continue;
      }
      return current;
    }
  }
}
