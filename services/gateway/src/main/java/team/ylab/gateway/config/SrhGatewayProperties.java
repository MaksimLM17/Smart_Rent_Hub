package team.ylab.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Smart Rent Hub API Gateway routes ({@code srh.gateway.*}).
 *
 * <p>Defines target URIs for backend microservices and Next.js web applications behind the single
 * origin (SDD v1.1 Section 4.1).
 */
@Data
@ConfigurationProperties(prefix = "srh.gateway")
public class SrhGatewayProperties {

  private Routes routes = new Routes();

  @Data
  public static class Routes {

    /** Target base URI for the {@code inventory} service. */
    private String inventoryUri = "http://localhost:8082";

    /**
     * Target URI for the {@code customer-web} Next.js application. Defaults to the built-in Gateway
     * stub ({@code forward:/__stub/customer-web}) until the Next.js server is running.
     */
    private String customerWebUri = "forward:/__stub/customer-web";

    /**
     * Target URI for the {@code staff-web} Next.js application. Defaults to the built-in Gateway
     * stub ({@code forward:/__stub/staff-web}) until the Next.js server is running.
     */
    private String staffWebUri = "forward:/__stub/staff-web";
  }
}
