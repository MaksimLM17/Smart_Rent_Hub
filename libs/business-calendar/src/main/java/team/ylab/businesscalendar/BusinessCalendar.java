package team.ylab.businesscalendar;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import team.ylab.businesscalendar.model.PickupCalendarConfig;
import team.ylab.businesscalendar.model.TimeSlot;

/**
 * Business calendar port for pickup point working hours, handover/return slots, holidays, and
 * working-time SLA calculations (SDD v1.1 Section 4.3, PRD v3.2 Sections 5.2, 5.6, 5.9, 6.5).
 */
public interface BusinessCalendar {

  /** Returns the active pickup point calendar configuration. */
  PickupCalendarConfig getConfig();

  /**
   * Checks whether the given calendar date is a working day (neither a configured weekend day nor a
   * holiday).
   *
   * @param date date in the pickup point's local calendar
   * @return {@code true} if the pickup point operates on {@code date}
   */
  boolean isWorkingDay(LocalDate date);

  /**
   * Checks whether the given instant falls within working hours {@code [workStart, workEnd)} on a
   * working day in the configured time zone.
   *
   * @param timestamp UTC instant to check
   * @return {@code true} if {@code timestamp} is within working hours on a working day
   */
  boolean isWorkingTime(Instant timestamp);

  /**
   * Checks whether the given instant is a valid pickup/return slot start boundary on a working day.
   *
   * @param slotStart UTC instant representing the start of a slot
   * @return {@code true} if {@code slotStart} aligns with a configured slot on a working day
   */
  boolean isValidSlot(Instant slotStart);

  /**
   * Returns all pickup/return slots for the given date according to {@code workStart}, {@code
   * workEnd}, and {@code slotMinutes}. Returns an empty list if {@code date} is not a working day.
   *
   * @param date date in the pickup point's local calendar
   * @return ordered list of available time slots for {@code date}
   */
  List<TimeSlot> getAvailableSlots(LocalDate date);

  /**
   * Adds the specified working duration to {@code from}, counting only time inside working windows
   * {@code [workStart, workEnd)} on working days (e.g., 4 working hours for inspection SLA per PRD
   * 5.6, or 2 working hours for high-risk moderation SLA per PRD 6.5).
   *
   * @param from starting instant (if outside working hours, calculation starts from the next
   *     working window opening)
   * @param workingDuration non-negative working duration to add
   * @return deadline instant in UTC
   */
  Instant addWorkingDuration(Instant from, Duration workingDuration);

  /**
   * Adds the specified number of working days to {@code from}, skipping weekend days and holidays
   * (e.g., 5 working days for dispute resolution SLA per PRD 5.9).
   *
   * @param from starting local date
   * @param workingDays non-negative number of working days to add
   * @return resulting working date
   */
  LocalDate addWorkingDays(LocalDate from, int workingDays);

  /**
   * Calculates the elapsed working duration between {@code start} and {@code end}, counting only
   * intervals within {@code [workStart, workEnd)} on working days.
   *
   * @param start start instant (inclusive)
   * @param end end instant (exclusive, must not be before {@code start})
   * @return total working duration elapsed between {@code start} and {@code end}
   */
  Duration workingDurationBetween(Instant start, Instant end);
}
