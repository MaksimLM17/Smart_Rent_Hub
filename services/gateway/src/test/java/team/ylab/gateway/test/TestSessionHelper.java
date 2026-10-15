package team.ylab.gateway.test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.web.server.WebSession;
import org.springframework.web.server.session.WebSessionManager;

/** Test helper for populating authenticated WebSessions in unit and integration tests. */
public final class TestSessionHelper {

  private TestSessionHelper() {}

  public static String createAuthenticatedSession(
      WebSessionManager sessionManager,
      String registrationId,
      String username,
      List<String> roles,
      String accessTokenValue) {

    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/").build());
    WebSession session = sessionManager.getSession(exchange).block();
    if (session == null) {
      throw new IllegalStateException("Failed to obtain WebSession from WebSessionManager");
    }

    List<SimpleGrantedAuthority> authorities =
        roles.stream()
            .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
            .map(SimpleGrantedAuthority::new)
            .toList();

    Map<String, Object> attributes =
        Map.of(
            "sub",
            "user-" + username,
            "preferred_username",
            username,
            "email",
            username + "@smartrent.local",
            "name",
            username.toUpperCase(),
            "realm_access",
            Map.of("roles", roles));

    OAuth2User oauth2User = new DefaultOAuth2User(authorities, attributes, "preferred_username");
    OAuth2AuthenticationToken authentication =
        new OAuth2AuthenticationToken(oauth2User, authorities, registrationId);

    SecurityContext securityContext = new SecurityContextImpl(authentication);
    session
        .getAttributes()
        .put(
            WebSessionServerSecurityContextRepository.DEFAULT_SPRING_SECURITY_CONTEXT_ATTR_NAME,
            securityContext);

    if (accessTokenValue != null) {
      ClientRegistration clientRegistration =
          ClientRegistration.withRegistrationId(registrationId)
              .clientId("srh-" + registrationId)
              .clientSecret("secret")
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("http://localhost:8080/login/oauth2/code/" + registrationId)
              .authorizationUri("http://localhost:8081/auth")
              .tokenUri("http://localhost:8081/token")
              .build();

      OAuth2AccessToken accessToken =
          new OAuth2AccessToken(
              OAuth2AccessToken.TokenType.BEARER,
              accessTokenValue,
              Instant.now(),
              Instant.now().plusSeconds(3600),
              Collections.singleton("openid"));

      OAuth2AuthorizedClient authorizedClient =
          new OAuth2AuthorizedClient(clientRegistration, username, accessToken);

      session
          .getAttributes()
          .put(
              "org.springframework.security.oauth2.client.web.server.WebSessionServerOAuth2AuthorizedClientRepository.AUTHORIZED_CLIENTS",
              Map.of(registrationId, authorizedClient));
    }

    session.save().block();
    return session.getId();
  }
}
