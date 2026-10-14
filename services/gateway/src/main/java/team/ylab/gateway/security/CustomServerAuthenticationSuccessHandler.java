package team.ylab.gateway.security;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.savedrequest.ServerRequestCache;
import org.springframework.security.web.server.savedrequest.WebSessionServerRequestCache;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import team.ylab.gateway.config.SrhGatewayProperties;

/**
 * Handles successful OAuth2 authentication in the Gateway BFF.
 *
 * <p>Redirects staff users to {@code /staff} (or their original saved request), and customers to
 * {@code /} (or their original saved request).
 */
public class CustomServerAuthenticationSuccessHandler
    implements ServerAuthenticationSuccessHandler {

  private final ServerRequestCache requestCache = new WebSessionServerRequestCache();
  private final SrhGatewayProperties gatewayProperties;

  public CustomServerAuthenticationSuccessHandler(SrhGatewayProperties gatewayProperties) {
    this.gatewayProperties = gatewayProperties;
  }

  @Override
  public Mono<Void> onAuthenticationSuccess(
      WebFilterExchange webFilterExchange, Authentication authentication) {
    ServerWebExchange exchange = webFilterExchange.getExchange();

    String registrationId = null;
    if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
      registrationId = oauthToken.getAuthorizedClientRegistrationId();
    }

    String defaultRedirectUri =
        "staff".equalsIgnoreCase(registrationId)
            ? gatewayProperties.getSecurity().getStaffLoginRedirectUri()
            : gatewayProperties.getSecurity().getCustomerLoginRedirectUri();

    return requestCache
        .getRedirectUri(exchange)
        .defaultIfEmpty(URI.create(defaultRedirectUri))
        .flatMap(
            targetUri -> {
              ServerHttpResponse response = exchange.getResponse();
              response.setStatusCode(HttpStatus.FOUND);
              response.getHeaders().setLocation(targetUri);
              return response.setComplete();
            });
  }
}
