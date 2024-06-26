package org.homepoker.game.cash;

import lombok.Builder;
import org.homepoker.model.game.GameStatus;
import org.homepoker.model.game.GameType;
import org.homepoker.model.game.Player;
import org.homepoker.model.user.User;

import java.time.Instant;
import java.util.List;

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
    Instant startTime,
    Integer maxBuyIn,
    User owner,
    Integer smallBlind,
    Integer bigBlind,
    List<Player> players) {
}

