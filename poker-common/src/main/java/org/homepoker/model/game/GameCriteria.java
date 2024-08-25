package org.homepoker.model.game;

import lombok.Builder;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.List;

@Builder
public record GameCriteria(@Nullable String name, @Nullable List<GameStatus> statuses, @Nullable Instant startTime, @Nullable Instant endTime) {
}
