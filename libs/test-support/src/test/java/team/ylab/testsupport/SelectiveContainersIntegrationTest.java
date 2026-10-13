package team.ylab.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import team.ylab.testsupport.annotation.EnablePostgresTestcontainer;
import team.ylab.testsupport.annotation.EnableRedisTestcontainer;

@SpringBootTest(classes = TestApplication.class)
@EnablePostgresTestcontainer
@EnableRedisTestcontainer
@DisplayName("Selective Containers Integration Test")
class SelectiveContainersIntegrationTest {

  @Autowired private Environment environment;

  @Test
  @DisplayName("Service configuring only Postgres and Redis receives only those properties")
  void testSelectiveProperties() {
    assertThat(environment.getProperty("spring.datasource.url")).isNotBlank();
    assertThat(environment.getProperty("spring.data.redis.host")).isNotBlank();
    assertThat(environment.getProperty("spring.kafka.bootstrap-servers")).isNull();
  }
}
