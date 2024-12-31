package org.homepoker.game;

import lombok.Builder;

@Builder
public record GameSettings(
    int saveIntervalSeconds,
    int seatingTimeSeconds) {

  public final static GameSettings DEFAULT = GameSettings.builder()
      .saveIntervalSeconds(5)
      .seatingTimeSeconds(60)
      .build();

}
