package team.ylab.security.rbac;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Registry interface for Role to Permission mappings. Allows looking up granted permissions for
 * individual roles or sets of roles.
 */
public interface RolePermissionRegistry {

  /**
   * Returns all permissions granted to a given role name. If the role is unknown, returns an empty
   * set.
   */
  Set<String> getPermissionsForRole(String roleName);

  /** Returns union of permissions granted to all given roles. */
  Set<String> getPermissionsForRoles(Collection<String> roleNames);

  /** Checks if a given role grants a specific permission. */
  boolean hasPermission(String roleName, String permission);

  /** Returns all registered role names (both functional and composite). */
  Set<String> getAllRegisteredRoles();

  /** Returns the entire role to permissions mapping. */
  Map<String, Set<String>> getRolePermissionsMap();
}
