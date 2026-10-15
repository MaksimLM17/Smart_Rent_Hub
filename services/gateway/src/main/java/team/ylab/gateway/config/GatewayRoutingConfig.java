package team.ylab.gateway.config;

import java.net.URI;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures Spring Cloud Gateway routes for single-origin routing to {@code inventory}, {@code
 * staff-web}, and {@code customer-web} with TokenRelay and Redis rate limiting (SDD v1.1 Section
 * 4.1, 7.2, Appendix B).
 */
@Configuration
public class GatewayRoutingConfig {

  public static final String ORIG_REQUEST_PATH_HEADER = "X-SRH-Original-Path";

  private static final String[] INVENTORY_API_PATHS = {
    "/api/v1/catalog/**",
    "/api/v1/claims/**",
    "/api/v1/staff/skus/**",
    "/api/v1/staff/units/**",
    "/api/v1/staff/kits/**",
    "/api/v1/staff/consumables/**",
    "/api/v1/staff/media/**",
    "/api/v1/staff/pickup-tasks/**",
    "/api/v1/staff/handovers/**",
    "/api/v1/staff/returns/**",
    "/api/v1/staff/inspections/**",
    "/api/v1/staff/defects/**",
    "/api/v1/staff/claims/**",
    "/api/v1/inventory/**"
  };

  @Bean
  public RouteLocator gatewayRouteLocator(
      RouteLocatorBuilder builder,
      SrhGatewayProperties gatewayProperties,
      RedisRateLimiter redisRateLimiter,
      KeyResolver keyResolver) {
    String inventoryUri = gatewayProperties.getRoutes().getInventoryUri();
    String staffWebUri = gatewayProperties.getRoutes().getStaffWebUri();
    String customerWebUri = gatewayProperties.getRoutes().getCustomerWebUri();

    return builder
        .routes()
        .route(
            "inventory-api",
            r ->
                r.order(-10)
                    .path(INVENTORY_API_PATHS)
                    .filters(
                        f -> {
                          f.tokenRelay();
                          if (gatewayProperties.getRateLimit().isEnabled()) {
                            f.requestRateLimiter(
                                c ->
                                    c.setRateLimiter(redisRateLimiter)
                                        .setKeyResolver(keyResolver)
                                        .setThrowOnLimit(true));
                          }
                          return f;
                        })
                    .uri(inventoryUri))
        .route(
            "staff-web",
            r ->
                r.order(10)
                    .path("/staff", "/staff/**")
                    .filters(
                        f -> {
                          if (staffWebUri.startsWith("forward:")) {
                            String targetPath = URI.create(staffWebUri).getSchemeSpecificPart();
                            return f.setPath(targetPath);
                          }
                          return f;
                        })
                    .uri(staffWebUri))
        .route(
            "customer-web",
            r ->
                r.order(100)
                    .path(
                        "/",
                        "/catalog",
                        "/catalog/**",
                        "/orders",
                        "/orders/**",
                        "/checkout",
                        "/checkout/**",
                        "/profile",
                        "/profile/**",
                        "/assistant",
                        "/assistant/**",
                        "/_next/**")
                    .filters(
                        f -> {
                          if (customerWebUri.startsWith("forward:")) {
                            String targetPath = URI.create(customerWebUri).getSchemeSpecificPart();
                            return f.setPath(targetPath);
                          }
                          return f;
                        })
                    .uri(customerWebUri))
        .build();
  }
}
