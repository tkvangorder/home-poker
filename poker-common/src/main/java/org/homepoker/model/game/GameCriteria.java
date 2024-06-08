package org.homepoker.model.game;

import org.springframework.lang.Nullable;

import java.time.Instant;

public record GameCriteria(@Nullable String name, @Nullable GameStatus status, @Nullable Instant startDate, @Nullable Instant endDate) {
}
