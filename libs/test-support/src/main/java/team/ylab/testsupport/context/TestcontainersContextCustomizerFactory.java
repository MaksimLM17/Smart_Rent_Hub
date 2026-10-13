package team.ylab.testsupport.context;

import java.util.EnumSet;
import java.util.List;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.TestContextAnnotationUtils;
import team.ylab.testsupport.annotation.EnableAllTestcontainers;
import team.ylab.testsupport.annotation.EnableKafkaTestcontainer;
import team.ylab.testsupport.annotation.EnableKeycloakTestcontainer;
import team.ylab.testsupport.annotation.EnableMinioTestcontainer;
import team.ylab.testsupport.annotation.EnablePostgresTestcontainer;
import team.ylab.testsupport.annotation.EnableRedisTestcontainer;
import team.ylab.testsupport.context.TestcontainersContextCustomizer.ContainerType;

/**
 * Factory that inspects test classes for {@code @Enable*Testcontainer} annotations and creates a
 * composite {@link TestcontainersContextCustomizer}.
 */
public class TestcontainersContextCustomizerFactory implements ContextCustomizerFactory {

  @Override
  public ContextCustomizer createContextCustomizer(
      Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
    EnumSet<ContainerType> enabled = EnumSet.noneOf(ContainerType.class);

    if (TestContextAnnotationUtils.hasAnnotation(testClass, EnableAllTestcontainers.class)) {
      enabled.addAll(EnumSet.allOf(ContainerType.class));
    } else {
      if (TestContextAnnotationUtils.hasAnnotation(testClass, EnablePostgresTestcontainer.class)) {
        enabled.add(ContainerType.POSTGRES);
      }
      if (TestContextAnnotationUtils.hasAnnotation(testClass, EnableKafkaTestcontainer.class)) {
        enabled.add(ContainerType.KAFKA);
      }
      if (TestContextAnnotationUtils.hasAnnotation(testClass, EnableRedisTestcontainer.class)) {
        enabled.add(ContainerType.REDIS);
      }
      if (TestContextAnnotationUtils.hasAnnotation(testClass, EnableMinioTestcontainer.class)) {
        enabled.add(ContainerType.MINIO);
      }
      if (TestContextAnnotationUtils.hasAnnotation(testClass, EnableKeycloakTestcontainer.class)) {
        enabled.add(ContainerType.KEYCLOAK);
      }
    }

    if (enabled.isEmpty()) {
      return null;
    }
    return new TestcontainersContextCustomizer(enabled);
  }
}
