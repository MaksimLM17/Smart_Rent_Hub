package team.ylab.businesscalendar;

import team.ylab.businesscalendar.model.PickupCalendarConfig;

/**
 * Provider of the current {@link PickupCalendarConfig}.
 *
 * <p>In Stage 0 this is backed by configuration properties / in-memory state; in Stage 2+ services
 * can refresh or replace the provider from the {@code pickup_config} projection updated via {@code
 * ConfigChanged} events (SDD v1.1 Section 4.3).
 */
@FunctionalInterface
public interface BusinessCalendarConfigProvider {

  /** Returns the current pickup calendar configuration snapshot. */
  PickupCalendarConfig getConfig();

  /**
   * Updates the active configuration snapshot if supported by this provider.
   *
   * @param newConfig updated pickup calendar configuration
   * @throws UnsupportedOperationException if this provider is read-only
   */
  default void updateConfig(PickupCalendarConfig newConfig) {
    throw new UnsupportedOperationException("Config updates are not supported by this provider");
  }
}
