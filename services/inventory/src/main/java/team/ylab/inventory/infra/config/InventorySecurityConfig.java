package team.ylab.inventory.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import team.ylab.security.autoconfigure.SecurityProperties;
import team.ylab.security.jwt.KeycloakJwtAuthenticationConverter;
import team.ylab.security.sod.SodFilter;

/**
 * Spring Security configuration for Inventory service (OAuth2 Resource Server). Configures
 * stateless JWT authentication, RBAC authorities, and permits public endpoints.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class InventorySecurityConfig {

  @Bean
  public SecurityFilterChain inventorySecurityFilterChain(
      HttpSecurity http,
      KeycloakJwtAuthenticationConverter jwtConverter,
      SecurityProperties properties,
      SodFilter sodFilter,
      JwtDecoder jwtDecoder)
      throws Exception {

    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth -> {
              if (properties.getPublicEndpoints() != null
                  && !properties.getPublicEndpoints().isEmpty()) {
                auth.requestMatchers(properties.getPublicEndpoints().toArray(new String[0]))
                    .permitAll();
              }
              auth.anyRequest().authenticated();
            })
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtConverter)))
        .addFilterAfter(sodFilter, BearerTokenAuthenticationFilter.class);

    return http.build();
  }
}
