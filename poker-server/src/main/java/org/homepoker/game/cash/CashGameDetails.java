package org.homepoker.game.cash;

import lombok.Builder;
import lombok.Data;
import org.homepoker.model.game.GameStatus;
import org.homepoker.model.game.GameType;
import org.homepoker.model.user.User;

import java.time.Instant;

/**
 * The game configuration is used to set the parameters for a given poker game.
 *
 * @author tyler.vangorder
 */
@Data
@Builder
public class CashGameDetails {

  /**
   * Unique Id of the game.
   */
  private String id;

  /**
   * A human readable name for the game.
   */
  private String name;

  /**
   * What type of poker game? Texas Hold'em, Draw, etc.
   */
  private GameType gameType;

  /**
   * Current status of the game
   */
  private GameStatus status;

  /**
   * The scheduled/actual start time of the game.
   */
  private Instant startTimestamp;

  /**
   * The maximum buy-in amount in cents, we do not want to deal with floating point numbers.
   */
  private Integer maxBuyIn;

  /**
   * The user that created/owns the game.
   */
  private User owner;

  /**
   * The number of chips for the small blind.
   */
  private Integer smallBlind;

  /**
   * The number of chips for the big blind (typically 2Xsmall blind)
   */
  private Integer bigBlind;

  /**
   * The number of players registered/playing in the game.
   * <p>
   * NOTE: This is a computed field and has no meaning during game creation/update.
   */
  private Integer numberOfPlayers;
}
