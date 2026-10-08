package team.ylab.security.rbac;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RbacCatalogDocument {

  private String version;
  private Map<String, RoleEntry> roles = Collections.emptyMap();
  private Map<String, CompositeRoleEntry> compositeRoles = Collections.emptyMap();

  @Data
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class RoleEntry {
    private String description;
    private List<String> permissions = Collections.emptyList();
  }

  @Data
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class CompositeRoleEntry {
    private String description;
    private List<String> includedRoles = Collections.emptyList();
  }
}
