package team.ylab.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("Containers Property Mapping Unit Tests")
class ContainersPropertyMappingUnitTest {

  @Test
  @DisplayName("Verify Spring environment mapping for all container properties")
  void testAllPropertiesMapping() {
    MockEnvironment env = new MockEnvironment();

    Map<String, String> properties = new HashMap<>();
    properties.put("spring.datasource.url", "jdbc:postgresql://localhost:5432/postgres");
    properties.put("spring.datasource.username", "postgres");
    properties.put("spring.datasource.password", "postgres");
    properties.put("spring.datasource.driver-class-name", "org.postgresql.Driver");

    properties.put("spring.kafka.bootstrap-servers", "localhost:9092");

    properties.put("spring.data.redis.host", "localhost");
    properties.put("spring.data.redis.port", "6379");

    properties.put("srh.minio.endpoint", "http://localhost:9000");
    properties.put("srh.minio.access-key", "minioadmin");
    properties.put("srh.minio.secret-key", "minioadmin");
    properties.put("srh.minio.region", "us-east-1");

    properties.put("srh.keycloak.auth-server-url", "http://localhost:8080");
    properties.put("srh.keycloak.realm", "smartrent");
    properties.put(
        "spring.security.oauth2.resourceserver.jwt.issuer-uri",
        "http://localhost:8080/realms/smartrent");

    TestPropertyValues.of(properties).applyTo(env);

    assertThat(env.getProperty("spring.datasource.url"))
        .isEqualTo("jdbc:postgresql://localhost:5432/postgres");
    assertThat(env.getProperty("spring.kafka.bootstrap-servers")).isEqualTo("localhost:9092");
    assertThat(env.getProperty("spring.data.redis.port")).isEqualTo("6379");
    assertThat(env.getProperty("srh.minio.endpoint")).isEqualTo("http://localhost:9000");
    assertThat(env.getProperty("srh.keycloak.realm")).isEqualTo("smartrent");
    assertThat(env.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"))
        .isEqualTo("http://localhost:8080/realms/smartrent");
  }
}
