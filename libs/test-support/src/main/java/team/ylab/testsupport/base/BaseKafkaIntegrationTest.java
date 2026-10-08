package team.ylab.testsupport.base;

import org.springframework.boot.test.context.SpringBootTest;
import team.ylab.testsupport.annotation.EnableKafkaTestcontainer;
import team.ylab.testsupport.annotation.EnablePostgresTestcontainer;
import team.ylab.testsupport.containers.ContainersEnvironment;
import team.ylab.testsupport.containers.KafkaTestContainer;
import team.ylab.testsupport.containers.PostgresTestContainer;

/** Base abstract class for event-driven integration tests requiring PostgreSQL and Apache Kafka. */
@SpringBootTest
@EnablePostgresTestcontainer
@EnableKafkaTestcontainer
public abstract class BaseKafkaIntegrationTest {

  protected static PostgresTestContainer getPostgres() {
    return ContainersEnvironment.getPostgres();
  }

  protected static KafkaTestContainer getKafka() {
    return ContainersEnvironment.getKafka();
  }
}
