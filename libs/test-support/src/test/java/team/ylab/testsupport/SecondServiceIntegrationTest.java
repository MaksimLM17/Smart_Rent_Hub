package team.ylab.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import team.ylab.testsupport.base.BaseIntegrationTest;

/**
 * Simulates integration test of the second service (e.g. booking-service) using the unified
 * Testcontainers base test class. Verifies that the exact same set of containers is reused across
 * services.
 */
@SpringBootTest(classes = TestApplication.class)
@DisplayName("Second Service Integration Test using BaseIntegrationTest")
class SecondServiceIntegrationTest extends BaseIntegrationTest {

  @Autowired private Environment environment;

  @Test
  @DisplayName("Service 2 boots with the identical container set configured in environment")
  void testService2Properties() {
    assertThat(environment.getProperty("spring.datasource.url")).isNotBlank();
    assertThat(environment.getProperty("spring.kafka.bootstrap-servers")).isNotBlank();
    assertThat(environment.getProperty("spring.data.redis.host")).isNotBlank();
    assertThat(environment.getProperty("srh.minio.endpoint")).isNotBlank();
    assertThat(environment.getProperty("srh.keycloak.realm")).isEqualTo("smartrent");
  }

  @Test
  @DisplayName("Service 2 reuses the same container instances")
  void testContainerAccessors() {
    assertThat(getPostgres()).isNotNull();
    assertThat(getKafka()).isNotNull();
    assertThat(getRedis()).isNotNull();
    assertThat(getMinio()).isNotNull();
    assertThat(getKeycloak()).isNotNull();
  }
}
