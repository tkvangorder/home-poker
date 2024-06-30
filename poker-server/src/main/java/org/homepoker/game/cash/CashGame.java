package org.homepoker.game.cash;

import lombok.Builder;
import lombok.With;
import org.homepoker.game.*;
import org.homepoker.model.game.GameFormat;
import org.homepoker.model.game.GameStatus;
import org.homepoker.model.game.GameType;
import org.homepoker.model.game.Player;
import org.homepoker.model.user.User;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Builder
@With
public record CashGame(
    String id,
    String name,
    GameFormat format,
    GameType type,
    Instant startTime,
    Instant endTime,
    GameStatus status,
    User owner,
    Map<String, Player> players,
    List<Table> tables,
    Integer smallBlind,
    Integer bigBlind,
    Integer maxBuyIn
    ) implements Game {
}

