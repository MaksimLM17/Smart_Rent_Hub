package team.ylab.security.jwt;

import java.util.*;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import team.ylab.security.rbac.RolePermissionRegistry;

/**
 * Converts Keycloak JWT into {@link JwtAuthenticationToken}. Extracts roles from realm_access and
 * top-level claims, and maps them to both {@code ROLE_*} authorities and granular {@code
 * resource:action} permissions according to {@link RolePermissionRegistry}.
 */
public class KeycloakJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  private final RolePermissionRegistry rolePermissionRegistry;
  private final String principalClaimName;

  public KeycloakJwtAuthenticationConverter(RolePermissionRegistry rolePermissionRegistry) {
    this(rolePermissionRegistry, "preferred_username");
  }

  public KeycloakJwtAuthenticationConverter(
      RolePermissionRegistry rolePermissionRegistry, String principalClaimName) {
    this.rolePermissionRegistry =
        Objects.requireNonNull(rolePermissionRegistry, "rolePermissionRegistry must not be null");
    this.principalClaimName =
        principalClaimName != null ? principalClaimName : "preferred_username";
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
    String principalName = extractPrincipalName(jwt);
    return new JwtAuthenticationToken(jwt, authorities, principalName);
  }

  public Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
    Set<String> roles = extractRoles(jwt);
    Set<GrantedAuthority> authorities = new HashSet<>();

    for (String role : roles) {
      String upperRole = role.toUpperCase(Locale.ROOT);
      // Add ROLE_ authority
      if (!upperRole.startsWith("ROLE_")) {
        authorities.add(new SimpleGrantedAuthority("ROLE_" + upperRole));
      } else {
        authorities.add(new SimpleGrantedAuthority(upperRole));
      }

      // Add granular permissions mapped to this role
      Set<String> permissions = rolePermissionRegistry.getPermissionsForRole(role);
      for (String permission : permissions) {
        authorities.add(new SimpleGrantedAuthority(permission));
      }
    }

    return Collections.unmodifiableSet(authorities);
  }

  @SuppressWarnings("unchecked")
  public Set<String> extractRoles(Jwt jwt) {
    Set<String> roles = new HashSet<>();

    // 1. Check realm_access.roles
    Map<String, Object> realmAccess = jwt.getClaim("realm_access");
    if (realmAccess != null) {
      Object realmRoles = realmAccess.get("roles");
      if (realmRoles instanceof Collection<?> list) {
        for (Object r : list) {
          if (r instanceof String roleStr && !roleStr.isBlank()) {
            roles.add(roleStr.trim());
          }
        }
      }
    }

    // 2. Check top-level roles claim
    Object topRoles = jwt.getClaim("roles");
    if (topRoles instanceof Collection<?> list) {
      for (Object r : list) {
        if (r instanceof String roleStr && !roleStr.isBlank()) {
          roles.add(roleStr.trim());
        }
      }
    }

    // 3. Check resource_access.<client>.roles
    Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
    if (resourceAccess != null) {
      for (Object clientObj : resourceAccess.values()) {
        if (clientObj instanceof Map<?, ?> clientMap) {
          Object clientRoles = clientMap.get("roles");
          if (clientRoles instanceof Collection<?> list) {
            for (Object r : list) {
              if (r instanceof String roleStr && !roleStr.isBlank()) {
                roles.add(roleStr.trim());
              }
            }
          }
        }
      }
    }

    return roles;
  }

  private String extractPrincipalName(Jwt jwt) {
    String claim = jwt.getClaimAsString(principalClaimName);
    if (claim != null && !claim.isBlank()) {
      return claim;
    }
    return jwt.getSubject();
  }
}
