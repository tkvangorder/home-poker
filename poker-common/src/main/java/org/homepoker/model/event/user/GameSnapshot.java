package org.homepoker.model.event.user;

import org.homepoker.model.event.EventMarker;
import org.homepoker.model.event.UserEvent;
import org.homepoker.model.game.GameStatus;
import org.homepoker.model.game.Player;

import java.time.Instant;
import java.util.List;

@EventMarker
public record GameSnapshot(
    Instant timestamp,
    String userId,
    String gameId,
    String gameName,
    GameStatus status,
    Instant startTime,
    int smallBlind,
    int bigBlind,
    List<Player> players,
    List<String> tableIds
) implements UserEvent {
}