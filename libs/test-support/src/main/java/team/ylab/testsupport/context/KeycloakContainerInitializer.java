package team.ylab.testsupport.context;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import team.ylab.testsupport.condition.DockerTestUtils;
import team.ylab.testsupport.containers.ContainersEnvironment;

/** Spring ApplicationContextInitializer for Keycloak IAM Testcontainer. */
public class KeycloakContainerInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    if (DockerTestUtils.isDockerAvailable()) {
      TestPropertyValues.of(ContainersEnvironment.getKeycloak().getSpringProperties())
          .applyTo(applicationContext.getEnvironment());
    }
  }
}
