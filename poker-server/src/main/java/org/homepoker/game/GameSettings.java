package org.homepoker.game;

public class GameSettings {

  public final static GameSettings DEFAULT = new GameSettings(5);

  public final int saveIntervalSeconds;

  public GameSettings(int saveIntervalSeconds) {
    this.saveIntervalSeconds = saveIntervalSeconds;
  }
}
