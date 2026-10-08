package team.ylab.testsupport.containers;

import java.util.LinkedHashMap;
import java.util.Map;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL Testcontainer configured for Smart Rent Hub requirements: logical decoding enabled for
 * Outbox CDC / Debezium and standard schema defaults.
 */
public class PostgresTestContainer extends PostgreSQLContainer<PostgresTestContainer> {

  public static final String DEFAULT_IMAGE = "postgres:17-alpine";
  public static final String DEFAULT_DATABASE = "postgres";
  public static final String DEFAULT_USERNAME = "postgres";
  public static final String DEFAULT_PASSWORD = "postgres";

  public PostgresTestContainer() {
    this(DEFAULT_IMAGE);
  }

  public PostgresTestContainer(String dockerImageName) {
    this(DockerImageName.parse(dockerImageName));
  }

  public PostgresTestContainer(DockerImageName dockerImageName) {
    super(dockerImageName);
    withDatabaseName(DEFAULT_DATABASE);
    withUsername(DEFAULT_USERNAME);
    withPassword(DEFAULT_PASSWORD);
    withCommand(
        "postgres",
        "-c",
        "wal_level=logical",
        "-c",
        "max_replication_slots=10",
        "-c",
        "max_wal_senders=10");
  }

  /** Returns standard Spring Boot data-source configuration properties. */
  public Map<String, String> getSpringProperties() {
    Map<String, String> properties = new LinkedHashMap<>();
    properties.put("spring.datasource.url", getJdbcUrl());
    properties.put("spring.datasource.username", getUsername());
    properties.put("spring.datasource.password", getPassword());
    properties.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
    return properties;
  }
}
