package org.homepoker.model.game.cash;

import lombok.Builder;
import lombok.With;
import org.homepoker.model.game.GameStatus;
import org.homepoker.model.game.GameType;
import org.homepoker.model.game.Player;
import org.homepoker.model.user.User;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * The game configuration is used to set the parameters for a given poker game.
 *
 * @author tyler.vangorder
 */
@Builder
@With
public record CashGameDetails(
    String id,
    String name,
    GameType type,
    GameStatus status,
    @Nullable
    Instant startTime,
    Integer maxBuyIn,
    User owner,
    Integer smallBlind,
    @Nullable
    Integer bigBlind,
    List<Player> players) {
}

