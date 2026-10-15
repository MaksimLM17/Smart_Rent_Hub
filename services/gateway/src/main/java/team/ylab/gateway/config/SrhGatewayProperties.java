package team.ylab.gateway.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Smart Rent Hub API Gateway routes and BFF ({@code srh.gateway.*}).
 *
 * <p>Defines target URIs for backend microservices, Next.js web applications, session settings,
 * security redirects, CSRF hardening, and Redis rate limiting behind the single origin (SDD v1.1
 * Section 4.1, 7.2, 9.7).
 */
@Data
@ConfigurationProperties(prefix = "srh.gateway")
public class SrhGatewayProperties {

  private Routes routes = new Routes();
  private Session session = new Session();
  private Security security = new Security();
  private RateLimit rateLimit = new RateLimit();

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

    /** CSRF protection configuration for cookie-based sessions. */
    private Csrf csrf = new Csrf();

    @Data
    public static class Csrf {

      /** Whether CSRF protection is enabled. */
      private boolean enabled = true;

      /** Name of the CSRF cookie readable by frontend JavaScript (e.g. Next.js). */
      private String cookieName = "XSRF-TOKEN";

      /** Header name expected in mutating requests from the client. */
      private String headerName = "X-XSRF-TOKEN";

      /** SameSite attribute value for the CSRF cookie. */
      private String cookieSameSite = "Lax";

      /** Path for the CSRF cookie. */
      private String cookiePath = "/";

      /**
       * Allowed origins for Origin and Referer validation on mutating requests. If empty, only
       * same-origin (Gateway's own host and authority) is allowed.
       */
      private List<String> allowedOrigins =
          new ArrayList<>(
              List.of(
                  "http://localhost:3000",
                  "http://localhost:3001",
                  "http://127.0.0.1:3000",
                  "http://127.0.0.1:3001"));
    }
  }

  @Data
  public static class RateLimit {

    /** Whether rate limiting is enabled. */
    private boolean enabled = true;

    /** Number of tokens added to bucket per second (token replenishment rate). */
    private int replenishRate = 50;

    /** Maximum number of tokens in the bucket (maximum burst capacity). */
    private int burstCapacity = 100;

    /** Number of tokens consumed per request. */
    private int requestedTokens = 1;

    /** Whether to include standard {@code X-RateLimit-*} response headers. */
    private boolean includeHeaders = true;

    /** Whether to allow requests through when Redis is down (fail-open policy). */
    private boolean failOpen = true;
  }
}
