package team.ylab.testsupport.containers;

import java.util.LinkedHashMap;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/** Redis Testcontainer for caching, rate limiting, and distributed sessions. */
public class RedisTestContainer extends GenericContainer<RedisTestContainer> {

  public static final String DEFAULT_IMAGE = "redis:7-alpine";
  public static final int REDIS_PORT = 6379;

  public RedisTestContainer() {
    this(DEFAULT_IMAGE);
  }

  public RedisTestContainer(String dockerImageName) {
    this(DockerImageName.parse(dockerImageName));
  }

  public RedisTestContainer(DockerImageName dockerImageName) {
    super(dockerImageName);
    withExposedPorts(REDIS_PORT);
    waitingFor(Wait.forListeningPort());
  }

  public int getRedisPort() {
    return getMappedPort(REDIS_PORT);
  }

  /** Returns standard Spring Boot Redis configuration properties. */
  public Map<String, String> getSpringProperties() {
    Map<String, String> properties = new LinkedHashMap<>();
    properties.put("spring.data.redis.host", getHost());
    properties.put("spring.data.redis.port", String.valueOf(getRedisPort()));
    properties.put("spring.redis.host", getHost());
    properties.put("spring.redis.port", String.valueOf(getRedisPort()));
    return properties;
  }
}
