package team.ylab.gateway.security;

import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Custom CSRF token request handler designed for Single Page Applications (Next.js SPA).
 *
 * <p>Unlike the default {@code XorServerCsrfTokenRequestAttributeHandler}, this handler does not
 * mask the token with XOR encoding, allowing the SPA to read the raw token from the cookie or
 * {@code GET /api/v1/session} and echo it back in either {@code X-XSRF-TOKEN} or {@code
 * X-CSRF-TOKEN} headers.
 */
public class SpaServerCsrfTokenRequestHandler extends ServerCsrfTokenRequestAttributeHandler {

  private final ServerCsrfTokenRequestHandler delegate =
      new ServerCsrfTokenRequestAttributeHandler();

  @Override
  public void handle(ServerWebExchange exchange, Mono<CsrfToken> csrfToken) {
    this.delegate.handle(exchange, csrfToken);
  }

  @Override
  public Mono<String> resolveCsrfTokenValue(ServerWebExchange exchange, CsrfToken csrfToken) {
    // 1. Check primary X-XSRF-TOKEN header
    String token = exchange.getRequest().getHeaders().getFirst("X-XSRF-TOKEN");
    if (StringUtils.hasText(token)) {
      return Mono.just(token.trim());
    }

    // 2. Check alternative X-CSRF-TOKEN header
    token = exchange.getRequest().getHeaders().getFirst("X-CSRF-TOKEN");
    if (StringUtils.hasText(token)) {
      return Mono.just(token.trim());
    }

    // 3. Fallback to delegate (form parameters: _csrf)
    return this.delegate.resolveCsrfTokenValue(exchange, csrfToken);
  }
}
