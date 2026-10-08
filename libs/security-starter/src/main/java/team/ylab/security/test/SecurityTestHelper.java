package team.ylab.security.test;

import java.util.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import team.ylab.security.rbac.DefaultRolePermissionRegistry;
import team.ylab.security.rbac.RolePermissionRegistry;
import team.ylab.security.rbac.SrhRole;

/**
 * Utility helper for setting up authentication, JWT tokens and MockMvc post-processors in tests.
 */
public final class SecurityTestHelper {

  private static final RolePermissionRegistry DEFAULT_REGISTRY =
      new DefaultRolePermissionRegistry();

  private SecurityTestHelper() {}

  public static MockJwtBuilder jwtBuilder() {
    return new MockJwtBuilder();
  }

  public static Jwt createJwt(String username, String... roles) {
    return new MockJwtBuilder().username(username).role(roles).build();
  }

  public static Jwt createJwt(String username, SrhRole... roles) {
    String[] roleNames = Arrays.stream(roles).map(Enum::name).toArray(String[]::new);
    return createJwt(username, roleNames);
  }

  public static JwtAuthenticationToken createAuthentication(String username, String... roles) {
    return createAuthentication(username, DEFAULT_REGISTRY, roles);
  }

  public static JwtAuthenticationToken createAuthentication(String username, SrhRole... roles) {
    String[] roleNames = Arrays.stream(roles).map(Enum::name).toArray(String[]::new);
    return createAuthentication(username, DEFAULT_REGISTRY, roleNames);
  }

  public static JwtAuthenticationToken createAuthentication(
      String username, RolePermissionRegistry registry, String... roles) {

    Jwt jwt = createJwt(username, roles);
    Set<GrantedAuthority> authorities = new HashSet<>();

    for (String role : roles) {
      String upper = role.toUpperCase(Locale.ROOT);
      if (!upper.startsWith("ROLE_")) {
        authorities.add(new SimpleGrantedAuthority("ROLE_" + upper));
      } else {
        authorities.add(new SimpleGrantedAuthority(upper));
      }
      Set<String> perms = registry.getPermissionsForRole(role);
      for (String p : perms) {
        authorities.add(new SimpleGrantedAuthority(p));
      }
    }

    return new JwtAuthenticationToken(jwt, authorities, username);
  }

  /**
   * Returns a {@link RequestPostProcessor} that sets the security context with a mock JWT
   * containing the specified roles and their mapped authorities.
   */
  public static RequestPostProcessor withJwtUser(String username, SrhRole... roles) {
    JwtAuthenticationToken authentication = createAuthentication(username, roles);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.authentication(authentication);
  }

  public static RequestPostProcessor withJwtUser(String username, String... roles) {
    JwtAuthenticationToken authentication = createAuthentication(username, roles);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.authentication(authentication);
  }
}
