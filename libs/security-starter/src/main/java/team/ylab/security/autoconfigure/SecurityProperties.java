package team.ylab.security.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import team.ylab.security.sod.SodMode;

/** Configuration properties for SRH Security Starter. */
@Data
@ConfigurationProperties(prefix = "srh.security")
public class SecurityProperties {

  /** Whether SRH Security auto-configuration is enabled. */
  private boolean enabled = true;

  /** Path to the RBAC YAML catalog. */
  private String catalogPath = "classpath:team/ylab/security/rbac-catalog.yaml";

  /** Principal claim name to use from the JWT. */
  private String principalClaimName = "preferred_username";

  /** List of public HTTP endpoints that permit unauthenticated access. */
  private List<String> publicEndpoints =
      new ArrayList<>(
          List.of(
              "/actuator/health",
              "/actuator/info",
              "/v3/api-docs/**",
              "/swagger-ui/**",
              "/swagger-ui.html"));

  /** Default SoD mode. */
  private SodMode sodDefaultMode = SodMode.SECOND_APPROVER;

  /** Whether to run SoD in single-operator mode (downgrades second-approver to log-only). */
  private boolean sodSingleOperatorMode = false;
}
