package team.ylab.testsupport.containers;

import java.util.LinkedHashMap;
import java.util.Map;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/** Apache Kafka Testcontainer configured for Smart Rent Hub messaging. */
public class KafkaTestContainer extends KafkaContainer {

  public static final String DEFAULT_IMAGE = "apache/kafka:3.9.0";

  public KafkaTestContainer() {
    this(DEFAULT_IMAGE);
  }

  public KafkaTestContainer(String dockerImageName) {
    this(DockerImageName.parse(dockerImageName));
  }

  public KafkaTestContainer(DockerImageName dockerImageName) {
    super(dockerImageName);
    withEnv("KAFKA_LISTENERS", "PLAINTEXT://:9092,BROKER://:9093,CONTROLLER://:9094");
  }

  /** Returns standard Spring Kafka configuration properties. */
  public Map<String, String> getSpringProperties() {
    Map<String, String> properties = new LinkedHashMap<>();
    properties.put("spring.kafka.bootstrap-servers", getBootstrapServers());
    return properties;
  }
}
