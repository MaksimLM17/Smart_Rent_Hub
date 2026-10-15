package team.ylab.gateway.security;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import reactor.core.publisher.Mono;
import team.ylab.gateway.config.SrhGatewayProperties;

/**
 * Handles successful logout in the Gateway BFF.
 *
 * <p>Clears the session cookie and returns a JSON response for API/AJAX callers, or redirects
 * browser clients to the configured logout landing URI ({@code /}).
 */
public class CustomServerLogoutSuccessHandler implements ServerLogoutSuccessHandler {

  private final SrhGatewayProperties gatewayProperties;

  public CustomServerLogoutSuccessHandler(SrhGatewayProperties gatewayProperties) {
    this.gatewayProperties = gatewayProperties;
  }

  @Override
  public Mono<Void> onLogoutSuccess(WebFilterExchange exchange, Authentication authentication) {
    ServerHttpRequest request = exchange.getExchange().getRequest();
    ServerHttpResponse response = exchange.getExchange().getResponse();

    // Expire the session cookie explicitly
    ResponseCookie expiredCookie =
        ResponseCookie.from(gatewayProperties.getSession().getCookieName(), "")
            .path(gatewayProperties.getSession().getCookiePath())
            .maxAge(0)
            .httpOnly(true)
            .sameSite(gatewayProperties.getSession().getCookieSameSite())
            .build();
    response.addCookie(expiredCookie);

    String accept = request.getHeaders().getFirst(HttpHeaders.ACCEPT);
    String path = request.getURI().getPath();

    if (path.startsWith("/api/")
        || (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE))) {
      response.setStatusCode(HttpStatus.OK);
      response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
      byte[] json =
          "{\"status\":\"LOGGED_OUT\",\"authenticated\":false}".getBytes(StandardCharsets.UTF_8);
      DataBuffer buffer = response.bufferFactory().wrap(json);
      return response.writeWith(Mono.just(buffer));
    }

    response.setStatusCode(HttpStatus.FOUND);
    response
        .getHeaders()
        .setLocation(URI.create(gatewayProperties.getSecurity().getLogoutSuccessUri()));
    return response.setComplete();
  }
}
