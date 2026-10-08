package team.ylab.security.rbac;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.InputStream;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Default implementation of {@link RolePermissionRegistry}. Loads role definitions and permissions
 * from YAML catalog, resolves composite roles and expands wildcards.
 */
@Slf4j
public class DefaultRolePermissionRegistry implements RolePermissionRegistry {

  private static final String DEFAULT_CATALOG_LOCATION =
      "classpath:team/ylab/security/rbac-catalog.yaml";
  private static final List<String> STANDARD_CRUD_ACTIONS =
      List.of("create", "read", "update", "delete", "list");

  private final Map<String, Set<String>> rolePermissionsMap = new HashMap<>();
  private final Set<String> registeredRoles = new HashSet<>();

  public DefaultRolePermissionRegistry() {
    this(DEFAULT_CATALOG_LOCATION);
  }

  public DefaultRolePermissionRegistry(String catalogLocation) {
    loadCatalog(catalogLocation);
  }

  public DefaultRolePermissionRegistry(Resource resource) {
    loadCatalogFromResource(resource);
  }

  private void loadCatalog(String location) {
    try {
      PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
      Resource resource = resolver.getResource(location);
      if (!resource.exists()) {
        log.warn(
            "RBAC catalog resource not found at location: {}. Falling back to default classpath.",
            location);
        resource = resolver.getResource(DEFAULT_CATALOG_LOCATION);
      }
      loadCatalogFromResource(resource);
    } catch (Exception e) {
      log.error("Failed to load RBAC catalog from {}: {}", location, e.getMessage(), e);
      throw new IllegalStateException("Could not load RBAC catalog from " + location, e);
    }
  }

  private void loadCatalogFromResource(Resource resource) {
    try (InputStream inputStream = resource.getInputStream()) {
      ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
      RbacCatalogDocument document = mapper.readValue(inputStream, RbacCatalogDocument.class);
      initialize(document);
    } catch (Exception e) {
      log.error("Error reading RBAC catalog from resource {}: {}", resource, e.getMessage(), e);
      throw new IllegalStateException("Failed to parse RBAC catalog", e);
    }
  }

  private void initialize(RbacCatalogDocument document) {
    // 1. Process standard functional roles
    if (document.getRoles() != null) {
      for (Map.Entry<String, RbacCatalogDocument.RoleEntry> entry :
          document.getRoles().entrySet()) {
        String roleName = normalizeRoleName(entry.getKey());
        Set<String> permissions = new HashSet<>();
        if (entry.getValue() != null && entry.getValue().getPermissions() != null) {
          for (String perm : entry.getValue().getPermissions()) {
            permissions.addAll(expandPermission(perm));
          }
        }
        registerRole(roleName, permissions);
      }
    }

    // 2. Process composite roles (can include other roles)
    if (document.getCompositeRoles() != null) {
      for (Map.Entry<String, RbacCatalogDocument.CompositeRoleEntry> entry :
          document.getCompositeRoles().entrySet()) {
        String compositeName = normalizeRoleName(entry.getKey());
        Set<String> compositePermissions = new HashSet<>();
        if (entry.getValue() != null && entry.getValue().getIncludedRoles() != null) {
          for (String included : entry.getValue().getIncludedRoles()) {
            String normIncluded = normalizeRoleName(included);
            Set<String> includedPerms = rolePermissionsMap.get(normIncluded);
            if (includedPerms != null) {
              compositePermissions.addAll(includedPerms);
            }
          }
        }
        registerRole(compositeName, compositePermissions);
      }
    }

    log.info(
        "Initialized RolePermissionRegistry with {} roles and total mapped authorities.",
        registeredRoles.size());
  }

  private void registerRole(String roleName, Set<String> permissions) {
    registeredRoles.add(roleName);
    rolePermissionsMap.put(roleName, Collections.unmodifiableSet(new HashSet<>(permissions)));

    // Also register with ROLE_ prefix for convenience
    if (!roleName.startsWith("ROLE_")) {
      rolePermissionsMap.put(
          "ROLE_" + roleName, Collections.unmodifiableSet(new HashSet<>(permissions)));
    }
  }

  private Set<String> expandPermission(String permission) {
    Set<String> expanded = new HashSet<>();
    if (permission == null || permission.isBlank()) {
      return expanded;
    }
    String trimmed = permission.trim();
    expanded.add(trimmed);

    // If permission has wildcard, e.g. "sku:*"
    if (trimmed.endsWith(":*")) {
      String resource = trimmed.substring(0, trimmed.length() - 2);
      for (String action : STANDARD_CRUD_ACTIONS) {
        expanded.add(resource + ":" + action);
      }
    }
    return expanded;
  }

  private String normalizeRoleName(String roleName) {
    return roleName.trim().toUpperCase(Locale.ROOT);
  }

  @Override
  public Set<String> getPermissionsForRole(String roleName) {
    if (roleName == null) {
      return Collections.emptySet();
    }
    Set<String> perms = rolePermissionsMap.get(normalizeRoleName(roleName));
    return perms != null ? perms : Collections.emptySet();
  }

  @Override
  public Set<String> getPermissionsForRoles(Collection<String> roleNames) {
    if (roleNames == null || roleNames.isEmpty()) {
      return Collections.emptySet();
    }
    Set<String> result = new HashSet<>();
    for (String role : roleNames) {
      result.addAll(getPermissionsForRole(role));
    }
    return Collections.unmodifiableSet(result);
  }

  @Override
  public boolean hasPermission(String roleName, String permission) {
    if (roleName == null || permission == null) {
      return false;
    }
    Set<String> perms = getPermissionsForRole(roleName);
    if (perms.contains(permission)) {
      return true;
    }
    // Check wildcard match e.g. if perms contains "sku:*" and permission is "sku:custom"
    int colonIdx = permission.indexOf(':');
    if (colonIdx > 0) {
      String wildcard = permission.substring(0, colonIdx) + ":*";
      return perms.contains(wildcard);
    }
    return false;
  }

  @Override
  public Set<String> getAllRegisteredRoles() {
    return Collections.unmodifiableSet(registeredRoles);
  }

  @Override
  public Map<String, Set<String>> getRolePermissionsMap() {
    return Collections.unmodifiableMap(rolePermissionsMap);
  }
}
