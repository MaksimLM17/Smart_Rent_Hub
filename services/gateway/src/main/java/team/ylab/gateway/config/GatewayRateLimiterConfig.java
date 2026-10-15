package team.ylab.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration for Gateway Redis rate limiting (SDD v1.1 Section 4.1, 7.2).
 *
 * <p>Exposes the primary {@link RedisRateLimiter} configured with token-bucket parameters from
 * {@link SrhGatewayProperties.RateLimit}.
 */
@Configuration(proxyBeanMethods = false)
public class GatewayRateLimiterConfig {

  @Bean
  @Primary
  public RedisRateLimiter redisRateLimiter(SrhGatewayProperties gatewayProperties) {
    SrhGatewayProperties.RateLimit config = gatewayProperties.getRateLimit();
    RedisRateLimiter rateLimiter =
        new RedisRateLimiter(
            config.getReplenishRate(), config.getBurstCapacity(), config.getRequestedTokens());
    rateLimiter.setIncludeHeaders(config.isIncludeHeaders());
    return rateLimiter;
  }
}
