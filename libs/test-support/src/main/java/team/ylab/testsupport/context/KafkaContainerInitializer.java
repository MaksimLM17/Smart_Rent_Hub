package team.ylab.testsupport.context;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import team.ylab.testsupport.condition.DockerTestUtils;
import team.ylab.testsupport.containers.ContainersEnvironment;

/** Spring ApplicationContextInitializer for Apache Kafka Testcontainer. */
public class KafkaContainerInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    if (DockerTestUtils.isDockerAvailable()) {
      TestPropertyValues.of(ContainersEnvironment.getKafka().getSpringProperties())
          .applyTo(applicationContext.getEnvironment());
    }
  }
}
