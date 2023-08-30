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
	 * The scheduled/actual start time of the game.
	 */
	private LocalDateTime startTimestamp;
	
	/**
	 * The number of chips a player receives for the buy-in amount. 
	 */
	private Integer buyInChips;
	
	/**
	 * The buy-in amount in dollars
	 */
	private BigDecimal buyInAmount;

	/**
	 * The user's loginId that created/owns the game.
	 */
	private String ownerLoginId;

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
	 * 
	 * NOTE: This is a computed field and has no meaning during game creation/update.
	 */
	private Integer numberOfPlayers;
}
