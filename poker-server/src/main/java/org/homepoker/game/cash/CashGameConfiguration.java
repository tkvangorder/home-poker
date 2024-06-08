package org.homepoker.game.cash;

import lombok.Builder;
import lombok.With;
import org.homepoker.model.game.GameType;

import java.time.Instant;

@Builder
@With
public record CashGameConfiguration(String id, String name, GameType gameType, Instant startTimestamp, Integer smallBlind, Integer bigBlind, Integer maxBuyIn) {
}
