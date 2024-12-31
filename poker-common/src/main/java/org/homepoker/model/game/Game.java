package org.homepoker.model.game;

import org.homepoker.lib.exception.ValidationException;
import org.homepoker.model.user.User;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.util.Map;

/**
 * An interface to define common properties and operations regardless of the game format : CASH/TOURNAMENT
 *
 * @author tyler.vangorder
 */
public interface Game<G extends Game<G>> {

  /**
   * Unique Id of the game.
   */
  String id();

  /**
   * A human-readable name for the game.
   */
  String name();

  /**
   * This is a simple enumeration for the format: CASH or TOURNAMENT
   *
   * @return The game format.
   */
  GameFormat format();

  /**
   * What type of poker game? Texas Hold'em, Draw, etc.
   */
  GameType type();

  Instant startTime();

  @Nullable
  Instant endTime();

  /**
   * Current status of the game (useful when persisting the game to storage)
   */
  GameStatus status();

  Game<G> status(GameStatus status);

  /**
   * User that created/owns the game.
   */
  User owner();

  Game<G> owner(User owner);

  /**
   * The players registered/participating in the game.
   */
  Map<String, Player> players();

  default void addPlayer(Player player) {
    if (players().containsKey(player.userId())) {
      throw new ValidationException("Player is already registered for this game.");
    }
    players().put(player.userId(), player);
  }

  /**
   * A game may have multiple tables depending on how many players are registered/participating in the game.
   * Each table can hold up to nine players and as players come and go, the players may be moved to different tables.
   */
  Map<String, Table> tables();

  @LastModifiedDate
  @Nullable
  Instant lastModified();

  @LastModifiedDate
  Game<G> lastModified(Instant lastModified);
}
