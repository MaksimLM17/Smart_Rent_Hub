package team.ylab.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.server.WebSessionServerOAuth2AuthorizedClientRepository;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.WebSessionServerLogoutHandler;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import team.ylab.gateway.config.SrhGatewayProperties;

/**
 * Reactive security configuration for Gateway acting as the Backend-For-Frontend (BFF).
 *
 * <p>Configures OAuth2 login (Keycloak), server-side session management (Redis), logout handling,
 * double-submit cookie CSRF hardening for Single Page Applications, and authorized client
 * management for downstream {@code TokenRelay} (SDD v1.1 Section 4.1, 9.1, 9.7).
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

  @Bean
  public SecurityWebFilterChain springSecurityFilterChain(
      ServerHttpSecurity http,
      SrhGatewayProperties gatewayProperties,
      ReactiveClientRegistrationRepository clientRegistrationRepository,
      ServerOAuth2AuthorizedClientRepository authorizedClientRepository) {

    // Configure double-submit cookie CSRF protection for SPA / mutating API requests
    CookieServerCsrfTokenRepository csrfTokenRepository =
        CookieServerCsrfTokenRepository.withHttpOnlyFalse();
    csrfTokenRepository.setCookieName(gatewayProperties.getSecurity().getCsrf().getCookieName());
    csrfTokenRepository.setHeaderName(gatewayProperties.getSecurity().getCsrf().getHeaderName());
    csrfTokenRepository.setCookiePath(gatewayProperties.getSecurity().getCsrf().getCookiePath());
    csrfTokenRepository.setCookieCustomizer(
        cookieBuilder ->
            cookieBuilder
                .sameSite(gatewayProperties.getSecurity().getCsrf().getCookieSameSite())
                .path(gatewayProperties.getSecurity().getCsrf().getCookiePath())
                .httpOnly(false));

    ServerWebExchangeMatcher requireCsrfMatcher =
        exchange -> {
          ServerHttpRequest request = exchange.getRequest();
          HttpMethod method = request.getMethod();
          // Safe methods do not require CSRF
          if (method == HttpMethod.GET
              || method == HttpMethod.HEAD
              || method == HttpMethod.OPTIONS
              || method == HttpMethod.TRACE) {
            return ServerWebExchangeMatcher.MatchResult.notMatch();
          }
          // Mutating methods on /api/** require CSRF token
          String path = request.getURI().getPath();
          if (path.startsWith("/api/")) {
            return ServerWebExchangeMatcher.MatchResult.match();
          }
          return ServerWebExchangeMatcher.MatchResult.notMatch();
        };

    http.csrf(
        csrf ->
            csrf.csrfTokenRepository(csrfTokenRepository)
                .requireCsrfProtectionMatcher(requireCsrfMatcher)
                .csrfTokenRequestHandler(new SpaServerCsrfTokenRequestHandler())
                .accessDeniedHandler(new CustomAccessDeniedHandler()));

    http.authorizeExchange(
            exchanges ->
                exchanges
                    // Observability & Actuator
                    .pathMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus")
                    .permitAll()
                    // Next.js stubs and frontend static resources
                    .pathMatchers("/__stub/**", "/_next/**", "/favicon.ico")
                    .permitAll()
                    // Public customer browsing paths
                    .pathMatchers("/", "/catalog", "/catalog/**")
                    .permitAll()
                    // Session status inquiry endpoint
                    .pathMatchers("/api/v1/session")
                    .permitAll()
                    // Public catalog and search API
                    .pathMatchers("/api/v1/catalog/**", "/api/v1/search/**")
                    .permitAll()
                    // OAuth2 authorization and callback paths
                    .pathMatchers("/oauth2/**", "/login/**")
                    .permitAll()
                    // Staff web UI and Staff API paths require authentication
                    .pathMatchers("/staff", "/staff/**", "/api/v1/staff/**")
                    .authenticated()
                    // Customer authenticated paths
                    .pathMatchers(
                        "/orders/**",
                        "/checkout/**",
                        "/profile/**",
                        "/api/v1/orders/**",
                        "/api/v1/quotes/**")
                    .authenticated()
                    // Other requests default to permitAll for Next.js routing
                    .anyExchange()
                    .permitAll())
        .oauth2Login(
            oauth2 ->
                oauth2
                    .clientRegistrationRepository(clientRegistrationRepository)
                    .authorizedClientRepository(authorizedClientRepository)
                    .authenticationSuccessHandler(
                        new CustomServerAuthenticationSuccessHandler(gatewayProperties)))
        .logout(
            logout ->
                logout
                    .logoutUrl("/logout")
                    .logoutHandler(new WebSessionServerLogoutHandler())
                    .logoutSuccessHandler(new CustomServerLogoutSuccessHandler(gatewayProperties)))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(new CustomAuthenticationEntryPoint())
                    .accessDeniedHandler(new CustomAccessDeniedHandler()));

    return http.build();
  }

  @Bean
  public ServerOAuth2AuthorizedClientRepository authorizedClientRepository() {
    return new WebSessionServerOAuth2AuthorizedClientRepository();
  }

  @Bean
  public ReactiveOAuth2AuthorizedClientManager authorizedClientManager(
      ReactiveClientRegistrationRepository clientRegistrationRepository,
      ServerOAuth2AuthorizedClientRepository authorizedClientRepository) {

    ReactiveOAuth2AuthorizedClientProvider authorizedClientProvider =
        ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
            .authorizationCode()
            .refreshToken()
            .clientCredentials()
            .build();

    DefaultReactiveOAuth2AuthorizedClientManager authorizedClientManager =
        new DefaultReactiveOAuth2AuthorizedClientManager(
            clientRegistrationRepository, authorizedClientRepository);
    authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

    return authorizedClientManager;
  }
}
