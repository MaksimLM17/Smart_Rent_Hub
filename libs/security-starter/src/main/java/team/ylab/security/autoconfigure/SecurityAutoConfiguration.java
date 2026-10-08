package team.ylab.security.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import team.ylab.security.context.SecurityUserContext;
import team.ylab.security.jwt.KeycloakJwtAuthenticationConverter;
import team.ylab.security.rbac.DefaultRolePermissionRegistry;
import team.ylab.security.rbac.RolePermissionRegistry;
import team.ylab.security.sod.DefaultSodPolicyEnforcer;
import team.ylab.security.sod.SodFilter;
import team.ylab.security.sod.SodPolicyEnforcer;

/**
 * Spring Boot AutoConfiguration for SRH Security Starter. Configures OAuth2 Resource Server,
 * Keycloak JWT converter with RBAC permission catalog, SoD enforcement and method-level security.
 */
@AutoConfiguration(
    beforeName = {
      "org.springframework.boot.autoconfigure.security.servlet.SpringBootWebSecurityConfiguration",
      "org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration"
    })
@EnableConfigurationProperties(SecurityProperties.class)
@EnableMethodSecurity(prePostEnabled = true)
@ConditionalOnProperty(
    prefix = "srh.security",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SecurityAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public RolePermissionRegistry rolePermissionRegistry(SecurityProperties properties) {
    return new DefaultRolePermissionRegistry(properties.getCatalogPath());
  }

  @Bean
  @ConditionalOnMissingBean
  public KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter(
      RolePermissionRegistry registry, SecurityProperties properties) {
    return new KeycloakJwtAuthenticationConverter(registry, properties.getPrincipalClaimName());
  }

  @Bean
  @ConditionalOnMissingBean
  public SecurityUserContext securityUserContext() {
    return new SecurityUserContext();
  }

  @Bean
  @ConditionalOnMissingBean
  public SodPolicyEnforcer sodPolicyEnforcer(SecurityProperties properties) {
    return new DefaultSodPolicyEnforcer(properties.isSodSingleOperatorMode());
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  public SodFilter sodFilter(
      SodPolicyEnforcer sodPolicyEnforcer, SecurityUserContext securityUserContext) {
    return new SodFilter(sodPolicyEnforcer, securityUserContext);
  }

  @Bean
  @ConditionalOnMissingBean
  public JwtDecoder jwtDecoder() {
    return token ->
        Jwt.withTokenValue(token)
            .header("alg", "none")
            .subject("mock-subject")
            .claim("scope", "read")
            .build();
  }

  @Bean
  @Order(1)
  @ConditionalOnMissingBean(SecurityFilterChain.class)
  @ConditionalOnClass(HttpSecurity.class)
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  public SecurityFilterChain defaultSecurityFilterChain(
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
