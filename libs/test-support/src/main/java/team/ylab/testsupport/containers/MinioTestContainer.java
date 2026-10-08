package team.ylab.testsupport.containers;

import java.util.LinkedHashMap;
import java.util.Map;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.utility.DockerImageName;

/** MinIO Testcontainer for S3-compatible object storage (photos, media, documents). */
public class MinioTestContainer extends MinIOContainer {

  public static final String DEFAULT_IMAGE = "minio/minio:latest";
  public static final String DEFAULT_USER = "minioadmin";
  public static final String DEFAULT_PASSWORD = "minioadmin";
  public static final String DEFAULT_REGION = "us-east-1";

  public MinioTestContainer() {
    this(DEFAULT_IMAGE);
  }

  public MinioTestContainer(String dockerImageName) {
    this(DockerImageName.parse(dockerImageName));
  }

  public MinioTestContainer(DockerImageName dockerImageName) {
    super(dockerImageName);
    withUserName(DEFAULT_USER);
    withPassword(DEFAULT_PASSWORD);
  }

  /** Returns MinIO / AWS S3 properties for application configuration. */
  public Map<String, String> getSpringProperties() {
    Map<String, String> properties = new LinkedHashMap<>();
    String s3Url = getS3URL();
    properties.put("srh.minio.endpoint", s3Url);
    properties.put("srh.minio.access-key", getUserName());
    properties.put("srh.minio.secret-key", getPassword());
    properties.put("srh.minio.region", DEFAULT_REGION);

    // Standard Spring Cloud AWS / AWS SDK properties
    properties.put("spring.cloud.aws.s3.endpoint", s3Url);
    properties.put("spring.cloud.aws.credentials.access-key", getUserName());
    properties.put("spring.cloud.aws.credentials.secret-key", getPassword());
    properties.put("spring.cloud.aws.region.static", DEFAULT_REGION);
    return properties;
  }
}
