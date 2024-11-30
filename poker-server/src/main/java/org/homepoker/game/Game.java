package org.homepoker.game;

import org.homepoker.model.game.GameFormat;
import org.homepoker.model.game.GameStatus;
import org.homepoker.model.game.GameType;
import org.homepoker.model.game.Player;
import org.homepoker.model.user.User;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.util.List;
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
   * A human readable name for the game.
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

  /**
   * Current status of the game (useful when persisting the game to storage)
   */
  GameStatus status();

  G withStatus(GameStatus status);

  /**
   * User that created/owns the game.
   */
  User owner();

  G withOwner(User owner);

  /**
   * The players registered/participating in the game.
   */
  Map<String, Player> players();

  G withPlayers(Map<String, Player> players);

  /**
   * A game may have multiple tables depending on how many players are registered/participating in the game.
   * Each table can hold up to nine players and as players come and go, the players may be moved to different tables.
   */
  List<Table> tables();

  G withTables(List<Table> tables);

  @LastModifiedDate
  Instant lastModified();
  G withLastModified(Instant lastModified);
}
