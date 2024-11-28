package org.homepoker.game;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the game server.
 *
 * @param threadModel The threading model to use for the game server. Using a single thread is useful for debugging and testing.
 * @param gameLoopIntervalMilliseconds The interval at which the game loop should run. Settings this to 0 disables the scheduled game loop and is useful for testing.
 */
@ConfigurationProperties(prefix = "game.server")
public record GameServerProperties(
    ThreadModel threadModel,
    Integer gameLoopIntervalMilliseconds
) {

  public GameServerProperties(@Nullable ThreadModel threadModel, @Nullable Integer gameLoopIntervalMilliseconds) {
     this.threadModel = threadModel == null ? ThreadModel.VIRTUAL : threadModel;
     this.gameLoopIntervalMilliseconds = gameLoopIntervalMilliseconds == null ? 1000 : gameLoopIntervalMilliseconds;
  }

  public enum ThreadModel {
    VIRTUAL,
    SINGLE_THREAD
  }
}
