package team.ylab.gateway.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Smart Rent Hub API Gateway routes and BFF ({@code srh.gateway.*}).
 *
 * <p>Defines target URIs for backend microservices, Next.js web applications, session settings, and
 * security redirects behind the single origin (SDD v1.1 Section 4.1).
 */
@Data
@ConfigurationProperties(prefix = "srh.gateway")
public class SrhGatewayProperties {

  private Routes routes = new Routes();
  private Session session = new Session();
  private Security security = new Security();

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

  @Data
  public static class Session {

    /** Whether Redis session storage is enabled. */
    private boolean redisEnabled = true;

    /** Default session timeout (defaults to 8 hours). */
    private Duration timeout = Duration.ofHours(8);

    /** Staff session timeout (defaults to 12 hours). */
    private Duration staffTimeout = Duration.ofHours(12);

    /** Name of the session cookie. */
    private String cookieName = "SESSION";

    /** Whether the session cookie must have the Secure flag. */
    private boolean cookieSecure = false;

    /** SameSite attribute value for the session cookie (Lax, Strict, None). */
    private String cookieSameSite = "Lax";

    /** Path for the session cookie. */
    private String cookiePath = "/";
  }

  @Data
  public static class Security {

    /** Default redirect URI after successful customer login. */
    private String customerLoginRedirectUri = "/";

    /** Default redirect URI after successful staff login. */
    private String staffLoginRedirectUri = "/staff";

    /** Redirect URI after logout. */
    private String logoutSuccessUri = "/";
  }
}
