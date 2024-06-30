package org.homepoker.game.cash;

import lombok.Builder;
import lombok.With;
import org.homepoker.model.game.GameType;

import java.time.Instant;

@Builder
@With
public record CashGameConfiguration(String name, GameType gameType, Instant startTime, Integer smallBlind, Integer bigBlind, Integer maxBuyIn) {
}
