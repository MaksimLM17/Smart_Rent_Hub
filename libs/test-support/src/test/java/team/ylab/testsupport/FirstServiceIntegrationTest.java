package team.ylab.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import team.ylab.testsupport.base.BaseIntegrationTest;

/**
 * Simulates integration test of the first service (e.g. inventory-service) using the unified
 * Testcontainers base test class.
 */
@SpringBootTest(classes = TestApplication.class)
@DisplayName("First Service Integration Test using BaseIntegrationTest")
class FirstServiceIntegrationTest extends BaseIntegrationTest {

  @Autowired private Environment environment;

  @Test
  @DisplayName("Service 1 boots with all 5 containers configured in environment")
  void testService1Properties() {
    assertThat(environment.getProperty("spring.datasource.url")).isNotBlank();
    assertThat(environment.getProperty("spring.kafka.bootstrap-servers")).isNotBlank();
    assertThat(environment.getProperty("spring.data.redis.host")).isNotBlank();
    assertThat(environment.getProperty("srh.minio.endpoint")).isNotBlank();
    assertThat(environment.getProperty("srh.keycloak.realm")).isEqualTo("smartrent");
  }

  @Test
  @DisplayName("Service 1 has access to container instance accessors")
  void testContainerAccessors() {
    assertThat(getPostgres()).isNotNull();
    assertThat(getKafka()).isNotNull();
    assertThat(getRedis()).isNotNull();
    assertThat(getMinio()).isNotNull();
    assertThat(getKeycloak()).isNotNull();
  }
}
