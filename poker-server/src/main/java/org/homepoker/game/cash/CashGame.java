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
public record CashGame(
    @With
    String id,
    @With
    String name,
    @With
    GameFormat format,
    @With
    GameType type,
    @With
    Instant startTime,
    @With
    Instant endTime,
    @With
    GameStatus status,
    @With
    User owner,
    @With
    Integer smallBlind,
    @With
    Integer bigBlind,
    @With
    Integer maxBuyIn,
    @With
    Instant lastModified,

    Map<String, Player> players,
    List<Table> tables

    ) implements Game {

    @Override
    @SuppressWarnings("unchecked")
    public <G extends Game> G withPlayers(Map<String, Player> players) {
        if (this.players == players || this.players.equals(players)) {
            return (G) this;
        }
        return (G) new CashGame(id, name, format, type, startTime, endTime, status, owner, smallBlind, bigBlind, maxBuyIn, lastModified, players, tables);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <G extends Game> G withTables(List<Table> tables) {
        if (this.tables == tables || this.tables.equals(tables)) {
            return (G) this;
        }
        return (G) new CashGame(id, name, format, type, startTime, endTime, status, owner, smallBlind, bigBlind, maxBuyIn, lastModified, players, tables);
    }
}

