package team.ylab.testsupport.base;

import org.springframework.boot.test.context.SpringBootTest;
import team.ylab.testsupport.annotation.EnableAllTestcontainers;
import team.ylab.testsupport.containers.ContainersEnvironment;
import team.ylab.testsupport.containers.KafkaTestContainer;
import team.ylab.testsupport.containers.KeycloakTestContainer;
import team.ylab.testsupport.containers.MinioTestContainer;
import team.ylab.testsupport.containers.PostgresTestContainer;
import team.ylab.testsupport.containers.RedisTestContainer;

/**
 * Base abstract class for full-stack integration tests. Automatically provisions and binds
 * PostgreSQL, Kafka, Redis, MinIO, and Keycloak.
 */
@SpringBootTest
@EnableAllTestcontainers
public abstract class BaseIntegrationTest {

  protected static PostgresTestContainer getPostgres() {
    return ContainersEnvironment.getPostgres();
  }

  protected static KafkaTestContainer getKafka() {
    return ContainersEnvironment.getKafka();
  }

  protected static RedisTestContainer getRedis() {
    return ContainersEnvironment.getRedis();
  }

  protected static MinioTestContainer getMinio() {
    return ContainersEnvironment.getMinio();
  }

  protected static KeycloakTestContainer getKeycloak() {
    return ContainersEnvironment.getKeycloak();
  }
}
