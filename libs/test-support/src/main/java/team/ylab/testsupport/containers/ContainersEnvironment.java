package team.ylab.testsupport.containers;

import java.util.LinkedHashMap;
import java.util.Map;
import team.ylab.testsupport.condition.DockerTestUtils;

/**
 * Singleton lifecycle manager for shared Testcontainers instances across integration tests.
 * Prevents multiple container restarts within the same JVM test run.
 */
public final class ContainersEnvironment {

  private static volatile PostgresTestContainer postgresContainer;
  private static volatile KafkaTestContainer kafkaContainer;
  private static volatile RedisTestContainer redisContainer;
  private static volatile MinioTestContainer minioContainer;
  private static volatile KeycloakTestContainer keycloakContainer;

  private ContainersEnvironment() {}

  public static PostgresTestContainer getPostgres() {
    if (postgresContainer == null) {
      synchronized (ContainersEnvironment.class) {
        if (postgresContainer == null) {
          ensureDockerAvailable();
          PostgresTestContainer container = new PostgresTestContainer();
          container.start();
          postgresContainer = container;
        }
      }
    }
    return postgresContainer;
  }

  public static KafkaTestContainer getKafka() {
    if (kafkaContainer == null) {
      synchronized (ContainersEnvironment.class) {
        if (kafkaContainer == null) {
          ensureDockerAvailable();
          KafkaTestContainer container = new KafkaTestContainer();
          container.start();
          kafkaContainer = container;
        }
      }
    }
    return kafkaContainer;
  }

  public static RedisTestContainer getRedis() {
    if (redisContainer == null) {
      synchronized (ContainersEnvironment.class) {
        if (redisContainer == null) {
          ensureDockerAvailable();
          RedisTestContainer container = new RedisTestContainer();
          container.start();
          redisContainer = container;
        }
      }
    }
    return redisContainer;
  }

  public static MinioTestContainer getMinio() {
    if (minioContainer == null) {
      synchronized (ContainersEnvironment.class) {
        if (minioContainer == null) {
          ensureDockerAvailable();
          MinioTestContainer container = new MinioTestContainer();
          container.start();
          minioContainer = container;
        }
      }
    }
    return minioContainer;
  }

  public static KeycloakTestContainer getKeycloak() {
    if (keycloakContainer == null) {
      synchronized (ContainersEnvironment.class) {
        if (keycloakContainer == null) {
          ensureDockerAvailable();
          KeycloakTestContainer container = new KeycloakTestContainer();
          container.start();
          keycloakContainer = container;
        }
      }
    }
    return keycloakContainer;
  }

  /**
   * Starts all 5 containers (PostgreSQL, Kafka, Redis, MinIO, Keycloak) and aggregates their Spring
   * configuration properties.
   */
  public static Map<String, String> startAll() {
    Map<String, String> properties = new LinkedHashMap<>();
    properties.putAll(getPostgres().getSpringProperties());
    properties.putAll(getKafka().getSpringProperties());
    properties.putAll(getRedis().getSpringProperties());
    properties.putAll(getMinio().getSpringProperties());
    properties.putAll(getKeycloak().getSpringProperties());
    return properties;
  }

  private static void ensureDockerAvailable() {
    if (!DockerTestUtils.isDockerAvailable()) {
      throw new IllegalStateException(
          "Docker environment is not available. Testcontainers cannot be started.");
    }
  }
}
