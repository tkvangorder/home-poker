package org.homepoker.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.homepoker.model.user.User;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;

/**
 * A service to generate and validate JWT tokens. This service uses the JJWT library to generate and validate tokens.
 */
@Service
public class JwtTokenService {

  private final SecretKey verificationKey;
  private final Duration expiration;

  public JwtTokenService(PokerSecurityProperties securityProperties) {
    // If the expiration is not set via properties, default to 4 hours.
    expiration = securityProperties.getJwtExpiration() == null ? Duration.ofHours(4) : securityProperties.getJwtExpiration();
    // If the verification key is not set via properties, default to an in-memory, transient key. This will not
    // survive a server restart and any tokens generated with this key will be invalid after a restart.
    verificationKey = securityProperties.getJwtVerificationKey() == null ?
        Jwts.SIG.HS256.key().build() : getKey(securityProperties.getJwtVerificationKey());
  }

  /**
   * Generates a JWT token for the user. The token will expire based on the server-configured expiration time.
   *
   * @param user The user to generate a token for
   * @return The generated token
   */
  public String generateToken(User user) {
    return Jwts.builder()
        .subject(user.loginId())
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + (expiration.getSeconds() * 1000)))
        .signWith(verificationKey)
        .compact();
  }

  /**
   * A method to extract the user login ID from the Jwt's subject field. This method will throw an exception if the token
   * is either invalid or had expired.
   *
   * @param jwtToken The JWT token to extract the user login ID from
   * @return The user's login ID
   */
  @Nullable
  public String extractUserLoginId(String jwtToken) {
    return extractClaims(jwtToken).getSubject();
  }

  /**
   * Extracts the claims from the JWT token. If the token is invalid or is expired, an exception will be thrown.
   * @param jwtToken The JWT token to extract claims from
   * @return The claims extracted from the token
   */
  public Claims extractClaims(String jwtToken) {
    Claims claims = Jwts.parser()
        .verifyWith(verificationKey)
        .build()
        .parseSignedClaims(jwtToken)
        .getPayload();

    if (claims.getExpiration().before(new Date())) {
      throw new IllegalArgumentException("Token has expired");
    }
    return claims;
  }

  /**
   * A method to correctly map the base64 encoded key to a SecretKey object.
   * @param base64EncodedKey The base64 encoded key
   * @return A SecretKey object
   */
  private SecretKey getKey(String base64EncodedKey) {
    byte[] bytes = Decoders.BASE64.decode(base64EncodedKey);
    return Keys.hmacShaKeyFor(bytes);
  }

}
