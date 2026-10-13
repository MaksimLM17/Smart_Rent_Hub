package team.ylab.businesscalendar;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import team.ylab.businesscalendar.model.PickupCalendarConfig;

/**
 * Thread-safe in-memory {@link BusinessCalendarConfigProvider} that allows atomic configuration
 * updates upon receiving {@code ConfigChanged} events.
 */
public class InMemoryBusinessCalendarConfigProvider implements BusinessCalendarConfigProvider {

  private final AtomicReference<PickupCalendarConfig> configRef;

  public InMemoryBusinessCalendarConfigProvider(PickupCalendarConfig initialConfig) {
    this.configRef =
        new AtomicReference<>(
            Objects.requireNonNull(initialConfig, "initialConfig must not be null"));
  }

  public InMemoryBusinessCalendarConfigProvider() {
    this(PickupCalendarConfig.defaultConfig());
  }

  @Override
  public PickupCalendarConfig getConfig() {
    return configRef.get();
  }

  @Override
  public void updateConfig(PickupCalendarConfig newConfig) {
    configRef.set(Objects.requireNonNull(newConfig, "newConfig must not be null"));
  }
}
