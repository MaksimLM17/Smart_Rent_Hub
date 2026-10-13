package team.ylab.testsupport.context;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.MergedContextConfiguration;
import team.ylab.testsupport.condition.DockerTestUtils;
import team.ylab.testsupport.containers.ContainersEnvironment;

/**
 * Spring Test {@link ContextCustomizer} that starts and binds the requested Testcontainers into the
 * application environment. Supports combining multiple {@code @Enable*Testcontainer} annotations on
 * the same test class.
 */
public class TestcontainersContextCustomizer implements ContextCustomizer {

  public enum ContainerType {
    POSTGRES,
    KAFKA,
    REDIS,
    MINIO,
    KEYCLOAK
  }

  private final Set<ContainerType> enabledContainers;

  public TestcontainersContextCustomizer(Set<ContainerType> enabledContainers) {
    this.enabledContainers = Collections.unmodifiableSet(EnumSet.copyOf(enabledContainers));
  }

  public Set<ContainerType> getEnabledContainers() {
    return enabledContainers;
  }

  @Override
  public void customizeContext(
      ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
    if (!DockerTestUtils.isDockerAvailable()) {
      return;
    }
    if (enabledContainers.contains(ContainerType.POSTGRES)) {
      TestPropertyValues.of(ContainersEnvironment.getPostgres().getSpringProperties())
          .applyTo(context.getEnvironment());
    }
    if (enabledContainers.contains(ContainerType.KAFKA)) {
      TestPropertyValues.of(ContainersEnvironment.getKafka().getSpringProperties())
          .applyTo(context.getEnvironment());
    }
    if (enabledContainers.contains(ContainerType.REDIS)) {
      TestPropertyValues.of(ContainersEnvironment.getRedis().getSpringProperties())
          .applyTo(context.getEnvironment());
    }
    if (enabledContainers.contains(ContainerType.MINIO)) {
      TestPropertyValues.of(ContainersEnvironment.getMinio().getSpringProperties())
          .applyTo(context.getEnvironment());
    }
    if (enabledContainers.contains(ContainerType.KEYCLOAK)) {
      TestPropertyValues.of(ContainersEnvironment.getKeycloak().getSpringProperties())
          .applyTo(context.getEnvironment());
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TestcontainersContextCustomizer that)) {
      return false;
    }
    return Objects.equals(enabledContainers, that.enabledContainers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(enabledContainers);
  }
}
