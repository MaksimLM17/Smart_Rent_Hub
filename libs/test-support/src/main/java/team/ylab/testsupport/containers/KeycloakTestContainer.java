package team.ylab.testsupport.containers;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/** Keycloak Testcontainer pre-configured with realm-smartrent.json and dev-mode import. */
public class KeycloakTestContainer extends GenericContainer<KeycloakTestContainer> {

  public static final String DEFAULT_IMAGE = "quay.io/keycloak/keycloak:26.1";
  public static final int KEYCLOAK_PORT = 8080;
  public static final String DEFAULT_REALM = "smartrent";
  public static final String DEFAULT_ADMIN_USER = "admin";
  public static final String DEFAULT_ADMIN_PASSWORD = "admin";

  public KeycloakTestContainer() {
    this(DEFAULT_IMAGE);
  }

  public KeycloakTestContainer(String dockerImageName) {
    this(DockerImageName.parse(dockerImageName));
  }

  public KeycloakTestContainer(DockerImageName dockerImageName) {
    super(dockerImageName);
    withExposedPorts(KEYCLOAK_PORT);
    withEnv("KEYCLOAK_ADMIN", DEFAULT_ADMIN_USER);
    withEnv("KEYCLOAK_ADMIN_PASSWORD", DEFAULT_ADMIN_PASSWORD);
    withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", DEFAULT_ADMIN_USER);
    withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", DEFAULT_ADMIN_PASSWORD);
    withEnv("KC_HEALTH_ENABLED", "true");
    withEnv("KC_HTTP_PORT", String.valueOf(KEYCLOAK_PORT));
    withCommand("start-dev", "--import-realm");
    withCopyFileToContainer(
        MountableFile.forClasspathResource("/keycloak/realm-smartrent.json", 0644),
        "/opt/keycloak/data/import/realm-smartrent.json");
    waitingFor(
        Wait.forHttp("/realms/" + DEFAULT_REALM)
            .forPort(KEYCLOAK_PORT)
            .forStatusCode(200)
            .withStartupTimeout(Duration.ofMinutes(2)));
  }

  public String getAuthServerUrl() {
    return "http://" + getHost() + ":" + getMappedPort(KEYCLOAK_PORT);
  }

  public String getIssuerUri() {
    return getAuthServerUrl() + "/realms/" + DEFAULT_REALM;
  }

  public String getRealm() {
    return DEFAULT_REALM;
  }

  /** Returns Spring Security OAuth2 / Keycloak properties for application configuration. */
  public Map<String, String> getSpringProperties() {
    Map<String, String> properties = new LinkedHashMap<>();
    String issuer = getIssuerUri();
    String authUrl = getAuthServerUrl();

    properties.put("srh.keycloak.auth-server-url", authUrl);
    properties.put("srh.keycloak.realm", DEFAULT_REALM);
    properties.put("srh.keycloak.admin-user", DEFAULT_ADMIN_USER);
    properties.put("srh.keycloak.admin-password", DEFAULT_ADMIN_PASSWORD);
    properties.put("spring.security.oauth2.resourceserver.jwt.issuer-uri", issuer);
    properties.put("spring.security.oauth2.client.provider.keycloak.issuer-uri", issuer);

    return properties;
  }
}
