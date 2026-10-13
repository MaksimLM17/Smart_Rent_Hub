package team.ylab.testsupport.base;

import org.springframework.boot.test.context.SpringBootTest;
import team.ylab.testsupport.annotation.EnablePostgresTestcontainer;
import team.ylab.testsupport.containers.ContainersEnvironment;
import team.ylab.testsupport.containers.PostgresTestContainer;

/** Base abstract class for integration tests requiring only a PostgreSQL database. */
@SpringBootTest
@EnablePostgresTestcontainer
public abstract class BasePostgresIntegrationTest {

  protected static PostgresTestContainer getPostgres() {
    return ContainersEnvironment.getPostgres();
  }
}
