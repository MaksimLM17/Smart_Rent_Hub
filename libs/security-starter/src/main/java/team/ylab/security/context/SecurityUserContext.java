package team.ylab.security.context;

import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Helper component providing type-safe access to the authenticated user's context, claims,
 * permissions and resource ownership check (sub = resource.owner_id).
 */
@Component
public class SecurityUserContext {

  public Optional<Authentication> getAuthentication() {
    return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication());
  }

  public Optional<Jwt> getJwt() {
    return getAuthentication()
        .filter(auth -> auth instanceof JwtAuthenticationToken)
        .map(auth -> ((JwtAuthenticationToken) auth).getToken());
  }

  public Optional<String> getUserId() {
    return getJwt().map(Jwt::getSubject);
  }

  public Optional<String> getUsername() {
    return getAuthentication().map(Authentication::getName);
  }

  public Optional<String> getEmail() {
    return getJwt().map(jwt -> jwt.getClaimAsString("email"));
  }

  public Set<String> getRoles() {
    Set<String> roles = new HashSet<>();
    getAuthentication()
        .ifPresent(
            auth -> {
              for (GrantedAuthority ga : auth.getAuthorities()) {
                String authority = ga.getAuthority();
                if (authority.startsWith("ROLE_")) {
                  roles.add(authority.substring(5));
                }
              }
            });
    return Collections.unmodifiableSet(roles);
  }

  public Set<String> getPermissions() {
    Set<String> perms = new HashSet<>();
    getAuthentication()
        .ifPresent(
            auth -> {
              for (GrantedAuthority ga : auth.getAuthorities()) {
                String authority = ga.getAuthority();
                if (!authority.startsWith("ROLE_")) {
                  perms.add(authority);
                }
              }
            });
    return Collections.unmodifiableSet(perms);
  }

  public boolean hasPermission(String permission) {
    if (permission == null || permission.isBlank()) {
      return false;
    }
    return getPermissions().contains(permission.trim());
  }

  public boolean hasRole(String role) {
    if (role == null || role.isBlank()) {
      return false;
    }
    String norm = role.trim().toUpperCase(Locale.ROOT);
    if (norm.startsWith("ROLE_")) {
      norm = norm.substring(5);
    }
    return getRoles().contains(norm);
  }

  /**
   * Verifies if the current authenticated user is the owner of the resource by comparing the JWT
   * {@code sub} with {@code resourceOwnerId}.
   */
  public boolean isResourceOwner(String resourceOwnerId) {
    if (resourceOwnerId == null || resourceOwnerId.isBlank()) {
      return false;
    }
    return getUserId().map(id -> id.equals(resourceOwnerId.trim())).orElse(false);
  }

  @SuppressWarnings("unchecked")
  public <T> Optional<T> getClaim(String claimName) {
    return getJwt().map(jwt -> (T) jwt.getClaim(claimName));
  }
}
