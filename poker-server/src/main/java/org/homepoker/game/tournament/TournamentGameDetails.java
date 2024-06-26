package org.homepoker.game.tournament;

import lombok.Builder;
import lombok.Data;
import org.homepoker.model.game.GameType;

import java.time.Instant;

/**
 * The game configuration is used to set the parameters for a given poker game.
 *
 * @author tyler.vangorder
 */
@Data
@Builder
public class TournamentGameDetails {

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
   * The scheduled/actual start time of the game.
   */
  private Instant startTimestamp;

  /**
   * The number of chips each player will start with.
   */
  private Integer buyInChips;

  /**
   * The buy-in amount in cents (we do not want to deal with floating point numbers).
   */
  private Integer buyInAmount;

  /**
   * User that created/owns the game.
   */
  private String ownerLoginId;

  /**
   * The time interval where the blinds go "up"
   */
  private Integer blindIntervalMinutes;

  /**
   * The estimated time that the tournament should end. This is used when calculating the blind schedule
   * along with the number of players and chips in play.
   */
  private Integer estimatedTournamentLengthHours;

  /**
   * The level at which re-buys are no longer allows and when an add-on may be applied.
   */
  private Integer cliffLevel;

  /**
   * Number of allowed rebuys for each player.
   */
  private Integer numberOfRebuys;

  /**
   * The amount of chips given for a re-buy.
   */
  private Integer rebuyChips;

  /**
   * The rebuy-in amount in cents
   */
  private Integer rebuyAmount;

  /**
   * Does this game allow add-ons?
   */
  private boolean addOnAllowed;

  /**
   * The amount of chips given if a player elects to add-on.
   */
  private Integer addOnChips;

  /**
   * The add-on amount in cents
   */
  private Integer addOnAmount;

  /**
   * The number of players registered/playing in the game.
   * <p>
   * NOTE: This is a computed field and has no meaning during game creation/update.
   */
  private Integer numberOfPlayers;
}
