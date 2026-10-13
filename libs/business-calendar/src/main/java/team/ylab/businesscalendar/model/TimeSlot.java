package team.ylab.businesscalendar.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents a single pickup or return time slot in UTC instants.
 *
 * @param start inclusive slot start timestamp
 * @param end exclusive slot end timestamp
 */
public record TimeSlot(Instant start, Instant end) {

  public TimeSlot {
    Objects.requireNonNull(start, "start must not be null");
    Objects.requireNonNull(end, "end must not be null");
    if (!start.isBefore(end)) {
      throw new IllegalArgumentException(
          "start (" + start + ") must be strictly before end (" + end + ")");
    }
  }

  /** Returns the duration of this slot. */
  public Duration duration() {
    return Duration.between(start, end);
  }

  /** Returns {@code true} if the given instant lies within {@code [start, end)}. */
  public boolean contains(Instant instant) {
    Objects.requireNonNull(instant, "instant must not be null");
    return !instant.isBefore(start) && instant.isBefore(end);
  }
}
