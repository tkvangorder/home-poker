package org.homepoker.security;

import lombok.Data;
import lombok.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Objects;

@ConfigurationProperties("poker.security")
@Value
public class PokerSecurityProperties {

  List<String> adminUsers;
  String passcode;

  public PokerSecurityProperties(List<String> adminUsers, String passcode) {
    Assert.notEmpty(adminUsers, "At least one admin user must be defined for the server.");
    Assert.hasText(passcode, "The server passcode cannot be empty!");
    this.adminUsers = adminUsers;
    this.passcode = passcode;
  }

}
