package team.ylab.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import team.ylab.testsupport.condition.RequiresDocker;
import team.ylab.testsupport.containers.KafkaTestContainer;
import team.ylab.testsupport.containers.KeycloakTestContainer;
import team.ylab.testsupport.containers.MinioTestContainer;
import team.ylab.testsupport.containers.PostgresTestContainer;
import team.ylab.testsupport.containers.RedisTestContainer;

class ContainersConfigurationUnitTest {

  @Nested
  @DisplayName("Default constants and configuration parameters")
  class ConstantsTests {

    @Test
    @DisplayName("Verify default images across all containers")
    void testDefaultImages() {
      assertThat(PostgresTestContainer.DEFAULT_IMAGE).isEqualTo("postgres:17-alpine");
      assertThat(KafkaTestContainer.DEFAULT_IMAGE).isEqualTo("apache/kafka:3.9.0");
      assertThat(RedisTestContainer.DEFAULT_IMAGE).isEqualTo("redis:7-alpine");
      assertThat(MinioTestContainer.DEFAULT_IMAGE).isEqualTo("minio/minio:latest");
      assertThat(KeycloakTestContainer.DEFAULT_IMAGE).isEqualTo("quay.io/keycloak/keycloak:26.1");
    }

    @Test
    @DisplayName("Verify default credentials and ports")
    void testDefaultCredentialsAndPorts() {
      assertThat(PostgresTestContainer.DEFAULT_DATABASE).isEqualTo("postgres");
      assertThat(PostgresTestContainer.DEFAULT_USERNAME).isEqualTo("postgres");
      assertThat(PostgresTestContainer.DEFAULT_PASSWORD).isEqualTo("postgres");

      assertThat(RedisTestContainer.REDIS_PORT).isEqualTo(6379);

      assertThat(MinioTestContainer.DEFAULT_USER).isEqualTo("minioadmin");
      assertThat(MinioTestContainer.DEFAULT_PASSWORD).isEqualTo("minioadmin");
      assertThat(MinioTestContainer.DEFAULT_REGION).isEqualTo("us-east-1");

      assertThat(KeycloakTestContainer.KEYCLOAK_PORT).isEqualTo(8080);
      assertThat(KeycloakTestContainer.DEFAULT_REALM).isEqualTo("smartrent");
      assertThat(KeycloakTestContainer.DEFAULT_ADMIN_USER).isEqualTo("admin");
      assertThat(KeycloakTestContainer.DEFAULT_ADMIN_PASSWORD).isEqualTo("admin");
    }
  }

  @Nested
  @RequiresDocker
  @DisplayName("Container runtime configuration (requires Docker)")
  class RuntimeConfigurationTests {

    @Test
    @DisplayName("PostgreSQL test container has correct runtime configuration")
    void testPostgresConfiguration() {
      PostgresTestContainer container = new PostgresTestContainer();
      assertThat(container.getDockerImageName()).contains("postgres:17-alpine");
      assertThat(container.getDatabaseName()).isEqualTo("postgres");
      assertThat(container.getUsername()).isEqualTo("postgres");
      assertThat(container.getPassword()).isEqualTo("postgres");
    }

    @Test
    @DisplayName("Kafka test container has correct runtime configuration")
    void testKafkaConfiguration() {
      KafkaTestContainer container = new KafkaTestContainer();
      assertThat(container.getDockerImageName()).contains("apache/kafka:3.9.0");
    }

    @Test
    @DisplayName("Redis test container has correct runtime configuration")
    void testRedisConfiguration() {
      RedisTestContainer container = new RedisTestContainer();
      assertThat(container.getDockerImageName()).contains("redis:7-alpine");
      assertThat(container.getExposedPorts()).contains(6379);
    }

    @Test
    @DisplayName("MinIO test container has correct runtime configuration")
    void testMinioConfiguration() {
      MinioTestContainer container = new MinioTestContainer();
      assertThat(container.getDockerImageName()).contains("minio/minio:latest");
      assertThat(container.getUserName()).isEqualTo("minioadmin");
      assertThat(container.getPassword()).isEqualTo("minioadmin");
    }

    @Test
    @DisplayName("Keycloak test container has correct runtime configuration")
    void testKeycloakConfiguration() {
      KeycloakTestContainer container = new KeycloakTestContainer();
      assertThat(container.getDockerImageName()).contains("quay.io/keycloak/keycloak:26.1");
      assertThat(container.getExposedPorts()).contains(8080);
      assertThat(container.getRealm()).isEqualTo("smartrent");
    }
  }
}
