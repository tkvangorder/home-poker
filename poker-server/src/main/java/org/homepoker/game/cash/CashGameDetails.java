package org.homepoker.game.cash;

import lombok.Builder;
import org.homepoker.model.game.GameStatus;
import org.homepoker.model.game.GameType;
import org.homepoker.model.user.User;

import java.time.Instant;

/**
 * The game configuration is used to set the parameters for a given poker game.
 *
 * @author tyler.vangorder
 */
@Builder
public record CashGameDetails(
    String id,
    String name,
    GameType type,
    GameStatus status,
    Instant startTimestamp,
    Integer maxBuyIn,
    User owner,
    Integer smallBlind,
    Integer bigBlind,
    Integer numberOfPlayers) {
}

