package team.ylab.gateway.test;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lightweight in-memory mock OpenID Connect / Keycloak server for Gateway BFF integration tests.
 *
 * <p>Serves JWKS certificates, token exchange endpoint, and userinfo endpoint with real RSA-signed
 * JWTs.
 */
public class KeycloakMockServer implements AutoCloseable {

  private final HttpServer server;
  private final RSAKey rsaJWK;
  private final RSASSASigner signer;
  private final AtomicReference<String> currentUsername = new AtomicReference<>("customer_test");
  private final List<String> currentRoles = new CopyOnWriteArrayList<>(List.of("CUSTOMER"));

  public KeycloakMockServer() throws Exception {
    this.rsaJWK = new RSAKeyGenerator(2048).keyID("srh-test-key").generate();
    this.signer = new RSASSASigner(rsaJWK);
    this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

    // Endpoint for OpenID Connect JWKS public key set
    this.server.createContext(
        "/certs",
        exchange -> {
          byte[] body =
              ("{\"keys\":[" + rsaJWK.toPublicJWK().toJSONString() + "]}")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        });

    // Endpoint for token exchange
    this.server.createContext(
        "/token",
        exchange -> {
          try {
            String username = currentUsername.get();
            List<String> roles = List.copyOf(currentRoles);
            String idToken = generateIdToken(username, roles);
            String accessToken = generateAccessToken(username, roles);

            String responseJson =
                String.format(
                    "{\"access_token\":\"%s\",\"token_type\":\"Bearer\",\"expires_in\":3600,\"refresh_token\":\"mock-refresh-token\",\"id_token\":\"%s\",\"scope\":\"openid profile email roles\"}",
                    accessToken, idToken);

            byte[] body = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
              os.write(body);
            }
          } catch (Exception e) {
            exchange.sendResponseHeaders(500, 0);
          }
        });

    // Endpoint for UserInfo
    this.server.createContext(
        "/userinfo",
        exchange -> {
          String username = currentUsername.get();
          String responseJson =
              String.format(
                  "{\"sub\":\"user-%s\",\"preferred_username\":\"%s\",\"email\":\"%s@smartrent.local\",\"name\":\"%s\"}",
                  username, username, username, username.toUpperCase());
          byte[] body = responseJson.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        });

    this.server.start();
  }

  public void configureUser(String username, List<String> roles) {
    this.currentUsername.set(username);
    this.currentRoles.clear();
    this.currentRoles.addAll(roles);
  }

  public int getPort() {
    return server.getAddress().getPort();
  }

  public String getBaseUrl() {
    return "http://127.0.0.1:" + getPort();
  }

  public String getIssuerUri() {
    return getBaseUrl();
  }

  public String getAuthUri() {
    return getBaseUrl() + "/auth";
  }

  public String getTokenUri() {
    return getBaseUrl() + "/token";
  }

  public String getJwkSetUri() {
    return getBaseUrl() + "/certs";
  }

  public String getUserInfoUri() {
    return getBaseUrl() + "/userinfo";
  }

  private String generateIdToken(String username, List<String> roles) throws Exception {
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("srh-test-key").build();
    Date now = new Date();
    JWTClaimsSet claimsSet =
        new JWTClaimsSet.Builder()
            .subject("user-" + username)
            .issuer(getIssuerUri())
            .audience("srh-customer")
            .issueTime(now)
            .expirationTime(new Date(now.getTime() + 3600_000))
            .claim("preferred_username", username)
            .claim("email", username + "@smartrent.local")
            .claim("name", username.toUpperCase())
            .claim("given_name", username)
            .claim("family_name", "Testov")
            .claim("realm_access", Map.of("roles", roles))
            .build();

    SignedJWT signedJWT = new SignedJWT(header, claimsSet);
    signedJWT.sign(signer);
    return signedJWT.serialize();
  }

  private String generateAccessToken(String username, List<String> roles) throws Exception {
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("srh-test-key").build();
    Date now = new Date();
    JWTClaimsSet claimsSet =
        new JWTClaimsSet.Builder()
            .subject("user-" + username)
            .issuer(getIssuerUri())
            .audience("account")
            .issueTime(now)
            .expirationTime(new Date(now.getTime() + 3600_000))
            .claim("preferred_username", username)
            .claim("scope", "openid profile email roles")
            .claim("realm_access", Map.of("roles", roles))
            .build();

    SignedJWT signedJWT = new SignedJWT(header, claimsSet);
    signedJWT.sign(signer);
    return signedJWT.serialize();
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
