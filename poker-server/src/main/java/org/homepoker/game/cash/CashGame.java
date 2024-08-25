package org.homepoker.game.cash;

import lombok.Builder;
import lombok.With;
import org.homepoker.game.*;
import org.homepoker.model.game.GameFormat;
import org.homepoker.model.game.GameStatus;
import org.homepoker.model.game.GameType;
import org.homepoker.model.game.Player;
import org.homepoker.model.user.User;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
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
    @Nullable
    Instant endTime,
    GameStatus status,
    User owner,
    Integer smallBlind,
    Integer bigBlind,
    Integer maxBuyIn,
    Instant lastModified,
    Map<String, Player> players,
    List<Table> tables
    ) implements Game<CashGame> {

    public CashGame withPlayer(Player player) {
        Map<String, Player> players = new HashMap<>(this.players);
        players.put(player.user().id(), player);
        return this.withPlayers(Map.copyOf(players));
    }

    public static class CashGameBuilder {

        CashGame build() {
            return new CashGame(id, name, format, type, startTime, endTime, status, owner, smallBlind, bigBlind,
                maxBuyIn, lastModified, players == null ? Map.of() : players, tables == null ? List.of() : tables);
        }

        CashGameBuilder player(Player player) {
            Map<String, Player> players = new HashMap<>(this.players);
            players.put(player.user().id(), player);
            this.players = Map.copyOf(players);
            return this;
        }

        CashGameBuilder table(Table table) {
            List<Table> tables = new ArrayList<>(this.tables);
            tables.add(table);
            this.tables = List.copyOf(tables);
            return this;
        }
    }
}

