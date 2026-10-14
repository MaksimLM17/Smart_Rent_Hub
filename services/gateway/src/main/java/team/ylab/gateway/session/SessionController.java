package team.ylab.gateway.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Controller providing session state inspection for frontend UI applications ({@code customer-web}
 * and {@code staff-web}) behind the Gateway BFF (SDD v1.1 Appendix B.2, B.3).
 *
 * <p>Never exposes sensitive OAuth2 tokens (access tokens, refresh tokens, client secrets) to the
 * client.
 */
@RestController
@RequestMapping("/api/v1")
public class SessionController {

  @GetMapping(value = "/session", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<Map<String, Object>> getSession(ServerWebExchange exchange) {
    return exchange
        .getPrincipal()
        .filter(Authentication.class::isInstance)
        .cast(Authentication.class)
        .map(this::buildAuthenticatedResponse)
        .defaultIfEmpty(Map.of("authenticated", false));
  }

  private Map<String, Object> buildAuthenticatedResponse(Authentication authentication) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("authenticated", true);

    String registrationId = null;
    if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
      registrationId = oauthToken.getAuthorizedClientRegistrationId();
    }
    response.put("registrationId", registrationId);
    response.put("username", authentication.getName());

    if (authentication.getPrincipal() instanceof OAuth2User oauth2User) {
      Map<String, Object> attributes = oauth2User.getAttributes();
      if (attributes.containsKey("sub")) {
        response.put("userId", attributes.get("sub"));
      }
      if (attributes.containsKey("email")) {
        response.put("email", attributes.get("email"));
      }
      if (attributes.containsKey("name")) {
        response.put("name", attributes.get("name"));
      }
      if (attributes.containsKey("given_name")) {
        response.put("firstName", attributes.get("given_name"));
      }
      if (attributes.containsKey("family_name")) {
        response.put("lastName", attributes.get("family_name"));
      }
      if (attributes.containsKey("preferred_username")) {
        response.put("username", attributes.get("preferred_username"));
      }

      // Extract Keycloak realm roles
      List<String> roles = new ArrayList<>();
      Object realmAccessObj = attributes.get("realm_access");
      if (realmAccessObj instanceof Map<?, ?> realmAccessMap) {
        Object rolesObj = realmAccessMap.get("roles");
        if (rolesObj instanceof List<?> rolesList) {
          for (Object r : rolesList) {
            if (r instanceof String roleStr) {
              roles.add(roleStr);
            }
          }
        }
      }

      // Fallback to authorities if realm_access is not present
      if (roles.isEmpty()) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
          String auth = authority.getAuthority();
          if (auth.startsWith("ROLE_")) {
            roles.add(auth.substring(5));
          } else {
            roles.add(auth);
          }
        }
      }
      response.put("roles", roles);

      if (oauth2User instanceof OidcUser oidcUser && oidcUser.getIdToken() != null) {
        Instant expiresAt = oidcUser.getIdToken().getExpiresAt();
        if (expiresAt != null) {
          response.put("expiresAt", expiresAt.toString());
        }
        Instant issuedAt = oidcUser.getIdToken().getIssuedAt();
        if (issuedAt != null) {
          response.put("issuedAt", issuedAt.toString());
        }
      }
    }

    return response;
  }
}
