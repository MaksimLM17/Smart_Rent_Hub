package team.ylab.gateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.config.ReactiveSessionRepositoryCustomizer;
import org.springframework.session.data.redis.ReactiveRedisSessionRepository;
import org.springframework.session.data.redis.config.ConfigureReactiveRedisAction;
import org.springframework.session.data.redis.config.annotation.web.server.EnableRedisWebSession;
import org.springframework.web.server.session.CookieWebSessionIdResolver;
import org.springframework.web.server.session.WebSessionIdResolver;

/**
 * Configures WebSession resolution and distributed server-side session storage in Redis for the
 * Gateway BFF (SDD v1.1 Section 4.1).
 */
@Configuration(proxyBeanMethods = false)
public class GatewaySessionConfig {

  @Bean
  public WebSessionIdResolver webSessionIdResolver(SrhGatewayProperties properties) {
    CookieWebSessionIdResolver resolver = new CookieWebSessionIdResolver();
    resolver.setCookieName(properties.getSession().getCookieName());
    resolver.addCookieInitializer(
        builder -> {
          builder.path(properties.getSession().getCookiePath());
          builder.sameSite(properties.getSession().getCookieSameSite());
          builder.httpOnly(true);
          if (properties.getSession().isCookieSecure()) {
            builder.secure(true);
          }
        });
    return resolver;
  }

  @Bean
  public ConfigureReactiveRedisAction configureReactiveRedisAction() {
    return ConfigureReactiveRedisAction.NO_OP;
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(
      name = "srh.gateway.session.redis-enabled",
      havingValue = "true",
      matchIfMissing = true)
  @EnableRedisWebSession(maxInactiveIntervalInSeconds = 28800)
  public static class RedisWebSessionConfig {

    @Bean
    public ReactiveSessionRepositoryCustomizer<ReactiveRedisSessionRepository>
        sessionRepositoryCustomizer(SrhGatewayProperties properties) {
      return repository -> {
        repository.setDefaultMaxInactiveInterval(properties.getSession().getTimeout());
      };
    }
  }
}
