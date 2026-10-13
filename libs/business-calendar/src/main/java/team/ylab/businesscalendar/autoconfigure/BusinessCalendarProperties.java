package team.ylab.businesscalendar.autoconfigure;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import team.ylab.businesscalendar.model.PickupCalendarConfig;

/** Configuration properties for the Business Calendar library ({@code srh.business-calendar.*}). */
@Data
@ConfigurationProperties(prefix = "srh.business-calendar")
public class BusinessCalendarProperties {

  /** Whether BusinessCalendar auto-configuration is enabled. */
  private boolean enabled = true;

  /** Pickup point time zone (default {@code Europe/Moscow}). */
  private ZoneId timeZone = PickupCalendarConfig.DEFAULT_TIME_ZONE;

  /** Daily opening time (default {@code 09:00}). */
  private LocalTime workStart = PickupCalendarConfig.DEFAULT_WORK_START;

  /** Daily closing time (default {@code 21:00}). */
  private LocalTime workEnd = PickupCalendarConfig.DEFAULT_WORK_END;

  /** Duration of a single pickup/return slot in minutes (default {@code 60}). */
  private int slotMinutes = PickupCalendarConfig.DEFAULT_SLOT_MINUTES;

  /** Maximum concurrent handovers/returns per slot (default {@code 10}). */
  private int slotCapacity = PickupCalendarConfig.DEFAULT_SLOT_CAPACITY;

  /** Configured non-working days of the week (empty by default: open 7 days a week). */
  private Set<DayOfWeek> weekendDays = new LinkedHashSet<>();

  /** Configured non-working holiday dates. */
  private Set<LocalDate> holidays = new LinkedHashSet<>();

  /** Converts these properties into an immutable {@link PickupCalendarConfig}. */
  public PickupCalendarConfig toPickupCalendarConfig() {
    return new PickupCalendarConfig(
        timeZone, workStart, workEnd, slotMinutes, slotCapacity, weekendDays, holidays);
  }
}
