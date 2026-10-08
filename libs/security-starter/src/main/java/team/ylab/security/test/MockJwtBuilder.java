package team.ylab.security.test;

import java.time.Instant;
import java.util.*;
import org.springframework.security.oauth2.jwt.Jwt;

/** Fluent builder for creating mock {@link Jwt} tokens in tests. */
public class MockJwtBuilder {

  private String tokenValue = "mock-jwt-token-value";
  private Instant issuedAt = Instant.now();
  private Instant expiresAt = Instant.now().plusSeconds(3600);
  private final Map<String, Object> headers = new HashMap<>(Map.of("alg", "RS256", "typ", "JWT"));
  private final Map<String, Object> claims = new HashMap<>();
  private final Set<String> roles = new HashSet<>();

  public MockJwtBuilder() {
    claims.put("sub", UUID.randomUUID().toString());
    claims.put("preferred_username", "test-user");
  }

  public MockJwtBuilder subject(String subject) {
    claims.put("sub", subject);
    return this;
  }

  public MockJwtBuilder username(String username) {
    claims.put("preferred_username", username);
    return this;
  }

  public MockJwtBuilder email(String email) {
    claims.put("email", email);
    return this;
  }

  public MockJwtBuilder role(String... roles) {
    if (roles != null) {
      this.roles.addAll(Arrays.asList(roles));
    }
    return this;
  }

  public MockJwtBuilder claim(String name, Object value) {
    claims.put(name, value);
    return this;
  }

  public MockJwtBuilder tokenValue(String tokenValue) {
    this.tokenValue = tokenValue;
    return this;
  }

  public MockJwtBuilder issuedAt(Instant issuedAt) {
    this.issuedAt = issuedAt;
    return this;
  }

  public MockJwtBuilder expiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
    return this;
  }

  public Jwt build() {
    Map<String, Object> allClaims = new HashMap<>(this.claims);
    if (!roles.isEmpty()) {
      allClaims.put("roles", new ArrayList<>(roles));
      allClaims.put("realm_access", Map.of("roles", new ArrayList<>(roles)));
    }
    return new Jwt(tokenValue, issuedAt, expiresAt, headers, allClaims);
  }
}
