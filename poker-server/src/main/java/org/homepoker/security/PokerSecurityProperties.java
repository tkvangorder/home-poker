package org.homepoker.security;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("poker.security")
@Value
public class PokerSecurityProperties {

  /**
   * The list of users that are considered administrators of the server.
   */
  List<String> adminUsers;

  /**
   * The server passcode that must be provided to register a new user.
   */
  String passcode;

  /**
   * The duration for which a JWT token is valid. The default is 4 hour
   */
  @Nullable
  Duration jwtExpiration;

  /**
   * The secret key used to sign JWT tokens. This should be a Base64 encoded string generated using
   * Jwts.SIG.HS256.key().build() or similar. If not provided, a transient key will be generated that will not survive
   * server restarts.
   */
  @Nullable
  String jwtVerificationKey;


  public PokerSecurityProperties(List<String> adminUsers, String passcode, @Nullable Duration jwtExpiration, @Nullable String jwtVerificationKey) {
    Assert.notEmpty(adminUsers, "At least one admin user must be defined for the server.");
    Assert.hasText(passcode, "The server passcode cannot be empty!");
    this.adminUsers = adminUsers;
    this.passcode = passcode;
    this.jwtExpiration = jwtExpiration;
    this.jwtVerificationKey = jwtVerificationKey;
  }

}
