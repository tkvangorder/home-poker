package org.homepoker.game.tournament;

import lombok.Builder;
import lombok.Data;
import org.homepoker.game.*;
import org.homepoker.user.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class TournamentGame implements Game {

  @Override
  public GameFormat getGameFormat() {
    return GameFormat.TOURNAMENT;
  }

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
  private LocalDateTime startTimestamp;

  /**
   * The end time is used only when persisting a game once it is over or has been ended
   * by the game manager.
   */
  private LocalDateTime endTimestamp;

  /**
   * Current status of the game (useful when persisting the game to storage)
   */
  private GameStatus status;

  /**
   * User that created/owns the game.
   */
  private User owner;

  /**
   * The players registered/participating in the game.
   */
  private Map<String, Player> players;

  /**
   * A game may have multiple tables depending on how many players are registered/participating in the game.
   * Each table can hold up to nine players and as players come and go, the players may be moved to different tables.
   */
  private List<Table> tables;

  /**
   * The number of chips each player will start with.
   */
  private Integer buyInChips;

  /**
   * The buy-in amount in dollars
   */
  private BigDecimal buyInAmount;

  /**
   * The time interval where the blinds go "up"
   */
  private int blindIntervalMinutes;

  /**
   * The estimated length of the tournament in hours.
   */
  private int estimatedTournamentLengthHours;

  /**
   * The level at which rebuys are no longer allows and when an add-on may be applied.
   */
  private Integer cliffLevel;

  /**
   * Number of allowed rebuys for each player.
   */
  private Integer numberOfRebuys;

  /**
   * The amount of chips given for a re-buy.
   */
  @Builder.Default
  private Integer rebuyChips = 0;

  /**
   * The rebuy-in amount in dollars
   */
  private BigDecimal rebuyAmount;

  /**
   * Does this game allow add-ons?
   */
  private boolean addOnAllowed;

  /**
   * The amount of chips given if a player elects to add-on.
   */
  private Integer addOnChips;

  /**
   * The add-on amount in dollars
   */
  private BigDecimal addOnAmount;

  /**
   * The blind schedule for the tournament. The blind schedule can be provided when creating the game or it can be
   * auto-calculated using the number of players and the amount of chips in play. If the blind schedule is not provided,
   * the schedule will be generated at the start of the tournament.
   */
  private BlindSchedule blindSchedule;
}
