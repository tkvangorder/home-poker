package org.homepoker.game.cash;

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
public class CashGame implements Game {

  @Override
  public GameFormat getGameFormat() {
    return GameFormat.CASH;
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
   * The players registered/participating in the game. The map is userId -> player.
   */
  private Map<String, Player> players;

  /**
   * A game may have multiple tables depending on how many players are registered/participating in the game.
   * Each table can hold up to nine players and as players come and go, the players may be moved to different tables.
   */
  private List<Table> tables;

  /**
   * Small blind for the cash game
   */
  int smallBlind;

  /**
   * Big blind for the cash game
   */
  int bigBlind;

  /**
   * The number of chips a player receives for the buy-in amount.
   */
  private Integer buyInChips;

  /**
   * The number of chips a player receives for the buy-in amount.
   */
  private BigDecimal buyInAmount;
}
