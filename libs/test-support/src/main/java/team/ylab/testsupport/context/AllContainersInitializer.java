package team.ylab.testsupport.context;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import team.ylab.testsupport.condition.DockerTestUtils;
import team.ylab.testsupport.containers.ContainersEnvironment;

/**
 * Spring ApplicationContextInitializer for all 5 core Testcontainers (PostgreSQL, Kafka, Redis,
 * MinIO, Keycloak).
 */
public class AllContainersInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    if (DockerTestUtils.isDockerAvailable()) {
      TestPropertyValues.of(ContainersEnvironment.startAll())
          .applyTo(applicationContext.getEnvironment());
    }
  }
}
