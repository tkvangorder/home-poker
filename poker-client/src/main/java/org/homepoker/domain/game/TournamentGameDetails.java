package org.homepoker.domain.game;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

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
	private LocalDateTime startTimestamp;

	/**
	 * The number of chips each player will start with.
	 */
	private Integer buyInChips;
	
	/**
	 * The buy-in amount in dollars
	 */
	private BigDecimal buyInAmount;

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

}
