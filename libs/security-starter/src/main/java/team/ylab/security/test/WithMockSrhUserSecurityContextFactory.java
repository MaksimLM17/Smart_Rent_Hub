package team.ylab.security.test;

import java.util.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.support.WithSecurityContextFactory;
import team.ylab.security.rbac.DefaultRolePermissionRegistry;
import team.ylab.security.rbac.RolePermissionRegistry;

/** SecurityContextFactory for {@link WithMockSrhUser}. */
public class WithMockSrhUserSecurityContextFactory
    implements WithSecurityContextFactory<WithMockSrhUser> {

  private final RolePermissionRegistry registry = new DefaultRolePermissionRegistry();

  @Override
  public SecurityContext createSecurityContext(WithMockSrhUser annotation) {
    SecurityContext context = SecurityContextHolder.createEmptyContext();

    String username = annotation.username();
    String userId = annotation.userId();
    String[] roles = annotation.roles();

    Jwt jwt = new MockJwtBuilder().subject(userId).username(username).role(roles).build();

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

    JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, authorities, username);
    context.setAuthentication(authentication);
    return context;
  }
}
